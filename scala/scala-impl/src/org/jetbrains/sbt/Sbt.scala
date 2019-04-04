package org.jetbrains.sbt

import javax.swing.Icon
import org.jetbrains.plugins.scala.buildinfo.BuildInfo
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project.Version

/**
 * @author Pavel Fatin
 */
object Sbt {
  val Name = "sbt"

  val BuildFile = "build.sbt"

  val PropertiesFile = "build.properties"

  val ProjectDirectory = "project"

  val PluginsFile = "plugins.sbt"

  val TargetDirectory = "target"

  val ModulesDirectory = ".idea/modules"

  val ProjectDescription = "sbt project"

  val ProjectLongDescription = "Project backed by sbt"

  val BuildModuleSuffix = "-build"

  val BuildModuleName = "sbt module"

  val BuildModuleDescription = "sbt modules are used to mark content roots and to provide libraries for sbt project definitions"

  val BuildLibraryName = "sbt-and-plugins"

  val UnmanagedLibraryName = "unmanaged-jars"

  val UnmanagedSourcesAndDocsName = "unmanaged-sources-and-docs"

  val DefinitionHolderClasses = Seq("sbt.Plugin", "sbt.Build")

  // this should be in sync with sbt.BuildUtil.baseImports
  val DefaultImplicitImports = Seq("sbt._", "Process._", "Keys._", "dsl._")

  val LatestVersion: Version = Version(BuildInfo.sbtLatestVersion)
  val Latest_1_0: Version = Version(BuildInfo.sbtLatest_1_0)
  val Latest_0_12: Version = Version(BuildInfo.sbtLatest_0_12)
  val Latest_0_13: Version = Version(BuildInfo.sbtLatest_0_13)

  val Icon: Icon = Icons.SBT

  val FolderIcon: Icon = Icons.SBT_FOLDER
}
