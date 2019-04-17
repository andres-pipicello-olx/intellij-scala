package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, ScalaQuickFixTestBase}

class ApparentResultTypeRefinementInspectionTest extends ScalaQuickFixTestBase {

  import CodeInsightTestFixture.{CARET_MARKER => CARET}
  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ApparentResultTypeRefinementInspection]

  protected override val description: String =
    InspectionBundle.message("method.signature.result.type.refinement")

  private val hint = InspectionBundle.message("insert.missing.assignment")


  def test(): Unit = {
    checkTextHasError(
      text =
        s"""
           |trait T {
           |  def test(): ${START}T {}$END
           |}
         """.stripMargin
    )

    testQuickFix(
      text =
        s"""
           |trait T {
           |  def test(): T$CARET {}
           |}
         """.stripMargin,
      expected =
        s"""
           |trait T {
           |  def test(): T = {}
           |}
         """.stripMargin,
      hint
    )
  }

  def test_ok(): Unit = {

    checkTextHasNoErrors(
      text =
        s"""
           |trait T {
           |  def test(): T = {}
           |}
         """.stripMargin
    )
  }
}