package org.jetbrains.plugins.scala.annotator.element

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.{annotationWithoutHighlighting, smartCheckConformance}
import org.jetbrains.plugins.scala.annotator._
import org.jetbrains.plugins.scala.annotator.quickfix.{AddBreakoutQuickFix, ChangeTypeFix, WrapInOptionQuickFix}
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker.registerUsedImports
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec

object ScExpressionAnnotator extends ElementAnnotator[ScExpression] {
  override def annotate(element: ScExpression, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    // TODO Annotating ScUnderscoreSection is technically correct, but reveals previously hidden red code in ScalacTestdataHighlightingTest.tuples_1.scala
    // TODO see visitUnderscoreExpression in ScalaAnnotator
    if (element.isInstanceOf[ScUnderscoreSection]) {
      return
    }

    val compiled = element.getContainingFile.asOptionOf[ScalaFile].exists(_.isCompiled)

    if (!compiled) {
      checkExpressionType(element, holder, typeAware)
    }
  }

  def checkExpressionType(element: ScExpression, holder: AnnotationHolder, typeAware: Boolean): Unit = {
    implicit val ctx: ProjectContext = element

    @tailrec
    def isInArgumentPosition(expr: ScExpression): Boolean =
      expr.getContext match {
        case _: ScArgumentExprList               => true
        case ScInfixExpr.withAssoc(_, _, `expr`) => true
        case b: ScBlockExpr                      => isInArgumentPosition(b)
        case p: ScParenthesisedExpr              => isInArgumentPosition(p)
        case t: ScTuple                          => isInArgumentPosition(t)
        case i: ScIf                         => isInArgumentPosition(i)
        case m: ScMatch                      => isInArgumentPosition(m)
        case _                                   => false
      }

    def isTooBigToHighlight(expr: ScExpression): Boolean = expr match {
      case _: ScMatch                            => true
      case bl: ScBlock if bl.lastStatement.isDefined => true
      case i: ScIf if i.elseExpression.isDefined     => true
      case _: ScFunctionExpr                         => true
      case _: ScTry                              => true
      case _                                         => false
    }

    def shouldNotHighlight(expr: ScExpression): Boolean = expr.getContext match {
      case a: ScAssignment if a.rightExpression.contains(expr) && a.isDynamicNamedAssignment => true
      case t: ScTypedExpression if t.isSequenceArg                                                => true
      case param: ScParameter if !param.isDefaultParam                                      => true //performance optimization
      case param: ScParameter                                                               =>
        param.getRealParameterType match {
          case Right(paramType) if paramType.extractClass.isDefined => false //do not check generic types. See SCL-3508
          case _                                                    => true
        }
      case ass: ScAssignment if ass.isNamedParameter                                        => true //that's checked in application annotator
      case _                                                                                => false
    }

    def checkExpressionTypeInner(fromUnderscore: Boolean) {
      val ExpressionTypeResult(exprType, importUsed, implicitFunction) =
        element.getTypeAfterImplicitConversion(expectedOption = element.smartExpectedType(fromUnderscore), fromUnderscore = fromUnderscore)

      registerUsedImports(element, importUsed)

      if (isTooBigToHighlight(element) || isInArgumentPosition(element) || shouldNotHighlight(element)) return

      element.expectedTypeEx(fromUnderscore) match {
        case Some((tp: ScType, _)) if tp equiv api.Unit => //do nothing
        case Some((tp: ScType, typeElement)) =>
          val expectedType = Right(tp)
          implicitFunction match {
            case Some(_) =>
            //todo:
            /*val typeFrom = expr.getType(TypingContext.empty).getOrElse(Any)
            val typeTo = exprType.getOrElse(Any)
            val exprText = expr.getText
            val range = expr.getTextRange
            showImplicitUsageAnnotation(exprText, typeFrom, typeTo, fun, range, holder,
              EffectType.LINE_UNDERSCORE, Color.LIGHT_GRAY)*/
            case None => //do nothing
          }
          val conformance = smartCheckConformance(expectedType, exprType)
          if (!conformance) {
            if (typeAware) {
              element.getParent match {
                case assign: ScAssignment if exprType.exists(ScalaPsiUtil.isUnderscoreEq(assign, _)) => return
                case _ =>
              }
              val annotation = TypeMismatchError.register(holder, element, tp, exprType.getOrNothing, blockLevel = 2) { (expected, actual) =>
                ScalaBundle.message("expr.type.does.not.conform.expected.type", actual, expected)
              }
              if (WrapInOptionQuickFix.isAvailable(element, expectedType, exprType)) {
                val wrapInOptionFix = new WrapInOptionQuickFix(element, expectedType, exprType)
                annotation.registerFix(wrapInOptionFix)
              }
              if (AddBreakoutQuickFix.isAvailable(element)) {
                annotation.registerFix(new AddBreakoutQuickFix(element))
              }
              typeElement match {
                case Some(te) if te.getContainingFile == element.getContainingFile =>
                  val fix = new ChangeTypeFix(te, exprType.getOrNothing)
                  annotation.registerFix(fix)
                  val teAnnotation = annotationWithoutHighlighting(holder, te)
                  teAnnotation.registerFix(fix)
                case _ =>
              }
            }
          }
        case _ => //do nothing
      }

    }
    if (ScUnderScoreSectionUtil.isUnderscoreFunction(element)) {
      checkExpressionTypeInner(fromUnderscore = true)
    }
    checkExpressionTypeInner(fromUnderscore = false)
  }
}
