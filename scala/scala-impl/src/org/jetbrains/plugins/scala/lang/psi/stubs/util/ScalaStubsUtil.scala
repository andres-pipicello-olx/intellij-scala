package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package util


import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiMember}
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.isStatic
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.10.2008
 */

object ScalaStubsUtil {
  def getClassInheritors(clazz: PsiClass, scope: GlobalSearchScope): Seq[ScTemplateDefinition] = {
    val name: String = clazz.name
    if (name == null || clazz.isEffectivelyFinal) return Seq.empty

    val inheritors = new ArrayBuffer[ScTemplateDefinition]

    import ScalaIndexKeys._
    val extendsBlocks = SUPER_CLASS_NAME_KEY.elements(name, scope, classOf[ScExtendsBlock])(clazz.getProject)
      .iterator

    while (extendsBlocks.hasNext) {
      val extendsBlock = extendsBlocks.next
      extendsBlock.getParent match {
        case tp: ScTemplateDefinition => inheritors += tp
        case _ =>
      }
    }
    inheritors
  }

  def getSelfTypeInheritors(clazz: PsiClass): Seq[ScTemplateDefinition] = {
    @CachedInUserData(clazz, CachesUtil.enclosingModificationOwner(clazz))
    def selfTypeInheritorsInner(): Seq[ScTemplateDefinition] = {
      if (clazz.name == null) {
        return Seq.empty
      }

      val inheritors = new ArrayBuffer[ScTemplateDefinition]

      implicit val project: Project = clazz.getProject
      val resolveScope = clazz.resolveScope

      def processClass(inheritedClazz: PsiClass) {
        val name = inheritedClazz.name
        if (name == null) {
          return
        }

        def checkTp(tp: ScType): Boolean = {
          tp match {
            case c: ScCompoundType =>
              c.components.exists(checkTp)
            case _ =>
              tp.extractClass match {
                case Some(otherClazz) =>
                  if (ScEquivalenceUtil.areClassesEquivalent(inheritedClazz, otherClazz)) return true
                case _ =>
              }
          }
          false
        }
        inReadAction {
          import ScalaIndexKeys._
          for {
            selfTypeElement <- SELF_TYPE_CLASS_NAME_KEY.elements(name, resolveScope, classOf[ScSelfTypeElement])
            typeElement <- selfTypeElement.typeElement
            tp <- typeElement.`type`().toOption
            if checkTp(tp)
            clazz = PsiTreeUtil.getContextOfType(selfTypeElement, classOf[ScTemplateDefinition])
          } if (clazz != null) inheritors += clazz
        }
      }
      processClass(clazz)
      ClassInheritorsSearch.search(clazz, ScalaFilterScope(project, resolveScope), true).forEach(new Processor[PsiClass] {
        def process(t: PsiClass): Boolean = {
          processClass(t)
          true
        }
      })
      inheritors.toVector
    }

    if (clazz.isEffectivelyFinal) Seq.empty
    else selfTypeInheritorsInner()
  }

  def classesToImportFor(m: PsiMember): Seq[PsiClass] = {
    val cClass = m.containingClass
    if (cClass == null)
      return Seq.empty

    if (isStatic(m)) Seq(cClass)
    else inheritorOrThisObjects(cClass)
  }

  def inheritorOrThisObjects(clazz: PsiClass): Seq[ScObject] = clazz match {
    case o: ScObject if o.isStatic                      => Seq(o)
    case td: ScTypeDefinition if !td.isEffectivelyFinal =>
      getClassInheritors(clazz, clazz.resolveScope)
        .collect {
          case o: ScObject if o.isStatic && o.isInheritor(clazz, deep = false) => o
        }
    case _ => Seq.empty
  }

  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaStubsUtil")
}
