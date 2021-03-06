package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, ScalaQuickFixTestBase}

class EmptyParenMethodCallInspectionTest extends ScalaQuickFixTestBase {

  import CodeInsightTestFixture.{CARET_MARKER => CARET}
  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ParameterlessAccessInspection.EmptyParenMethod]

  protected override val description: String =
    InspectionBundle.message("method.signature.parameterless.access.empty.paren")

  private val hint = InspectionBundle.message("add.call.parentheses")


  def test_call_without_parenthesis(): Unit = {
    getFixture.configureByText("S.scala",
      """
        |class S {
        |  def test(): Unit = ()
        |}
      """.stripMargin
    )

    checkTextHasError(
      text =
        s"""
           |new S().${START}test$END
         """.stripMargin
    )

    testQuickFix(
      text =
        s"""
           |new S().tes${CARET}t
         """.stripMargin,
      expected =
        s"""
           |new S().test()
         """.stripMargin,
      hint
    )
  }

  def test_ok(): Unit = {

    checkTextHasNoErrors(
      text =
        s"""
           |class S {
           |  def foo: Int = 0
           |}
           |
           |new S().foo
         """.stripMargin
    )

    checkTextHasNoErrors(
      text =
        s"""
           |class S {
           |  def foo(): Int = 0
           |}
           |
           |new S().foo()
         """.stripMargin
    )
  }
}
