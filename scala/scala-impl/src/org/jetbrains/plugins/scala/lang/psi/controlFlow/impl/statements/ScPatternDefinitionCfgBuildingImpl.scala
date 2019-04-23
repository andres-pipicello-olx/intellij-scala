package org.jetbrains.plugins.scala.lang.psi.controlFlow.impl.statements

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.controlFlow.cfg.{ExprResult, RequireResult, ResultRequirement}
import org.jetbrains.plugins.scala.lang.psi.controlFlow.{CfgBuilder, CfgBuildingBlockStatement}

trait ScPatternDefinitionCfgBuildingImpl extends CfgBuildingBlockStatement { this: ScPatternDefinition =>

  override def buildBlockStatementControlFlow(rreq: ResultRequirement)(implicit builder: CfgBuilder): ExprResult = {
    import org.jetbrains.plugins.scala.lang.psi.controlFlow.CfgBuildingTools._

    val patterns = pList.patterns.filter(!canIgnorePattern(_))
    val result = buildExprOrAny(expr, RequireResult.If(patterns.nonEmpty))

    if (patterns.nonEmpty) {
      val pin = result.pin
      patterns.foreach(_.buildPatternControlFlow(pin, None))
    }

    rreq.satisfyUnit()
  }
}