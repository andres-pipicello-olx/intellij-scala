package org.jetbrains.jps
package incremental
package scala

import _root_.java.io.File
import _root_.java.util

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.java.{JavaBuilderUtil, JavaSourceRootDescriptor}

import _root_.scala.collection.JavaConverters._
import _root_.scala.collection.mutable

/**
  * Nikolay.Tropin
  * 11/19/13
  */
class IdeaIncrementalBuilder(category: BuilderCategory) extends ModuleLevelBuilder(category) {

  import ModuleLevelBuilder._
  import ScalaBuilder._
  import messages._

  override def getPresentableName: String = "Scala IDEA builder"

  override def build(context: CompileContext,
                     chunk: ModuleChunk,
                     dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget],
                     outputConsumer: ModuleLevelBuilder.OutputConsumer): ExitCode = {

    if (isDisabled(context, chunk) || ChunkExclusionService.isExcluded(chunk))
      return ExitCode.NOTHING_DONE

    context.processMessage(new ProgressMessage("Searching for compilable files..."))

    val sourceDependencies = SourceDependenciesProviderService.getSourceDependenciesFor(chunk)
    if (sourceDependencies.nonEmpty) {
      val message = "IDEA incremental compiler cannot handle shared source modules: " +
        sourceDependencies.map(_.getName).mkString(", ") +
        ".\nPlease enable sbt incremental compiler for the project."
      context.processMessage(new CompilerMessage("scala", BuildMessage.Kind.ERROR, message))
      return ExitCode.ABORT
    }

    val sources = collectSources(context, chunk, dirtyFilesHolder)
    if (sources.isEmpty) return ExitCode.NOTHING_DONE

    if (hasBuildModules(chunk)) return ExitCode.NOTHING_DONE // *.scala files in sbt "build" modules are rightly excluded from compilation

    if (!InitialScalaBuilder.hasScalaModules(context, chunk)) {
      val message = "skipping Scala files without a Scala SDK in module(s) " + chunk.getPresentableShortName
      context.processMessage(new CompilerMessage("scala", BuildMessage.Kind.WARNING, message))
      return ExitCode.NOTHING_DONE
    }

    val packageObjectsData = local.PackageObjectsData.getFor(context)
    if (JavaBuilderUtil.isForcedRecompilationAllJavaModules(context)) { //rebuild
      packageObjectsData.clear()
    }
    else {
      val additionalFiles = packageObjectsData.invalidatedPackageObjects(sources).filter(_.exists)
      if (additionalFiles.nonEmpty) {
        (sources ++ additionalFiles).foreach(f => FSOperations.markDirty(context, fs.CompilationRound.NEXT, f))
        return ExitCode.ADDITIONAL_PASS_REQUIRED
      }
    }

    val delta = context.getProjectDescriptor.dataManager.getMappings.createDelta()
    val callback = delta.getCallback

    val modules = chunk.getModules.asScala.toSet

    val successfullyCompiled = mutable.Set.empty[File]

    val compilerName = if (data.CompilerData.hasDotty(modules)) "dotc"
    else "scalac"

    val client = new local.IdeClientIdea(compilerName, context, modules.map(_.getName).toSeq, outputConsumer,
      callback, successfullyCompiled, packageObjectsData)

    val scalaSources = sources.filter(_.getName.endsWith(".scala")).asJava

    compile(context, chunk, sources, Seq.empty, modules, client) match {
      case Left(error) =>
        client.error(error)
        ExitCode.ABORT
      case _ if client.hasReportedErrors || client.isCanceled => ExitCode.ABORT
      case Right(code) =>
        if (delta != null && JavaBuilderUtil.updateMappings(context, delta, dirtyFilesHolder, chunk, scalaSources, successfullyCompiled.asJava))
          ExitCode.ADDITIONAL_PASS_REQUIRED
        else {
          client.progress("Compilation completed", Some(1.0F))
          code
        }
    }
  }

  override def getCompilableFileExtensions: util.List[String] = util.Arrays.asList("scala", "java")

  private def isDisabled(context: CompileContext, chunk: ModuleChunk): Boolean = {
    val settings = projectSettings(context)

    def wrongIncrType = settings.getIncrementalityType != model.IncrementalityType.IDEA

    def wrongCompileOrder = {
      import model.CompileOrder._
      settings.getCompilerSettings(chunk).getCompileOrder match {
        case JavaThenScala => getCategory == BuilderCategory.SOURCE_PROCESSOR
        case ScalaThenJava | Mixed => getCategory == BuilderCategory.OVERWRITING_TRANSLATOR
        case _ => false
      }
    }
    wrongIncrType || wrongCompileOrder
  }

  private def collectSources(context: CompileContext,
                             chunk: ModuleChunk,
                             dirtyFilesHolder: DirtyFilesHolder[JavaSourceRootDescriptor, ModuleBuildTarget]): Seq[File] = {

    val result = mutable.ListBuffer.empty[File]

    val project = context.getProjectDescriptor

    val compileOrder = projectSettings(context).getCompilerSettings(chunk).getCompileOrder
    val extensionsToCollect = compileOrder match {
      case model.CompileOrder.Mixed => List(".scala", ".java")
      case _ => List(".scala")
    }

    def checkAndCollectFile(file: File): Boolean = {
      val fileName = file.getName
      if (extensionsToCollect.exists(fileName.endsWith))
        result += file

      true
    }

    dirtyFilesHolder.processDirtyFiles((_: ModuleBuildTarget, file: File, _: JavaSourceRootDescriptor) => checkAndCollectFile(file))

    for {
      target <- chunk.getTargets.asScala
      tempRoot <- project.getBuildRootIndex.getTempTargetRoots(target, context).asScala
    } {
      FileUtil.processFilesRecursively(tempRoot.getRootFile, (file: File) => checkAndCollectFile(file))
    }


    //if no scala files to compile, return empty seq
    if (!result.exists(_.getName.endsWith(".scala"))) Seq.empty
    else result
  }


}
