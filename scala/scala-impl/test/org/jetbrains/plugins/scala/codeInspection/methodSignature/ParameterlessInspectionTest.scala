package org.jetbrains.plugins.scala.codeInspection.methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, ScalaQuickFixTestBase}

/**
 * Inspection UnitMethodInspection.Parameterless is deprecated!!!
 */
class ParameterlessInspectionTest extends ScalaQuickFixTestBase {

  import CodeInsightTestFixture.{CARET_MARKER => CARET}
  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[UnitMethodInspection.Parameterless]

  protected override val description: String =
    InspectionBundle.message("method.signature.unit.parameterless")

  private val hint = InspectionBundle.message("empty.parentheses")


  def test_function_style(): Unit = {
    checkTextHasError(
      text = s"def ${START}foo$END: Unit"
    )

    testQuickFix(
      text = s"def f${CARET}oo: Unit",
      expected = "def foo(): Unit",
      hint
    )
  }

  def test_procedure_style(): Unit = {
    checkTextHasError(
      text = s"def ${START}foo$END"
    )

    testQuickFix(
      text = s"def f${CARET}oo",
      expected = "def foo()",
      hint
    )
  }
}
