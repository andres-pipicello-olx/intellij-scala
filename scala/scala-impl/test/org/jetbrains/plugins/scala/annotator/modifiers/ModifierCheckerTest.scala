package org.jetbrains.plugins.scala
package annotator.modifiers

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator._
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList

class ModifierCheckerTest extends SimpleTestCase {
  def testToplevelObject(): Unit = {
    assertMatches(messages("final object A")) {
      case List(Warning(_,RedundantFinal())) =>
    }
  }

  def testInnerObject(): Unit = {
    assertMatches(messages("object A { final object B }")) {
      case Nil => // SCL-10420
    }
  }

  def testFinalValConstant(): Unit = {
    assertMatches(messages(
      """
        |final class Foo {
        |  final val constant = "This is a constant string that will be inlined"
        |}
      """.stripMargin)) {
      case Nil => // SCL-11500
    }
  }

  def testFinalValConstantAnnotated(): Unit = {
    assertMatches(messages(
      """
        |final class Foo {
        |  final val constant: String = "With annotation there is no inlining"
        |}
      """.stripMargin)) {
      case List(Warning(_,RedundantFinal())) => // SCL-11500
    }
  }

  def testAccessModifierInClass(): Unit = {
    assertNothing(messages(
      """
        |private class Test {
        |  private class InnerTest
        |  private def test(): Unit = ()
        |}
      """.stripMargin
    ))
  }

  def testAccessModifierInBlock(): Unit = {
    assertMessagesSorted(messages(
      """
        |{
        |  private class Test
        |
        |  try {
        |    protected class Test2
        |  }
        |}
      """.stripMargin
    ))(
      Error("private", "'private' modifier is not allowed here"),
      Error("protected", "'protected' modifier is not allowed here")
    )
  }

  private def messages(@Language(value = "Scala") code: String) = {
    val file = code.parse

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)
    file.depthFirst().foreach {
      case modifierList: ScModifierList => ModifierChecker.checkModifiers(modifierList)
      case _ =>
    }
    mock.annotations
  }

  val RedundantFinal = StartWith("'final' modifier is redundant")

  case class StartWith(fragment: String) {
    def unapply(s: String): Boolean = s.startsWith(fragment)
  }
}

