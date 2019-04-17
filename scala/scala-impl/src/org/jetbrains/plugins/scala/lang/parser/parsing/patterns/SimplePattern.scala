package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package patterns

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Literal
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import org.jetbrains.plugins.scala.lang.parser.parsing.xml.pattern.XmlPattern
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 29.02.2008
*/

/*
 * SimplePattern ::= '_'
 *                 | varid
 *                 | Literal
 *                 | StableId
 *                 | StableId '(' [Patterns [',']] ')'
 *                 | StableId '(' [Patterns ','] [(varid | '_' ) '@'] '_' '*'')'
 *                 |'(' [Patterns [',']] ')'
 *                 | XmlPattern
 */
object SimplePattern {

  def parse(builder: ScalaPsiBuilder): Boolean = {
    val simplePatternMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tUNDER =>
        builder.advanceLexer() //Ate _
        builder.getTokenText match {
          case "*" =>
            simplePatternMarker.rollbackTo()
            return false
          case _ =>
        }
        simplePatternMarker.done(ScalaElementType.WILDCARD_PATTERN)
        return true
      case ScalaTokenTypes.tLPARENTHESIS =>
        builder.advanceLexer() //Ate (
        builder.disableNewlines()
        builder.getTokenType match {
          case ScalaTokenTypes.tRPARENTHESIS =>
            builder.advanceLexer() //Ate )
            builder.restoreNewlinesState()
            simplePatternMarker.done(ScalaElementType.TUPLE_PATTERN)
            return true
          case _ =>
        }
        if (Patterns.parse(builder)) {
          builder.getTokenType match {
            case ScalaTokenTypes.tRPARENTHESIS =>
              builder.advanceLexer() //Ate )
              builder.restoreNewlinesState()
              simplePatternMarker.done(ScalaElementType.TUPLE_PATTERN)
              return true
            case _ =>
              builder error ScalaBundle.message("rparenthesis.expected")
              builder.restoreNewlinesState()
              simplePatternMarker.done(ScalaElementType.TUPLE_PATTERN)
              return true
          }
        }
        if (Pattern parse builder) {
          builder.getTokenType match {
            case ScalaTokenTypes.tRPARENTHESIS =>
              builder.advanceLexer() //Ate )
            case _ =>
              builder error ScalaBundle.message("rparenthesis.expected")
          }
          builder.restoreNewlinesState()
          simplePatternMarker.done(ScalaElementType.PATTERN_IN_PARENTHESIS)
          return true
        }
      case _ =>
    }
    if (InterpolationPattern parse builder) {
      simplePatternMarker.done(ScalaElementType.INTERPOLATION_PATTERN)
      return true
    }

    if (Literal parse builder) {
      simplePatternMarker.done(ScalaElementType.LITERAL_PATTERN)
      return true
    }

    if (XmlPattern.parse(builder)) {
      simplePatternMarker.drop()
      return true
    }
    if (builder.lookAhead(ScalaTokenTypes.tIDENTIFIER) &&
            !builder.lookAhead(ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tDOT) &&
            !builder.lookAhead(ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tLPARENTHESIS) &&
      builder.invalidVarId) {
      val rpm = builder.mark
      builder.getTokenText
      builder.advanceLexer()
      rpm.done(ScalaElementType.REFERENCE_PATTERN)
      simplePatternMarker.drop()
      return true
    }

    val rb1 = builder.mark
    if (StableId parse(builder, ScalaElementType.REFERENCE_EXPRESSION)) {
      builder.getTokenType match {
        case ScalaTokenTypes.tLPARENTHESIS =>
          rb1.rollbackTo()
          StableId parse(builder, ScalaElementType.REFERENCE)
          val args = builder.mark
          builder.advanceLexer() //Ate (
          builder.disableNewlines()

          def parseSeqWildcard(withComma: Boolean): Boolean = {
            if (if (withComma)
              builder.lookAhead(ScalaTokenTypes.tCOMMA, ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)
            else builder.lookAhead(ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)) {
              val wild = builder.mark
              if (withComma) builder.advanceLexer()
              builder.getTokenType
              builder.advanceLexer()
              if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && "*".equals(builder.getTokenText)) {
                builder.advanceLexer()
                wild.done(ScalaElementType.SEQ_WILDCARD)
                true
              } else {
                wild.rollbackTo()
                false
              }
            } else {
              false
            }
          }

          def parseSeqWildcardBinding(withComma: Boolean): Boolean = {
            if (if (withComma) builder.lookAhead(ScalaTokenTypes.tCOMMA, ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tAT,
            ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER) || builder.lookAhead(ScalaTokenTypes.tCOMMA, ScalaTokenTypes.tUNDER, ScalaTokenTypes.tAT,
              ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)
            else builder.lookAhead(ScalaTokenTypes.tIDENTIFIER, ScalaTokenTypes.tAT,
            ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER) || builder.lookAhead(ScalaTokenTypes.tUNDER, ScalaTokenTypes.tAT,
              ScalaTokenTypes.tUNDER, ScalaTokenTypes.tIDENTIFIER)) {
              ParserUtils.parseVarIdWithWildcardBinding(builder, withComma)
            } else false
          }

          if (!parseSeqWildcard(withComma = false) && !parseSeqWildcardBinding(withComma = false) && Pattern.parse(builder)) {
            while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
              builder.advanceLexer() // eat comma
              if (!parseSeqWildcard(withComma = false) && !parseSeqWildcardBinding(withComma = false)) Pattern.parse(builder)
            }
          }
          builder.getTokenType match {
            case ScalaTokenTypes.tRPARENTHESIS =>
              builder.advanceLexer() //Ate )
            case _ =>
              builder error ErrMsg("rparenthesis.expected")
          }
          builder.restoreNewlinesState()
          args.done(ScalaElementType.PATTERN_ARGS)
          simplePatternMarker.done(ScalaElementType.CONSTRUCTOR_PATTERN)
          return true
        case _ =>
          rb1.drop()
          simplePatternMarker.done(ScalaElementType.STABLE_REFERENCE_PATTERN)
          return true
      }
    }
    simplePatternMarker.rollbackTo()
    false
  }
}