package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/**
  * Nikolay.Tropin
  * 6/25/13
  */
class ProcedureDeclarationInspectionTest extends ScalaQuickFixTestBase {

  import CodeInsightTestFixture.CARET_MARKER
  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[UnitMethodInspection.ProcedureDeclaration]

  protected override val description: String =InspectionBundle.message("method.signature.procedure.declaration")

  private val hint = InspectionBundle.message("convert.to.function.syntax")

  def test1(): Unit = {
    checkTextHasError(s"def ${START}foo$END()")

    testQuickFix(
      "def foo()",
      "def foo(): Unit"
    )
  }

  def test2(): Unit = {
    checkTextHasError(
      s"""def haha()
         |def ${START}hoho$END()
         |def hihi()"""
    )

    testQuickFix(
      s"""def haha()
         |def ho${CARET_MARKER}ho()
         |def hihi()""",
      """def haha()
        |def hoho(): Unit
        |def hihi()"""
    )
  }

  def test3(): Unit = {
    checkTextHasError(s"def ${START}foo$END(x: Int)")
    testQuickFix(
      "def foo(x: Int)",
      "def foo(x: Int): Unit"
    )
  }

  def test4(): Unit = {
    checkTextHasError(s"def ${START}foo$END")
    testQuickFix(
      "def foo",
      "def foo: Unit"
    )
  }

  private def testQuickFix(text: String, expected: String): Unit = testQuickFix(text, expected, hint)
}
