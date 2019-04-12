package org.jetbrains.plugins.scala
package lang
package psi
package impl

import java.{util => ju}

import com.intellij.lang.{ASTNode, LanguageParserDefinitions, PsiBuilder, PsiBuilderFactory}
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi._
import com.intellij.psi.impl.compiled.ClsParameterImpl
import com.intellij.psi.impl.source.DummyHolderFactory
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.tree.{IElementType, IFileElementType}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.apache.commons.lang.StringUtils
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.{ScXmlEndTag, ScXmlStartTag}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScBlockImpl
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocResolvableCodeReference, ScDocSyntaxElement}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.reflect.ClassTag

final class ScalaPsiElementFactoryImpl(project: Project) extends JVMElementFactory {

  def createDocCommentFromText(text: String): PsiDocComment = ???

  def isValidClassName(name: String): Boolean = isIdentifier(name)

  def isValidMethodName(name: String): Boolean = isIdentifier(name)

  def isValidParameterName(name: String): Boolean = isIdentifier(name)

  def isValidFieldName(name: String): Boolean = isIdentifier(name)

  def isValidLocalVariableName(name: String): Boolean = isIdentifier(name)

  def createConstructor(name: String, context: PsiElement): PsiMethod = ???

  def createParameter(name: String, `type`: PsiType, context: PsiElement): PsiParameter = ???

  def createClass(name: String): PsiClass = throw new IncorrectOperationException

  def createInterface(name: String): PsiClass = throw new IncorrectOperationException

  def createEnum(name: String): PsiClass = throw new IncorrectOperationException

  def createField(name: String, `type`: PsiType): PsiField = throw new IncorrectOperationException

  def createMethod(name: String, returnType: PsiType): PsiMethod = throw new IncorrectOperationException

  def createMethod(name: String, returnType: PsiType, context: PsiElement): PsiMethod = throw new IncorrectOperationException

  def createConstructor(): PsiMethod =
    ScalaPsiElementFactory.createMethodFromText(
      """def this() {
        |this()
        |}""".stripMargin
    )(project)

  def createConstructor(name: String): PsiMethod = createConstructor()

  def createClassInitializer(): PsiClassInitializer = throw new IncorrectOperationException

  def createParameter(name: String, `type`: PsiType): PsiParameter = {
    implicit val context: ProjectContext = project
    val typeText = `type`.toScType().canonicalText
    ScalaPsiElementFactory.createParameterFromText(s"$name: $typeText")
  }

  def createParameterList(names: Array[String], types: Array[PsiType]): PsiParameterList = throw new IncorrectOperationException

  def createMethodFromText(text: String, context: PsiElement): PsiMethod = throw new IncorrectOperationException

  def createAnnotationFromText(annotationText: String, context: PsiElement): PsiAnnotation = throw new IncorrectOperationException

  def createReferenceElementByType(`type`: PsiClassType): PsiElement = ???

  def createTypeParameterList(): PsiTypeParameterList = ???

  def createTypeParameter(name: String, superTypes: Array[PsiClassType]): PsiTypeParameter = ???

  def createType(aClass: PsiClass): PsiClassType = ???

  def createAnnotationType(name: String): PsiClass = ???

  def createType(resolve: PsiClass, substitutor: PsiSubstitutor): PsiClassType = ???

  def createType(resolve: PsiClass, substitutor: PsiSubstitutor, languageLevel: LanguageLevel): PsiClassType = ???

  def createType(resolve: PsiClass, substitutor: PsiSubstitutor, languageLevel: LanguageLevel, annotations: Array[PsiAnnotation]): PsiClassType = ???

  def createType(aClass: PsiClass, parameters: PsiType): PsiClassType = ???

  def createRawSubstitutor(owner: PsiTypeParameterListOwner): PsiSubstitutor = ???

  def createSubstitutor(map: ju.Map[PsiTypeParameter, PsiType]): PsiSubstitutor = ???

  def createPrimitiveType(text: String): PsiPrimitiveType = ???

  def createTypeByFQClassName(qName: String): PsiClassType = ???

  def createTypeByFQClassName(qName: String, resolveScope: GlobalSearchScope): PsiClassType = ???

  def createType(aClass: PsiClass, parameters: PsiType*): PsiClassType = ???

  def createExpressionFromText(text: String, context: PsiElement): PsiElement =
    ScalaPsiElementFactory.createExpressionFromText(text, context)
}

object ScalaPsiElementFactory {

  import ScalaPsiUtil._
  import lang.parser.parsing.{base => parsingBase, statements => parsingStat, _}
  import lexer.ScalaTokenTypes._
  import refactoring.util.ScalaNamesUtil._

  def createExpressionFromText(text: String, context: PsiElement): ScExpression = {
    try {
      createExpressionWithContextFromText(text, context, context)
    } catch {
      case p: ProcessCanceledException => throw p
      case throwable: Throwable => throw elementCreationException("expression", text, context, throwable)
    }
  }

  def createScalaFileFromText(text: String)
                             (implicit ctx: ProjectContext): ScalaFile =
    PsiFileFactory.getInstance(ctx)
      .createFileFromText(s"dummy.${ScalaFileType.INSTANCE.getDefaultExtension}", ScalaFileType.INSTANCE, convertLineSeparators(text))
      .asInstanceOf[ScalaFile]


