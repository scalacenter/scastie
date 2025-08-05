package com.olegych.scastie.instrumentation

import scala.annotation.tailrec
import scala.collection.Seq
import scala.jdk.CollectionConverters._
import scala.meta._
import scala.meta.tokens.Tokens.tokensToInput

import difflib._
import difflib.myers.Equalizer

/** Helper to map between position between two similar strings. */
final class TokenEditDistance private (matching: Array[MatchingToken]) {
  private def isEmpty: Boolean = matching.length == 0

  def originalInput: Input =
    if (isEmpty) Input.None
    else matching(0).original.input

  def revisedInput: Input =
    if (isEmpty) Input.None
    else matching(0).revised.input

  def toOffset(input: Input, line: Int, column: Int): Position = {
    Position.Range(input, line, column, line, column)
  }

  def contains(pos: Position, offset: Int): Boolean = {
    if (pos.start == pos.end) pos.end == offset
    else {
      pos.start <= offset &&
      pos.end > offset
    }
  }

  def toUnslicedPosition(pos: Position): Position = pos.input match {
    case Input.Slice(underlying, a, _) => toUnslicedPosition(Position.Range(underlying, a + pos.start, a + pos.end))
    case _                             => pos
  }

  def toOriginalLine(revisedLine: Int): Int = {
    if (isEmpty) {
      return revisedLine
    }

    val charOffset = lineToCharOffset(revisedLine, revisedInput)

    toOriginal(charOffset) match {
      case Right(pos) =>
        val originalLine = charOffsetToLine(pos.start, originalInput)
        originalLine
      case Left(_) => revisedLine
    }
  }

  private def lineToCharOffset(line: Int, input: Input): Int = {
    val lines = input.text.split('\n')

    if (line <= 0) {
      0
    } else if (line > lines.length) {
      input.text.length
    } else {
      val offset = lines.take(line - 1).map(_.length + 1).sum
      val lineText = if (line > 0 && line <= lines.length) lines(line - 1) else ""
      val valTPattern = "^val \\$t = ".r
      val prefixTLength = valTPattern.findFirstIn(lineText).map(_.length).getOrElse(0)
      if (prefixTLength > 0) {
        offset + prefixTLength
      } else {
        offset
      }
      
    }
  }

  private def charOffsetToLine(charOffset: Int, input: Input): Int = {
    val beforeOffset = input.text.take(charOffset)
    beforeOffset.count(_ == '\n') + 1
  }

  /** Convert from offset in revised string to offset in original string */
  def toOriginal(revisedOffset: Int): Either[EmptyResult, Position] = {
    if (isEmpty) EmptyResult.unchanged
    else {
      val exactMatch = matching.find { mt =>
        val pos = toUnslicedPosition(mt.revised.pos)
        contains(pos, revisedOffset)
      }

      exactMatch match {
        case Some(m) => Right(m.original.pos)

        case None =>
          val nearestToken = matching.minBy { mt =>
            val pos = toUnslicedPosition(mt.revised.pos)
            val distance =
              if (revisedOffset < pos.start) pos.start - revisedOffset
              else if (revisedOffset > pos.end) revisedOffset - pos.end
              else 0
            distance
          }

          val nearestPos = toUnslicedPosition(nearestToken.revised.pos)
          Right(nearestToken.original.pos)
      }
    }
  }
}

object TokenEditDistance {

  /**
    * Build utility to map offsets/lines between original and instrumented inputs.
    *
    * @param original
    *   Original user code.
    * @param revised
    *   Instrumented user code.
    */
  def apply(
    original: IndexedSeq[Token],
    revised: IndexedSeq[Token]
  ): TokenEditDistance = {
    val filteredOriginal = original.filterNot(t => t.is[Token.BOF] || t.is[Token.EOF])
    val filteredRevised = revised.filterNot(t => t.is[Token.BOF] || t.is[Token.EOF])
    val buffer = Array.newBuilder[MatchingToken]
    buffer.sizeHint(math.max(filteredOriginal.length, filteredRevised.length))
    @tailrec
    def loop(i: Int, j: Int, ds: List[Delta[Token]]): Unit = {
      val isDone: Boolean = i >= filteredOriginal.length || j >= filteredRevised.length
      if (isDone) ()
      else {
        val o = filteredOriginal(i)
        val r = filteredRevised(j)
        if (TokenEqualizer.equals(o, r)) {
          buffer += MatchingToken(o, r)
          loop(i + 1, j + 1, ds)
        } else {
          ds match {
            case Nil => loop(i + 1, j + 1, ds)
            case delta :: tail => loop(
                i + delta.getOriginal.size(),
                j + delta.getRevised.size(),
                tail
              )
          }
        }
      }
    }
    val deltas = {
      difflib.DiffUtils
        .diff(filteredOriginal.asJava, filteredRevised.asJava, TokenEqualizer)
        .getDeltas
        .iterator()
        .asScala
        .toList
    }
    loop(0, 0, deltas)
    new TokenEditDistance(buffer.result())
  }

  def apply(
    originalInput: Input,
    revisedInput: Input
  ): Option[TokenEditDistance] = {
    for {
      revised <- revisedInput.tokenize.toOption
      original <- {
        if (originalInput == revisedInput) Some(revised)
        else originalInput.tokenize.toOption
      }
    } yield apply(original, revised)
  }

  /** Compare tokens only by their text and token category. */
  private object TokenEqualizer extends Equalizer[Token] {
    override def equals(original: Token, revised: Token): Boolean = original.productPrefix == revised.productPrefix &&
      original.pos.text == revised.pos.text
  }

}
