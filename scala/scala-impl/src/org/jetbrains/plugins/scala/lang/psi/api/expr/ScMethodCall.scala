package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.expr.ScMethodCallCfgBuildingImpl

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
trait ScMethodCall extends ScExpression with MethodInvocation {
  def deepestInvokedExpr: ScExpression = {
    getEffectiveInvokedExpr match {
      case call: ScMethodCall =>
        call.deepestInvokedExpr
      case expr => expr
    }
  }

  def args: ScArgumentExprList = findChildByClassScala(classOf[ScArgumentExprList])

  override def isUpdateCall: Boolean = getContext.isInstanceOf[ScAssignment] &&
    getContext.asInstanceOf[ScAssignment].leftExpression == this

  def updateExpression(): Option[ScExpression] = {
    getContext match {
      case a: ScAssignment if a.leftExpression == this => a.rightExpression
      case _ => None
    }
  }

  override def argsElement: PsiElement = args

  /**
    * If named parameters enabled for this method even if it is from java; needed for Play 2 support
    */
  def isNamedParametersEnabledEverywhere: Boolean = false

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitMethodCallExpression(this)
  }
}

object ScMethodCall {
  def unapply(call: ScMethodCall) =
    Some(call.getInvokedExpr, call.argumentExpressions)
}