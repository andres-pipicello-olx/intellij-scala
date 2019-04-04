package org.jetbrains.plugins.scala
package project
package external

import java.io.File
import java.{util => ju}

import com.intellij.facet.ModifiableFacetModel
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectKeys}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.{DisposeAwareProjectChange, ExternalSystemApiUtil}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library

import scala.collection.JavaConverters

/**
 * @author Pavel Fatin
 */
abstract class AbstractDataService[E, I](key: Key[E]) extends AbstractProjectDataService[E, I] {

  def createImporter(toImport: Seq[DataNode[E]],
                     projectData: ProjectData,
                     project: Project,
                     modelsProvider: IdeModifiableModelsProvider): Importer[E]

  def getTargetDataKey: Key[E] = key

  override final def importData(toImport: ju.Collection[DataNode[E]],
                                projectData: ProjectData,
                                project: Project,
                                modelsProvider: IdeModifiableModelsProvider): Unit = {
    import JavaConverters._
    createImporter(toImport.asScala.toSeq, projectData, project, modelsProvider).importData()
  }
}

/**
 * The purposes of this trait are the following:
 *    - Encapsulate logic necessary for importing specified data
 *    - Wrap "unsafe" methods from IdeModifiableModelsProvider
 *    - Collect import parameters as class fields to eliminate necessity of
 *      dragging them into each and every method of ProjectDataService
 *    - Abstract from External System's API which is rather unstable
 */
trait Importer[E] {
  val dataToImport: Seq[DataNode[E]]
  val projectData: ProjectData
  val project: Project
  val modelsProvider: IdeModifiableModelsProvider

  def importData(): Unit

  // IdeModifiableModelsProvider wrappers

  def findIdeModule(name: String): Option[Module] =
    Option(modelsProvider.findIdeModule(name))

  def findIdeModule(data: ModuleData): Option[Module] =
    Option(modelsProvider.findIdeModule(data))

  def getModifiableFacetModel(module: Module): ModifiableFacetModel =
    modelsProvider.getModifiableFacetModel(module)

  def getModifiableLibraryModel(library: Library): Library.ModifiableModel =
    modelsProvider.getModifiableLibraryModel(library)

  def getModifiableRootModel(module: Module): ModifiableRootModel =
    modelsProvider.getModifiableRootModel(module)

  def getModules: Array[Module] =
    modelsProvider.getModules

  // Utility methods

  def getIdeModuleByNode(node: DataNode[_]): Option[Module] =
    for {
      moduleData <- Option(node.getData(ProjectKeys.MODULE))
      module <- findIdeModule(moduleData)
    } yield module

  def executeProjectChangeAction(action: => Unit): Unit =
    ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
      override def execute(): Unit = action
    })

  def setScalaSdk(library: Library,
                  compilerClasspath: Seq[File])
                 (maybeVersion: Option[Version] = library.scalaVersion): Unit =
    Importer.setScalaSdk(modelsProvider, library, ScalaLibraryProperties(maybeVersion, compilerClasspath))
}

object Importer {

  def setScalaSdk(modelsProvider: IdeModifiableModelsProvider,
                  library: Library,
                  properties: ScalaLibraryProperties): Unit =
    modelsProvider.getModifiableLibraryModel(library) match { // FIXME: should be implemented in External System
      case modelEx: LibraryEx.ModifiableModelEx =>
        modelEx.setKind(ScalaLibraryType.Kind)
        modelEx.setProperties(properties)
    }
}

abstract class AbstractImporter[E](val dataToImport: Seq[DataNode[E]],
                                   val projectData: ProjectData,
                                   val project: Project,
                                   val modelsProvider: IdeModifiableModelsProvider) extends Importer[E]
