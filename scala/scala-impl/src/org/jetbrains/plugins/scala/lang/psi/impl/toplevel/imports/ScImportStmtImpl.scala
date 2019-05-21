package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package imports

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.statements.ScBlockStatementCfgBuildingNoopImpl
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSimpleTypeElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImportStmtStub
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.clean
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.annotation.tailrec
import scala.collection.immutable.Set
import scala.collection.mutable

/**
 * @author Alexander Podkhalyuzin
 *         Date: 20.02.2008
 */

class ScImportStmtImpl private(stub: ScImportStmtStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.IMPORT_STMT, node)
    with ScImportStmt with ScBlockStatementCfgBuildingNoopImpl {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScImportStmtStub) = this(stub, null)

  override def toString: String = "ScImportStatement"

  import com.intellij.psi.scope._

  def importExprs: Seq[ScImportExpr] =
    getStubOrPsiChildren(ScalaElementType.IMPORT_EXPR, JavaArrayFactoryUtil.ScImportExprFactory).toSeq

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    val importsIterator = importExprs.takeWhile(_ != lastParent).reverseIterator
    while (importsIterator.hasNext) {
      val importExpr = importsIterator.next()
      ProgressManager.checkCanceled()

      def workWithImportExpr: Boolean = {
        val ref = importExpr.reference match {
          case Some(element) => element
          case _ => return true
        }
        val nameHint = processor.getHint(NameHint.KEY)
        val name = if (nameHint == null) "" else nameHint.getName(state)
        if (name != "" && !importExpr.isSingleWildcard) {
          val decodedName = clean(name)
          val importedNames = importExpr.importedNames.map(clean)
          if (!importedNames.contains(decodedName)) return true
        }
        val checkWildcardImports = processor match {
          case r: ResolveProcessor =>
            if (!r.checkImports()) return false
            r.checkWildcardImports()
          case _ => true
        }
        val exprQual: ScStableCodeReference = importExpr.selectorSet match {
          case Some(_) => ref
          case None if importExpr.isSingleWildcard => ref
          case None => ref.qualifier.getOrElse(return true)
        }

        val resolve = processor match {
          case p: ResolveProcessor =>
            ref match {
              // do not process methodrefs when importing a type from a type
              case ref: ScStableCodeReference
                if p.kinds.contains(ResolveTargets.CLASS) &&
                  ref.getKinds(incomplete = false).contains(ResolveTargets.CLASS) &&
                  ref.getKinds(incomplete = false).contains(ResolveTargets.METHOD) =>
                ref.resolveTypesOnly(false)
              case ref: ScStableCodeReference if p.kinds.contains(ResolveTargets.METHOD) =>
                ref.resolveMethodsOnly(false)
              case _ => ref.multiResolveScala(false)
            }
          case _ => ref.multiResolveScala(false)
        }

        def isInPackageObject(element: PsiNamedElement): Boolean = {
          PsiTreeUtil.getContextOfType(element, true, classOf[ScTypeDefinition]) match {
            case obj: ScObject if obj.isPackageObject => true
            case _ => false
          }
        }

        def qualifierType(checkPackageObject: Boolean): Option[ScType] = {
          exprQual.bind() match {
            case Some(ScalaResolveResult(p: PsiPackage, _)) =>
              if (!checkPackageObject) None
              else {
                ScalaShortNamesCacheManager.getInstance(getProject)
                  .findPackageObjectByName(p.getQualifiedName, this.resolveScope)
                  .flatMap(_.`type`().toOption)
              }
            case _ => ScSimpleTypeElementImpl.calculateReferenceType(exprQual).toOption
          }
        }

        val resolveIterator = resolve.iterator
        while (resolveIterator.hasNext) {
          @tailrec
          def getFirstReference(ref: ScStableCodeReference): ScStableCodeReference = {
            ref.qualifier match {
              case Some(qual) => getFirstReference(qual)
              case _ => ref
            }
          }

          val next = resolveIterator.next()
          val elem = next.getElement
          val importsUsed = getFirstReference(exprQual).bind().fold(next.importsUsed)(r => r.importsUsed ++ next.importsUsed)
          val subst = state.substitutor.followed(next.substitutor)

          (elem, processor) match {
            case (pack: PsiPackage, completionProcessor: CompletionProcessor) if completionProcessor.includePrefixImports =>
              val settings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(getProject)
              val prefixImports = settings.getImportsWithPrefix.filter(s =>
                !s.startsWith(ScalaCodeStyleSettings.EXCLUDE_PREFIX) &&
                  s.substring(0, s.lastIndexOf(".")) == pack.getQualifiedName
              )
              val excludeImports = settings.getImportsWithPrefix.filter(s =>
                s.startsWith(ScalaCodeStyleSettings.EXCLUDE_PREFIX) &&
                  s.substring(ScalaCodeStyleSettings.EXCLUDE_PREFIX.length, s.lastIndexOf(".")) == pack.getQualifiedName
              )
              val names = new mutable.HashSet[String]()
              for (prefixImport <- prefixImports) {
                names += prefixImport.substring(prefixImport.lastIndexOf('.') + 1)
              }
              val excludeNames = new mutable.HashSet[String]()
              for (prefixImport <- excludeImports) {
                excludeNames += prefixImport.substring(prefixImport.lastIndexOf('.') + 1)
              }
              val wildcard = names.contains("_")

              def isOK(name: String): Boolean = {
                if (wildcard) !excludeNames.contains(name)
                else names.contains(name)
              }

              val newImportsUsed = Set(importsUsed.toSeq: _*) + ImportExprUsed(importExpr)
              val newState = state.withPrefixCompletion.withImportsUsed(newImportsUsed)

              val importsProcessor = new BaseProcessor(StdKinds.stableImportSelector) {

                override protected def execute(namedElement: PsiNamedElement)
                                              (implicit state: ResolveState): Boolean =
                  if (isOK(namedElement.name)) completionProcessor.execute(namedElement, state)
                  else true

                override def getHint[T](hintKey: Key[T]): T = completionProcessor.getHint(hintKey)
              }

              elem.processDeclarations(importsProcessor, newState, this, place)
            case _ =>
          }
          ProgressManager.checkCanceled()
          importExpr.selectorSet match {
            case None =>
              // Update the set of used imports
              val newImportsUsed = Set(importsUsed.toSeq: _*) + ImportExprUsed(importExpr)
              val refType = qualifierType(isInPackageObject(next.element))

              val newState: ResolveState = state
                .withImportsUsed(newImportsUsed)
                .withSubstitutor(subst)
                .withFromType(refType)

              if (importExpr.isSingleWildcard) {
                if (!checkWildcardImports)
                  return true

                val processed = (elem, refType, processor) match {
                  case (cl: PsiClass, _, processor: BaseProcessor) if !cl.isInstanceOf[ScTemplateDefinition] =>
                    processor.processType(ScDesignatorType.static(cl), place, newState)
                  case (_, Some(value), processor: BaseProcessor) =>
                    processor.processType(value, place, newState)
                  case _ =>
                    elem.processDeclarations(processor, newState, this, place)
                }

                if (!processed)
                  return false
              }
              else if (!processor.execute(elem, newState))
                return false
            case Some(set) =>
              val shadowed: mutable.HashSet[(ScImportSelector, PsiElement)] = mutable.HashSet.empty
              val selectors = set.selectors.iterator //for reducing stacktrace
              while (selectors.hasNext) {
                val selector = selectors.next()
                ProgressManager.checkCanceled()
                selector.reference match {
                  case Some(reference) =>
                    val isImportAlias = selector.isAliasedImport && !selector.importedName.contains(reference.refName)
                    if (isImportAlias) {
                      for (result <- reference.multiResolveScala(false)) {
                        //Resolve the name imported by selector
                        //Collect shadowed and aliased elements
                        shadowed += ((selector, result.getElement))
                        val importedName = selector.importedName.map(clean)

                        if (!importedName.contains("_")) {
                          val refType = qualifierType(isInPackageObject(result.element))
                          //processor should skip shadowed reference
                          val newState: ResolveState = state
                            .withRename(importedName)
                            .withImportsUsed(Set(importsUsed.toSeq: _*) + ImportSelectorUsed(selector))
                            .withSubstitutor(subst.followed(result.substitutor))
                            .withFromType(refType)

                          if (!processor.execute(result.getElement, newState)) {
                            return false
                          }
                        }
                      }
                    }
                  case _ =>
                }
              }

              // There is total import from stable id
              // import a.b.c.{d=>e, f=>_, _}
              if (set.hasWildcard) {
                if (!checkWildcardImports) return true
                processor match {
                  case bp: BaseProcessor =>
                    ProgressManager.checkCanceled()
                    val p1 = new BaseProcessor(bp.kinds) {
                      override def getHint[T](hintKey: Key[T]): T = processor.getHint(hintKey)

                      override def isImplicitProcessor: Boolean = bp.isImplicitProcessor

                      override def handleEvent(event: PsiScopeProcessor.Event, associated: Object) {
                        processor.handleEvent(event, associated)
                      }

                      override def getClassKind: Boolean = bp.getClassKind

                      override def setClassKind(b: Boolean) {
                        bp.setClassKind(b)
                      }

                      override protected def execute(namedElement: PsiNamedElement)
                                                    (implicit state: ResolveState): Boolean = {
                        if (shadowed.exists(p => ScEquivalenceUtil.smartEquivalence(namedElement, p._2))) return true

                        val refType = qualifierType(isInPackageObject(namedElement))
                        val newState = state
                          .withSubstitutor(subst)
                          .withFromType(refType)

                        processor.execute(namedElement, newState)
                      }
                    }

                    val newImportsUsed: Set[ImportUsed] = Set(importsUsed.toSeq: _*) + ImportWildcardSelectorUsed(importExpr)
                    val newState: ResolveState =
                      state.withImportsUsed(newImportsUsed).withSubstitutor(subst)

                    (elem, processor) match {
                      case (cl: PsiClass, processor: BaseProcessor) if !cl.isInstanceOf[ScTemplateDefinition] =>
                        val qualType = qualifierType(isInPackageObject(next.element))

                        if (!processor.processType(ScDesignatorType.static(cl), place, newState.withFromType(qualType)))
                          return false
                      case _ =>
                        // In this case import optimizer should check for used selectors
                        if (!elem.processDeclarations(p1, newState, this, place))
                          return false
                    }
                  case _ => true
                }
              }

              //wildcard import first, to show that this imports are unused if they really are
              set.selectors.foreach { selector =>
                ProgressManager.checkCanceled()
                for {
                  element <- selector.reference
                  result <- element.multiResolveScala(false)
                } {
                  if (!selector.isAliasedImport || selector.importedName == selector.reference.map(_.refName)) {
                    val rSubst = result.substitutor
                    val newState = state
                      .withImportsUsed(Set(importsUsed.toSeq: _*) + ImportSelectorUsed(selector))
                      .withSubstitutor(subst.followed(rSubst))
                      .withFromType(qualifierType(isInPackageObject(result.element)))

                    if (!processor.execute(result.getElement, newState))
                      return false
                  }
                }
              }
          }
        }
        true
      }

      if (!workWithImportExpr) return false
    }
    true
  }
}
