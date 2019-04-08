package org.jetbrains.plugins.scala
package project
package template

import java.io.File

/**
 * @author Pavel Fatin
 */
final case class ScalaSdkDescriptor(version: Option[String],
                                    compilerClasspath: Seq[File],
                                    libraryFiles: Seq[File],
                                    sourceFiles: Seq[File],
                                    docFiles: Seq[File])
  extends Ordered[ScalaSdkDescriptor] {

  private val comparableVersion = version.map(Version(_))

  override def compare(that: ScalaSdkDescriptor): Int = that.comparableVersion.compare(comparableVersion)
}

object ScalaSdkDescriptor {

  import Artifact._
  import Kind._

  def from(components: Seq[Component]): Either[String, ScalaSdkDescriptor] = {
    val componentsByKind = components.groupBy(_.kind)
      .withDefault(Function.const(Seq.empty))

    def filesByKind(kind: Kind) =
      files(componentsByKind(kind))()

    val binaryComponents = componentsByKind(Binaries)

    requiredBinaryArtifacts -- binaryComponents.map(_.artifact) match {
      case missingBinaryArtifacts if missingBinaryArtifacts.nonEmpty =>
        Left("Not found: " + missingBinaryArtifacts.map(_.prefix + "*.jar").mkString(", "))
      case _ =>
        val libraryVersion = binaryComponents.collectFirst {
          case Component(ScalaLibrary, _, Some(version), _) => version
        }

        val descriptor = ScalaSdkDescriptor(
          libraryVersion,
          files(binaryComponents)(requiredBinaryArtifacts),
          files(binaryComponents)(),
          filesByKind(Sources),
          filesByKind(Docs)
        )

        Right(descriptor)
    }
  }

  private[this] def requiredBinaryArtifacts = Set[Artifact](
    ScalaLibrary,
    ScalaCompiler,
    ScalaReflect
  )

  private[this] def files(components: Seq[Component])
                         (predicate: Artifact => Boolean = ScalaArtifacts - ScalaCompiler) =
    for {
      Component(artifact, _, _, file) <- components
      if predicate(artifact)
    } yield file
}