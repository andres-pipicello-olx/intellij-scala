package org.jetbrains.plugins.scala.projectHighlighting

import java.io.File

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.apache.commons.io.FilenameUtils
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.{inWriteAction, using}
import org.jetbrains.plugins.scala.util.PsiFileTestUtil
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

import scala.io.Source

/**
  * Nikolay.Tropin
  * 04-Aug-17
  */
trait SeveralFilesHighlightingTest {

  def getProject: Project

  def module: Module

  def filesToHighlight: Array[File]

  val reporter: ProgressReporter

  def doTest(): Unit = {
    val allFiles = filesToHighlight
      .filter(f => f.isDirectory || isScalaFile(f) || isFlagsFile(f))
      .groupBy(f => FilenameUtils.removeExtension(f.getPath))

    var idx = 0
    for ((basePath, files) <- allFiles) {
      if (allFiles.size > 1) {
        reporter.notify(s"$basePath (${idx + 1} of ${allFiles.size})")
      }
      annotateFiles(files, reporter)
      idx += 1
    }
    reporter.reportResults()
  }

  private def addFileToProject(file: File, relativeTo: File): PsiFile = {
    val text: String = content(file)
    val path = relativeTo.toPath.relativize(file.toPath)
    PsiFileTestUtil.addFileToProject(path, text, getProject)
  }

  private def content(file: File) = using(Source.fromFile(file))(_.getLines.mkString("\n"))

  private def removeFile(psiFile: PsiFile): Unit = {
    inWriteAction(psiFile.delete())
  }

  private def annotateFiles(files: Array[File], reporter: ProgressReporter): Unit = {
    def allFiles(f: File): Seq[File] =
      if (f.isDirectory) f.listFiles.flatMap(allFiles)
      else               Seq(f)

    def parseScalacFlags(f: File): Seq[String] =
      using(Source.fromFile(f, "UTF-8"))(_.getLines().map(_.trim).filter(_.nonEmpty).toList)

    val root = files match {
      case Array(file) if file.isDirectory => file
      case Array(file, _*)                 => file.getParentFile
    }

    val (flagFiles, sourceFiles) = files.flatMap(allFiles).partition(isFlagsFile)
    val addedFiles = sourceFiles.map(addFileToProject(_, relativeTo = root))

    try {
      module.scalaCompilerSettings.additionalCompilerOptions = flagFiles.flatMap(parseScalacFlags)
      ScalaCompilerConfiguration.incModificationCount()
      addedFiles.foreach(AllProjectHighlightingTest.annotateScalaFile(_, reporter))
    } finally {
      addedFiles.foreach(removeFile)
      module.scalaCompilerSettings.additionalCompilerOptions = Seq.empty
    }
  }

  private def isScalaFile(f: File) = f.getName.endsWith(ScalaFileType.INSTANCE.getDefaultExtension)
  private def isFlagsFile(f: File) = f.getName.endsWith("flags")

}