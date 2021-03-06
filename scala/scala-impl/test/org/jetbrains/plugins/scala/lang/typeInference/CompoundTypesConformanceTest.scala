package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

@Category(Array(classOf[PerfCycleTests]))
class CompoundTypesConformanceTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL14527(): Unit = checkTextHasNoErrors(
    """trait Container[T]
      |trait Concrete
      |trait A
      |trait B
      |class Parent[T](t: Container[Concrete with T])
      |class Child(t: Container[Concrete with A with B]) extends Parent[A with B](t)
    """.stripMargin
  )
}
