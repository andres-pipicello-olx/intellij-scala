package org.jetbrains.plugins.scala
package lang
package psi
package impl

import com.intellij.extapi.psi.{ASTWrapperPsiElement, StubBasedPsiElementBase}
import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.impl.CheckUtil
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, StubBasedPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

abstract class ScalaPsiElementImpl(node: ASTNode) extends ASTWrapperPsiElement(node)
  with ScalaPsiElement {

  override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit =
    super.annotate(holder, typeAware)

  override def getStartOffsetInParent: Int = this.child match {
    case null => super.getStartOffsetInParent
    case element => element.getStartOffsetInParent
  }

  override def getPrevSibling: PsiElement = this.child match {
    case null => super.getPrevSibling
    case element => element.getPrevSibling
  }

  override def getNextSibling: PsiElement = this.child match {
    case null => super.getNextSibling
    case element => element.getNextSibling
  }

  override def findLastChildByType[T <: PsiElement](t: IElementType): T = {
    super[ScalaPsiElement].findLastChildByType(t)
  }

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = findChildByClass[T](clazz)

  // todo override in more specific cases
  override def replace(newElement: PsiElement): PsiElement = {
    val newElementCopy = newElement.copy
    getParent.getNode.replaceChild(getNode, newElementCopy.getNode)
    newElementCopy
  }

  override def delete() {
    getParent match {
      case x: LazyParseablePsiElement =>
        CheckUtil.checkWritable(this)
        x.deleteChildInternal(getNode)
      case _ => super.delete()
    }
  }

  override def subtreeChanged(): Unit = {
    ScalaPsiManager.AnyScalaPsiModificationTracker.incModificationCount()
    super.subtreeChanged()
  }
}

abstract class ScalaStubBasedElementImpl[T <: PsiElement, S <: StubElement[T]](stub: S,
                                                                               nodeType: stubs.elements.ScStubElementType[S, T],
                                                                               node: ASTNode)
  extends StubBasedPsiElementBase[S](stub, if (stub == null) null else nodeType, node)
    with StubBasedPsiElement[S]
    with ScalaPsiElement {

  override final def getElementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement] = super.getElementType

  override def getStartOffsetInParent: Int = this.child match {
    case null => super.getStartOffsetInParent
    case element => element.getStartOffsetInParent
  }

  override def getPrevSibling: PsiElement = this.child match {
    case null => super.getPrevSibling
    case element => ScalaPsiUtil.getStubOrPsiSibling(element)
  }

  override def getNextSibling: PsiElement = this.child match {
    case null => super.getNextSibling
    case element => ScalaPsiUtil.getStubOrPsiSibling(element, next = true)
  }

  override def findLastChildByType[T <: PsiElement](t: IElementType): T = {
    super[ScalaPsiElement].findLastChildByType(t)
  }

  protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = findChildByClass[T](clazz)

  override def delete() {
    getParent match {
      case x: LazyParseablePsiElement =>
        CheckUtil.checkWritable(this)
        x.deleteChildInternal(getNode)
      case _ => super.delete()
    }
  }

  //may use stubs even if AstNode exists
  def byStubOrPsi[R](byStub: S => R)(byPsi: => R): R = getGreenStub match {
    case null => byPsi
    case s => byStub(s)
  }

  //byStub branch is used only if AstNode is missing
  def byPsiOrStub[R](byPsi: => R)(byStub: S => R): R = getStub match {
    case null => byPsi
    case s => byStub(s)
  }

  override def subtreeChanged(): Unit = {
    ScalaPsiManager.AnyScalaPsiModificationTracker.incModificationCount()
    super.subtreeChanged()
  }

  override def copyCopyableDataTo(clone: UserDataHolderBase): Unit = {
    super.copyCopyableDataTo(clone)

    clone.asInstanceOf[ScalaStubBasedElementImpl[_, _]]
      .setContext(this.context, this.child)
  }
}