  def createElementFromText(text: String)
                           (implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText(text).getFirstChild

  def createElementFromText[E <: ScalaPsiElement](text: String, returnType: Class[E])
                                                 (implicit ctx: ProjectContext): E =
    createElementFromText(text)(ctx).asInstanceOf[E]

  def createWildcardNode(implicit ctx: ProjectContext): ASTNode =
    createScalaFileFromText("import a._").getLastChild.getLastChild.getLastChild.getNode

  def createClauseFromText(clauseText: String = "()")
                          (implicit ctx: ProjectContext): ScParameterClause = {
    val function = createMethodFromText(s"def foo$clauseText = null")
    function.paramClauses.clauses.head
  }

  def createClauseForFunctionExprFromText(clauseText: String)
                                         (implicit ctx: ProjectContext): ScParameterClause = {
    val functionExpression = createElementFromText(s"$clauseText => null", classOf[ScFunctionExpr])
    functionExpression.params.clauses.head
  }

  def createParameterFromText(paramText: String)
                             (implicit ctx: ProjectContext): ScParameter = {
    val function = createMethodFromText(s"def foo($paramText) = null")
    function.parameters.head
  }

  // Supports "_" parameter name
  def createFunctionParameterFromText(paramText: String)
                                     (implicit ctx: ProjectContext): ScParameter = {
    val function = createScalaFileFromText(s"($paramText) =>").getFirstChild.asInstanceOf[ScFunctionExpr]
    function.parameters.head
  }

  def createPatternFromText(patternText: String)
                           (implicit ctx: ProjectContext): ScPattern = {
    val matchStatement = createElementFromText(s"x match { case $patternText => }", classOf[ScMatch])
    matchStatement.clauses.head.pattern.get
  }

  def createTypeParameterFromText(name: String)
                                 (implicit ctx: ProjectContext): ScTypeParam = {
    val function = createMethodFromText(s"def foo[$name]() = {}")
    function.typeParameters.head
  }

  def createMatch(element: String, caseClauses: Seq[String])
                 (implicit ctx: ProjectContext): ScMatch = {
    val clausesText = caseClauses.mkString("{ ", "\n", " }")
    createElementFromText(s"$element match $clausesText", classOf[ScMatch])
  }

  def createMethodFromText(text: String)
                          (implicit ctx: ProjectContext): ScFunction =
    createElementFromText(text, classOf[ScFunction])

  def createExpressionFromText(text: String)
                              (implicit context: ProjectContext): ScExpression =
    getExprFromFirstDef(s"val b = ($text)") match {
      case ScParenthesisedExpr(e) => e
      case e => e
    }

  def createReferenceExpressionFromText(text: String)
                                       (implicit ctx: ProjectContext): ScReferenceExpression =
    createElementFromText(text, classOf[ScReferenceExpression])

  def createImplicitClauseFromTextWithContext(clauses: Seq[String],
                                              context: PsiElement,
                                              isClassParameter: Boolean): ScParameterClause =
    clauses match {
      case Seq() => throw new IncorrectOperationException("At least one clause required.")
      case _ =>
        createElementWithContext[ScParameterClause](s"(implicit ${clauses.commaSeparated()})", context, contextLastChild(context)) {
          case builder if isClassParameter => top.params.ImplicitClassParamClause.parse(builder)
          case builder => params.ImplicitParamClause.parse(builder)
        }
    }

  def createEmptyClassParamClauseWithContext(context: PsiElement): ScParameterClause =
    createElementWithContext[ScParameterClause]("()", context, contextLastChild(context))(top.params.ClassParamClause.parse)

  def createClassParamClausesWithContext(text: String, context: PsiElement): ScParameters =
    createElementWithContext[ScParameters](text, context, contextLastChild(context))(top.params.ClassParamClauses.parse)

  def createConstructorFromText(text: String, context: PsiElement, child: PsiElement): ScConstructorInvocation =
    createElementWithContext[ScConstructorInvocation](text, context, child)(parsingBase.Constructor.parse)

  def createParamClausesWithContext(text: String, context: PsiElement, child: PsiElement): ScParameters =
    createElementWithContext[ScParameters](text, context, child)(params.ParamClauses.parse)

  private def contextLastChild(element: PsiElement): PsiElement =
    stub(element)
      .map(_.getChildrenStubs)
      .fold(element.getLastChild) {
        at(_)().orNull
      }

  def createPatternFromTextWithContext(patternText: String, context: PsiElement, child: PsiElement): ScPattern =
    createElementWithContext[ScCaseClause](kCASE + " " + patternText, context, child)(patterns.CaseClause.parse)
      .pattern
      .getOrElse {
        throw elementCreationException("pattern", patternText, context)
      }

  def createAnAnnotation(name: String)
                        (implicit ctx: ProjectContext): ScAnnotation = {
    val text =
      s"""@$name
          |def foo""".stripMargin
    createElementFromText(text).getFirstChild.getFirstChild.asInstanceOf[ScAnnotation]
  }

  def createAnnotationExpression(text: String)
                                (implicit ctx: ProjectContext): ScAnnotationExpr =
    createElement(text)(expressions.AnnotationExpr.parse)
      .asInstanceOf[ScAnnotationExpr]

  def createBlockExpressionWithoutBracesFromText(text: String)
                                                (implicit ctx: ProjectContext): ScBlockImpl = {
    createElement(text)(expressions.Block.parse(_, hasBrace = false, needNode = true)) match {
      case b: ScBlockImpl => b
      case _ => null
    }
  }

  def createOptionExpressionFromText(text: String)
                                    (implicit ctx: ProjectContext): Option[ScExpression] = {
    val dummyFile = createScalaFileFromText(text)
    Option(dummyFile.getFirstChild).collect {
      case expression: ScExpression if expression.getNextSibling == null && !PsiTreeUtil.hasErrorElements(dummyFile) => expression
    }
  }

  def createIdentifier(name: String)
                      (implicit ctx: ProjectContext): ASTNode = {
    try {
      createScalaFileFromText(s"package ${escapeKeyword(name)}").getNode
        .getLastChildNode.getLastChildNode.getLastChildNode
    }
    catch {
      case p: ProcessCanceledException => throw p
      case throwable: Throwable => throw elementCreationException("identifier", name, cause = throwable)
    }
  }

  def createModifierFromText(modifier: String)
                            (implicit context: ProjectContext): PsiElement =
    createScalaFileFromText(s"$modifier class a").typeDefinitions.head.getModifierList.getFirstChild

  def createImportExprFromText(name: String)
                              (implicit ctx: ProjectContext): ScImportExpr =
    createScalaFileFromText(s"import ${escapeKeywordsFqn(name)}")
      .getLastChild.getLastChild.asInstanceOf[ScImportExpr]

  def createImportFromText(text: String)
                          (implicit ctx: ProjectContext): ScImportStmt =
    createElementFromText(text, classOf[ScImportStmt])

  def createReferenceFromText(name: String)
                             (implicit ctx: ProjectContext): ScStableCodeReference = {
    try {
      val importStatement = createElementFromText(s"import ${escapeKeywordsFqn(name)}", classOf[ScImportStmt])
      importStatement.importExprs.head.reference.orNull
    }
    catch {
      case p: ProcessCanceledException => throw p
      case throwable: Throwable => throw elementCreationException("reference", name, cause = throwable)
    }
  }

  def createDeclaration(`type`: ScType, name: String, isVariable: Boolean,
                        exprText: String, isPresentableText: Boolean = false)
                       (implicit context: ProjectContext): ScValueOrVariable = {
    val typeText = `type` match {
      case null => ""
      case tp if isPresentableText => tp.presentableText
      case tp => tp.canonicalText
    }

    createDeclaration(name, typeText, isVariable, createExpressionFromText(exprText))
  }

  def createDeclaration(name: String, typeName: String, isVariable: Boolean, body: ScExpression)
                       (implicit context: ProjectContext): ScValueOrVariable =
    createMember(name, typeName, body, isVariable = isVariable).asInstanceOf[ScValueOrVariable]

  private[this] def createMember(name: String, typeName: String, body: ScExpression,
                                 modifiers: String = "",
                                 isVariable: Boolean = false)
                                (implicit context: ProjectContext): ScMember = {
    def stmtText: ScBlockStatement => String = {
      case block@ScBlock(st) if !block.hasRBrace => stmtText(st)
      case fun@ScFunctionExpr(parSeq, Some(result)) =>
        val paramText = parSeq match {
          case Seq(parameter) if parameter.typeElement.isDefined && parameter.getPrevSiblingNotWhitespace == null =>
            parameter.getText.parenthesize()
          case _ => fun.params.getText
        }

        val resultText = result match {
          case block: ScBlock if !block.hasRBrace && block.statements.size != 1 =>
            s"""{
               |${block.getText}
               |}""".stripMargin
          case block@ScBlock(st) if !block.hasRBrace => stmtText(st)
          case _ => result.getText
        }
        s"$paramText $functionArrow $resultText"
      case null => ""
      case statement => statement.getText
    }

    val typedName = typeName match {
      case null | "" => name
      case _ =>
        // throws an exception if type name is incorrect
        createTypeElementFromText(typeName)

        val space = if (isOpCharacter(name.last)) " " else ""
        s"$name$space: $typeName"
    }

    val text = s"$modifiers${if (modifiers.isEmpty) "" else " "}${if (isVariable) kVAR else kVAL} $typedName = ${stmtText(body)}"

    createMemberFromText(text)
  }

  def createValFromVarDefinition(parameter: ScClassParameter): ScClassParameter = {
    val clauseText = replaceKeywordTokenIn(parameter).parenthesize()

    val classParameters = createClassParamClausesWithContext(clauseText, parameter).params
    classParameters.head.asInstanceOf[ScClassParameter]
  }

  def createValFromVarDefinition(variable: ScVariable): ScValue =
    createValueOrVariable(variable, kVAR, kVAL).asInstanceOf[ScValue]

  def createVarFromValDeclaration(value: ScValue): ScVariable =
    createValueOrVariable(value, kVAL, kVAR).asInstanceOf[ScVariable]

  private[this] def createValueOrVariable(valOrVar: ScValueOrVariable,
                                          fromToken: IElementType,
                                          toToken: IElementType)
                                         (implicit context: ProjectContext = valOrVar.projectContext): ScMember =
    createMemberFromText(replaceKeywordTokenIn(valOrVar, fromToken, toToken))

  private[this] def replaceKeywordTokenIn(member: ScMember,
                                          fromToken: IElementType = kVAR,
                                          toToken: IElementType = kVAL) = {
    val offset = member.findFirstChildByType(fromToken).getStartOffsetInParent
    val memberText = member.getText

    memberText.substring(0, offset) +
      toToken +
      memberText.substring(offset + fromToken.toString.length)
  }

  def createForBinding(name: String, expr: ScExpression, typeName: String)
                      (implicit ctx: ProjectContext): ScForBinding = {
    val typeText = Option(typeName).filter {
      _.nonEmpty
    }.map { name =>
      s": $name"
    }.getOrElse("")
    val enumText = s"$name$typeText = ${expr.getText}"

    val text =
      s"""for {
          |  i <- 1 to 239
          |  $enumText
          |}""".stripMargin
    val forStmt = createElementFromText(text, classOf[ScFor])
    forStmt.enumerators.flatMap {
      _.forBindings.headOption
    }.getOrElse {
      throw elementCreationException("enumerator", enumText)
    }
  }

  def createNewLine(text: String = "\n")
                   (implicit context: ProjectContext): PsiElement =
    createNewLineNode(text).getPsi

  def createNewLineNode(text: String = "\n")
                       (implicit context: ProjectContext): ASTNode =
    createScalaFileFromText(text).getNode.getFirstChildNode

  def createBlockFromExpr(expression: ScExpression)
                         (implicit context: ProjectContext): ScExpression =
    getExprFromFirstDef(
      s"""val b = {
         |${expression.getText}
         |}""".stripMargin)

  def createAnonFunBlockFromFunExpr(expression: ScFunctionExpr)
                                   (implicit context: ProjectContext): ScExpression =
    getExprFromFirstDef(
      s"""val b = {${expression.params.getText}=>
         |${expression.result.map(_.getText).getOrElse("")}
         |}""".stripMargin)

  def createPatternDefinition(name: String, typeName: String, body: ScExpression,
                              modifiers: String = "",
                              isVariable: Boolean = false)
                             (implicit context: ProjectContext): ScPatternDefinition =
    createMember(name, typeName, body, modifiers, isVariable).asInstanceOf[ScPatternDefinition]

  private[this] def getExprFromFirstDef(text: String)
                                       (implicit context: ProjectContext): ScExpression =
    createMemberFromText(text) match {
      case ScPatternDefinition.expr(body) => body
      case _ => throw new IncorrectOperationException("Expression not found")
    }

  def createBodyFromMember(elementText: String)
                          (implicit ctx: ProjectContext): ScTemplateBody =
    createClassWithBody(elementText).extendsBlock.templateBody.orNull

  def createTemplateBody(implicit ctx: ProjectContext): ScTemplateBody =
    createBodyFromMember("")

  def createClassTemplateParents(superName: String)
                                (implicit ctx: ProjectContext): (PsiElement, ScTemplateParents) = {
    val text =
      s"""class a extends $superName {
          |}""".stripMargin
    val extendsBlock = createScalaFileFromText(text).typeDefinitions.head.extendsBlock
    (extendsBlock.findFirstChildByType(kEXTENDS), extendsBlock.templateParents.get)
  }

  def createMethodFromSignature(signature: PhysicalMethodSignature, body: String,
                                withComment: Boolean = true, withAnnotation: Boolean = true)
                               (implicit projectContext: ProjectContext): ScFunction = {
    val builder = StringBuilder.newBuilder

    val PhysicalMethodSignature(method, substitutor) = signature

    if (withComment) {
      val maybeCommentText = method.firstChild.collect {
        case comment: PsiDocComment => comment.getText
      }

      maybeCommentText.foreach(builder.append)
      if (maybeCommentText.isDefined) builder.append("\n")
    }

    if (withAnnotation) {
      val annotations = method match {
        case function: ScFunction => function.annotations.map(_.getText)
        case _ => Seq.empty
      }

      annotations.foreach(builder.append)
      if (annotations.nonEmpty) builder.append("\n")
    }

    signatureText(method, substitutor)(builder)

    builder.append(" ")
      .append(tASSIGN)
      .append(" ")
      .append(body)

    createClassWithBody(builder.toString()).functions.head
  }

  def createOverrideImplementMethod(signature: PhysicalMethodSignature, needsOverrideModifier: Boolean, body: String,
                                    withComment: Boolean = true, withAnnotation: Boolean = true)
                                   (implicit ctx: ProjectContext): ScFunction = {
    val function = createMethodFromSignature(signature, body, withComment, withAnnotation)
    addModifiersFromSignature(function, signature, needsOverrideModifier)
  }

  def createOverrideImplementType(alias: ScTypeAlias,
                                  substitutor: ScSubstitutor,
                                  needsOverrideModifier: Boolean,
                                  comment: String = "")
                                 (implicit ctx: ProjectContext): ScTypeAlias = {
    val typeSign = getOverrideImplementTypeSign(alias, substitutor, needsOverrideModifier)
    createClassWithBody(s"$comment $typeSign").aliases.head
  }

  def createOverrideImplementVariable(variable: ScTypedDefinition,
                                      substitutor: ScSubstitutor,
                                      needsOverrideModifier: Boolean,
                                      isVal: Boolean,
                                      comment: String = "",
                                      withBody: Boolean = true)
                                     (implicit ctx: ProjectContext): ScMember = {
    val variableSign = getOverrideImplementVariableSign(variable, substitutor, if (withBody) Some("_") else None, needsOverrideModifier, isVal, needsInferType = true)
    createMemberFromText(s"$comment $variableSign")
  }

  def createOverrideImplementVariableWithClass(variable: ScTypedDefinition,
                                               substitutor: ScSubstitutor,
                                               needsOverrideModifier: Boolean,
                                               isVal: Boolean,
                                               clazz: ScTemplateDefinition,
                                               comment: String = "",
                                               withBody: Boolean = true)(implicit ctx: ProjectContext): ScMember = {
    val member = createOverrideImplementVariable(variable, substitutor, needsOverrideModifier, isVal, comment, withBody)
    clazz match {
      case td: ScTypeDefinition => member.syntheticContainingClass = td
      case _ =>
    }
    member
  }

  def createSemicolon(implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText(";").findElementAt(0)

  private def addModifiersFromSignature(function: ScFunction, sign: PhysicalMethodSignature, addOverride: Boolean): ScFunction = {
    sign.method match {
      case fun: ScFunction =>
        import lexer.ScalaModifier._
        val res = function.getModifierList.replace(fun.getModifierList)
        if (res.getText.nonEmpty) res.getParent.addAfter(createWhitespace(fun.getManager), res)
        function.setModifierProperty(ABSTRACT, value = false)
        if (!fun.hasModifierProperty("override") && addOverride) function.setModifierProperty(OVERRIDE)
      case m: PsiMethod =>
        var hasOverride = false
        if (m.getModifierList.getNode != null)
          for (modifier <- m.getModifierList.getNode.getChildren(null); modText = modifier.getText) {
            modText match {
              case "override" => hasOverride = true; function.setModifierProperty("override")
              case "protected" => function.setModifierProperty("protected")
              case "final" => function.setModifierProperty("final")
              case _ =>
            }
          }
        if (addOverride && !hasOverride) function.setModifierProperty("override")
    }
    function
  }

  private def signatureText(method: PsiMethod, substitutor: ScSubstitutor)
                           (myBuilder: StringBuilder)
                           (implicit projectContext: ProjectContext): Unit = {
    myBuilder.append(kDEF)
      .append(" ")
      .append(escapeKeyword(method.name))

    val typeParameters = method match {
      case function: ScFunction if function.typeParameters.nonEmpty =>
        def buildText(typeParam: ScTypeParam): String = {
          val variance = if (typeParam.isContravariant) "-" else if (typeParam.isCovariant) "+" else ""
          val clauseText = typeParam.typeParametersClause match {
            case None => ""
            case Some(x) => x.typeParameters.map(buildText).mkString("[", ",", "]")
          }

          val lowerBoundText = typeParam.lowerBound.toOption.collect {
            case x if !x.isNothing => " >: " + substitutor(x).canonicalText
          }

          val upperBoundText = typeParam.upperBound.toOption.collect {
            case x if !x.isAny => " <: " + substitutor(x).canonicalText
          }

          val viewBoundText = typeParam.viewBound.map { x =>
            " <% " + substitutor(x).canonicalText
          }

          val contextBoundText = typeParam.contextBound.map { tp =>
            " : " + refactoring.util.ScTypeUtil.stripTypeArgs(substitutor(tp)).canonicalText
          }

          val boundsText = (lowerBoundText.toSeq ++ upperBoundText.toSeq ++ viewBoundText ++ contextBoundText).mkString
          s"$variance${typeParam.name}$clauseText$boundsText"
        }

        function.typeParameters.map(buildText)
      case _ if method.hasTypeParameters =>
        for {
          param <- method.getTypeParameters.toSeq
          extendsTypes = param.getExtendsListTypes
          extendsTypesText = if (extendsTypes.nonEmpty) {
            extendsTypes.map { classType =>
              substitutor(classType.toScType()).canonicalText
            }.mkString(" <: ", " with ", "")
          } else ""
        } yield param.name + extendsTypesText
      case _ => Seq.empty
    }

    if (typeParameters.nonEmpty) {
      val typeParametersText = typeParameters.mkString(tLSQBRACKET.toString, ", ", tRSQBRACKET.toString)
      myBuilder.append(typeParametersText)
    }

    // do not substitute aliases
    method match {
      case method: ScFunction if method.paramClauses != null =>
        for (paramClause <- method.paramClauses.clauses) {
          val parameters = paramClause.parameters.map { param =>
            val arrow = if (param.isCallByNameParameter) functionArrow else ""
            val asterisk = if (param.isRepeatedParameter) tSTAR.toString else ""

            val name = param.name
            val tpe = param.`type`().map(substitutor).getOrAny

            s"$name${colon(name)} $arrow${tpe.canonicalText}$asterisk"
          }

          myBuilder.append(parameters.mkString(if (paramClause.isImplicit) "(implicit " else "(", ", ", ")"))
        }
      case _ if !method.isParameterless || !method.hasQueryLikeName =>
        val params = for (param <- method.parameters) yield {
          val paramName = param.name match {
            case null => param match {
              case param: ClsParameterImpl => param.getStub.getName
              case _ => null
            }
            case x => x
          }

          val pName: String = escapeKeyword(paramName)
          val colon = if (pName.endsWith("_")) " " else ""
          val paramType = {
            val tpe = param.paramType()
            substitutor(tpe)
          }

          val asterisk = if (param.isVarArgs) "*" else ""

          val typeText = paramType match {
            case t if t.isAnyRef => "scala.Any"
            case t => t.canonicalText
          }

          s"$pName$colon: $typeText$asterisk"
        }

        myBuilder.append(params.mkString("(", ", ", ")"))
      case _ =>
    }

    val maybeReturnType = method match {
      case function: ScFunction =>
        function.returnType.toOption.map {
          (_, function.isParameterless && function.typeParameters.isEmpty && isIdentifier(method.name + tCOLON))
        }
      case _ =>
        Option(method.getReturnType).map { returnType =>
          (returnType.toScType(), false)
        }
    }

    maybeReturnType match {
      case Some((returnType, flag)) =>
        val typeText = substitutor(returnType).canonicalText match {
          case "_root_.java.lang.Object" => "AnyRef"
          case text => text
        }

        myBuilder.append(if (flag) " " else "")
          .append(tCOLON)
          .append(" ")
          .append(typeText)
      case _ =>
    }
  }

  def getOverrideImplementTypeSign(alias: ScTypeAlias, substitutor: ScSubstitutor, needsOverride: Boolean): String = {
    try {
      alias match {
        case alias: ScTypeAliasDefinition =>
          val overrideText = if (needsOverride && !alias.hasModifierProperty("override")) "override " else ""
          val modifiersText = alias.getModifierList.getText
          val typeText = substitutor(alias.aliasedType.getOrAny).canonicalText
          s"$overrideText$modifiersText type ${alias.name} = $typeText"
        case alias: ScTypeAliasDeclaration =>
          val overrideText = if (needsOverride) "override " else ""
          s"$overrideText${alias.getModifierList.getText} type ${alias.name} = this.type"
      }
    } catch {
      case p: ProcessCanceledException => throw p
      case e: Exception =>
        e.printStackTrace()
        ""
    }
  }

  private def colon(name: String) = (if (isIdentifier(name + tCOLON)) " " else "") + tCOLON + " "

  private def getOverrideImplementVariableSign(variable: ScTypedDefinition, substitutor: ScSubstitutor,
                                               body: Option[String], needsOverride: Boolean,
                                               isVal: Boolean, needsInferType: Boolean): String = {
    val modOwner: ScModifierListOwner = nameContext(variable) match {
      case m: ScModifierListOwner => m
      case _ => null
    }
    val overrideText = if (needsOverride && (modOwner == null || !modOwner.hasModifierProperty("override"))) "override " else ""
    val modifiersText = if (modOwner != null) modOwner.getModifierList.getText + " " else ""
    val keyword = if (isVal) "val " else "var "
    val name = variable.name
    val colon = this.colon(name)
    val typeText = if (needsInferType)
      substitutor(variable.`type`().getOrAny).canonicalText else ""
    s"$overrideText$modifiersText$keyword$name$colon$typeText${body.map(x => " = " + x).getOrElse("")}"
  }

  def getStandardValue(`type`: ScType): String = {
    val stdTypes = `type`.projectContext.stdTypes
    import stdTypes._

    `type` match {
      case Unit => "()"
      case Boolean => "false"
      case Char | Int | Byte => "0"
      case Long => "0L"
      case Float | Double => "0.0"
      case ScDesignatorType(c: PsiClass) if c.qualifiedName == "java.lang.String" => "\"\""
      case _ => "null"
    }
  }

  def createTypeFromText(text: String, context: PsiElement, child: PsiElement): Option[ScType] = {
    val typeElement = createTypeElementFromText(text, context, child)
    Option(typeElement).map {
      _.`type`().getOrAny // FIXME this should probably be a None instead of Some(Any)
    }
  }

  def createMethodWithContext(text: String, context: PsiElement, child: PsiElement): ScFunction =
    createElementWithContext[ScFunction](text, context, child)(parsingStat.Def.parse(_))

  def createDefinitionWithContext(text: String, context: PsiElement, child: PsiElement): ScMember =
    createElementWithContext[ScMember](text, context, child)(parsingStat.Def.parse(_))

  def createObjectWithContext(text: String, context: PsiElement, child: PsiElement): ScObject =
    createElementWithContext[ScObject](text, context, child)(top.TmplDef.parse)

  def createTypeDefinitionWithContext(text: String, context: PsiElement, child: PsiElement): ScTypeDefinition =
    createElementWithContext[ScTypeDefinition](text, context, child)(top.TmplDef.parse)

  def createReferenceFromText(text: String, context: PsiElement, child: PsiElement): ScStableCodeReference =
    createElementWithContext[ScStableCodeReference](text, context, child) {
      types.StableId.parse(_, parser.ScalaElementType.REFERENCE)
    }

  // TODO method should be eliminated eventually
  def createExpressionWithContextFromText(text: String, context: PsiElement, child: PsiElement): ScExpression = {
    val methodCall = createElementWithContext[ScMethodCall](s"foo($text)", context, child)(expressions.Expr.parse)

    val firstArgument = methodCall.argumentExpressions
      .headOption
      .getOrElse {
        throw elementCreationException("expression", text, context)
      }

    withContext(firstArgument, context, child)
  }

  def createMirrorElement(text: String, context: PsiElement, child: PsiElement): ScExpression = child match {
    case _: ScConstrBlock | _: ScConstrExpr =>
      createElementWithContext[ScExpression](text, context, child)(parsingStat.ConstrExpr.parse)
    case _ =>
      createExpressionWithContextFromText(text, context, child)
  }

  def createElement(text: String)
                   (parse: builder.ScalaPsiBuilder => AnyVal)
                   (implicit ctx: ProjectContext): PsiElement =
    createElement(
      text,
      createScalaFileFromText("")
    )(parse)(ctx.project)

  private def createElementWithContext[E <: ScalaPsiElement](text: String,
                                                             context: PsiElement,
                                                             child: PsiElement)
                                                            (parse: builder.ScalaPsiBuilder => AnyVal)
                                                            (implicit tag: ClassTag[E]): E =
    createElement(text, context, checkLength = true)(parse)(context.getProject) match {
      case element: E => withContext(element, context, child)
      case element => throw elementCreationException(tag.getClass.getSimpleName, text + "; actual: " + element.getText, context)
    }

  def createEmptyModifierList(context: PsiElement): ScModifierList =
    createElementWithContext[ScModifierList]("", context, context.getFirstChild) {
      _.mark().done(parser.ScalaElementType.MODIFIERS)
    }

  private def withContext[E <: ScalaPsiElement](element: E, context: PsiElement, child: PsiElement) = {
    element.setContext(context, child)
    element
  }

  private def createElement[T <: AnyVal](text: String, context: PsiElement,
                                         checkLength: Boolean = false)
                                        (parse: builder.ScalaPsiBuilder => T)
                                        (implicit project: Project): PsiElement = {
    val chameleon = DummyHolderFactory.createHolder(
      PsiManager.getInstance(project),
      context
    ).getTreeElement

    val language = ScalaLanguage.INSTANCE
    val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language)

    val seq = convertLineSeparators(text).trim
    val delegate = PsiBuilderFactory.getInstance.createBuilder(
      project,
      chameleon,
      parserDefinition.createLexer(project),
      language,
      seq
    )

    val psiBuilder = new builder.ScalaPsiBuilderImpl(delegate)
    psiBuilder.mark() match {
      case marker =>
        parse(psiBuilder)
        advanceLexer(psiBuilder)(marker, parserDefinition.getFileNodeType)
    }

    val first = psiBuilder.getTreeBuilt
      .getFirstChildNode
      .asInstanceOf[TreeElement]
    chameleon.rawAddChildren(first)

    first.getPsi match {
      case result if checkLength && result.getTextLength != seq.length =>
        throw new IncorrectOperationException(s"Text length differs; actual: ${result.getText}, expected: $seq")
      case result => result
    }
  }

  @tailrec
  private[this] def advanceLexer(psiBuilder: PsiBuilder)
                                (marker: PsiBuilder.Marker,
                                 fileNodeType: IFileElementType): Unit =
    if (psiBuilder.eof()) {
      marker.done(fileNodeType)
    } else {
      psiBuilder.advanceLexer()
      advanceLexer(psiBuilder)(marker, fileNodeType)
    }

  def createImportFromTextWithContext(text: String, context: PsiElement, child: PsiElement): ScImportStmt =
    createElementWithContext[ScImportStmt](text, context, child)(parsingBase.Import.parse)

  def createTypeElementFromText(text: String)
                               (implicit ctx: ProjectContext): ScTypeElement =
    createScalaFileFromText(s"var f: $text")
      .getLastChild
      .getLastChild match {
      case typeElement: ScTypeElement => typeElement
      case _ => throw elementCreationException("type element", text)
    }

  def createParameterTypeFromText(text: String)(implicit ctx: ProjectContext): ScParameterType =
    createScalaFileFromText(s"(_: $text) => ())")
      .getFirstChild.asInstanceOf[ScFunctionExpr].parameters.head.paramType.get

  def createColon(implicit ctx: ProjectContext): PsiElement =
    createElementFromText("var f: Int", classOf[ScalaPsiElement]).findChildrenByType(tCOLON).head

  def createComma(implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText(",").findChildrenByType(tCOMMA).head

  def createAssign(implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText("val x = 0").findChildrenByType(tASSIGN).head

  def createWhitespace(implicit ctx: ProjectContext): PsiElement =
    createExpressionFromText("1 + 1").findElementAt(1)

  def createWhitespace(whitespace: String)(implicit ctx: ProjectContext): PsiElement =
    createExpressionFromText(s"1$whitespace+ 1").findElementAt(1)

  def createTypeElementFromText(text: String, context: PsiElement, child: PsiElement): ScTypeElement =
    createElementWithContext[ScTypeElement](text, context, child)(types.ParamType.parseInner)

  def createTypeParameterClauseFromTextWithContext(text: String, context: PsiElement,
                                                   child: PsiElement): ScTypeParamClause =
    createElementWithContext[ScTypeParamClause](text, context, child)(params.TypeParamClause.parse)

  def createWildcardPattern(implicit ctx: ProjectContext): ScWildcardPattern = {
    val element = createElementFromText("val _ = x")
    element.getChildren.apply(2).getFirstChild.asInstanceOf[ScWildcardPattern]
  }

  def createTemplateDefinitionFromText(text: String, context: PsiElement, child: PsiElement): ScTemplateDefinition =
    createElementWithContext[ScTemplateDefinition](text, context, child)(top.TmplDef.parse)

  def createDeclarationFromText(text: String, context: PsiElement, child: PsiElement): ScDeclaration =
    createElementWithContext[ScDeclaration](text, context, child)(parsingStat.Dcl.parse(_))

  def createTypeAliasDefinitionFromText(text: String, context: PsiElement, child: PsiElement): ScTypeAliasDefinition =
    createElementWithContext[ScTypeAliasDefinition](text, context, child)(parsingStat.Def.parse(_))

  def createDocCommentFromText(text: String)
                              (implicit ctx: ProjectContext): ScDocComment =
    createDocComment(
      s"""/**
         |$text
         |*/""".stripMargin)

  def createMonospaceSyntaxFromText(text: String)
                                   (implicit ctx: ProjectContext): ScDocSyntaxElement =
    createDocCommentFromText(s"`$text`").getChildren()(2).asInstanceOf[ScDocSyntaxElement]

  def createDocHeaderElement(length: Int)
                            (implicit ctx: ProjectContext): PsiElement =
    createClassWithBody(
      s"""/**=header${StringUtils.repeat("=", length)}*/
          |""".stripMargin).docComment.orNull
      .getNode.getChildren(null)(1).getLastChildNode.getPsi

  def createDocWhiteSpace(implicit ctx: ProjectContext): PsiElement =
    createDocCommentFromText(" *").getNode.getChildren(null)(1).getPsi

  def createLeadingAsterisk(implicit ctx: ProjectContext): PsiElement =
    createDocCommentFromText(" *").getNode.getChildren(null)(2).getPsi

  def createDocSimpleData(text: String)
                         (implicit ctx: ProjectContext): PsiElement =
    createDocComment(s"/**$text*/").getNode.getChildren(null)(1).getPsi

  def createDocTagValue(text: String)
                       (implicit ctx: ProjectContext): PsiElement =
    createClassWithBody(
      s"""/**@param $text
          |*/""".stripMargin).docComment.orNull
      .getNode.getChildren(null)(1).getChildren(null)(2).getPsi

  def createDocTagName(name: String)
                      (implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText("/**@" + name + " qwerty */")
      .typeDefinitions(0).docComment.get.getNode.getChildren(null)(1).getChildren(null)(0).getPsi

  def createDocLinkValue(text: String)
                        (implicit ctx: ProjectContext): ScDocResolvableCodeReference =
    createDocComment(s"/**[[$text]]*/")
      .getNode.getChildren(null)(1).getChildren(null)(1).getPsi.asInstanceOf[ScDocResolvableCodeReference]

  def createXmlEndTag(tagName: String)
                     (implicit ctx: ProjectContext): ScXmlEndTag =
    createScalaFileFromText(s"val a = <$tagName></$tagName>")
      .getFirstChild.getLastChild.getFirstChild.getLastChild.asInstanceOf[ScXmlEndTag]

  def createXmlStartTag(tagName: String, attributes: String = "")
                       (implicit ctx: ProjectContext): ScXmlStartTag =
    createScalaFileFromText(s"val a = <$tagName$attributes></$tagName>")
      .getFirstChild.getLastChild.getFirstChild.getFirstChild.asInstanceOf[ScXmlStartTag]

  def createInterpolatedStringPrefix(prefix: String)
                                    (implicit ctx: ProjectContext): PsiElement =
    createScalaFileFromText(prefix + "\"blah\"").getFirstChild.getFirstChild

  def createEquivMethodCall(infix: ScInfixExpr): ScMethodCall = {
    val ScInfixExpr.withAssoc(base, ElementText(operationText), argument) = infix

    val clauseText = argument match {
      case _: ScTuple | _: ScParenthesisedExpr | _: ScUnitExpr => argument.getText
      case ElementText(text) => text.parenthesize()
    }

    val typeArgText = infix.typeArgs.map(_.getText).getOrElse("")
    val exprText = s"(${base.getText}).$operationText$typeArgText$clauseText"

    val exprA = createExpressionWithContextFromText(base.getText, infix, base)

    val methodCall = createExpressionWithContextFromText(exprText.toString, infix.getContext, infix)
    val referenceExpression = methodCall match {
      case ScMethodCall(reference: ScReferenceExpression, _) => reference
      case ScMethodCall(ScGenericCall(reference, _), _) => reference
    }

    referenceExpression.qualifier.foreach {
      _.replaceExpression(exprA, removeParenthesis = true)
    }
    methodCall.asInstanceOf[ScMethodCall]
  }

  def createEquivQualifiedReference(postfix: ScPostfixExpr): ScReferenceExpression = {
    val operand = postfix.operand
    val operandText = operand.getText
    val qualRefText = s"($operandText).${postfix.operation.getText}"
    val expr = createExpressionWithContextFromText(qualRefText, postfix.getContext, postfix).asInstanceOf[ScReferenceExpression]
    val qualWithoutPars = createExpressionWithContextFromText(operandText, postfix, operand)
    expr.qualifier.foreach(_.replaceExpression(qualWithoutPars, removeParenthesis = true))
    expr
  }

  private[this] def createClassWithBody(body: String)
                                       (implicit context: ProjectContext): ScTypeDefinition = {
    val fileText =
      s"""class a {
         |  $body
         |}""".stripMargin
    createScalaFileFromText(fileText).typeDefinitions.head
  }

  private[this] def createMemberFromText(text: String)
                                        (implicit context: ProjectContext): ScMember =
    createClassWithBody(text).members.head

  def createDocComment(prefix: String)
                      (implicit context: ProjectContext): ScDocComment =
    createScalaFileFromText(s"$prefix class a").typeDefinitions.head
      .docComment.orNull

  private[this] def elementCreationException(kind: String, text: String,
                                             context: PsiElement = null,
                                             cause: Throwable = null) = {
    val contextSuffix = context match {
      case null => ""
      case _ => "; with context: " + context.getText
    }
    new IncorrectOperationException(s"Cannot create $kind from text: $text$contextSuffix", cause)
  }
}
