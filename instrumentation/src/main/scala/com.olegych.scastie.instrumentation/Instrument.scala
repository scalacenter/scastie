package com.olegych.scastie.instrumentation

import com.olegych.scastie.api.ScalaTarget._
import com.olegych.scastie.api.{Inputs, Instrumentation, ScalaTarget, ScalaTargetType}
import com.olegych.scastie.api.InstrumentationRecorder

import scala.collection.immutable.Seq
import scala.meta._
import scala.meta.inputs.Position
import scala.meta.parsers.Parsed
import scala.util.control.NonFatal

sealed trait InstrumentationFailure

object InstrumentationFailure {
  case object HasMainMethod extends InstrumentationFailure
  case object UnsupportedDialect extends InstrumentationFailure
  case class ParsingError(error: Parsed.Error) extends InstrumentationFailure
  case class InternalError(exception: Throwable) extends InstrumentationFailure
}

case class InstrumentationSuccess(
    instrumentedCode: String,
    lineMapper: LineMapper
)

object Instrument {
  def getParsingLineOffset(inputs: Inputs): Int = {
    if (inputs.isWorksheetMode) -1 else 0
  }
  def getExceptionLineOffset(inputs: Inputs): Int = {
    if (inputs.isWorksheetMode) -2 else 0
  }
  def getMessageLineOffset(inputs: Inputs): Int = {
    if (inputs.isWorksheetMode) -2 else 0
  }

  import InstrumentationFailure._

  private val instrumentedObject = Instrumentation.instrumentedObject
  private val instrumentationMethod = "instrumentations$"

  private val emptyMapT = "_root_.scala.collection.mutable.Map.empty"
  private val jsExportT = "_root_.scala.scalajs.js.annotation.JSExport"
  private val jsExportTopLevelT =
    "_root_.scala.scalajs.js.annotation.JSExportTopLevel"

  private val apiPackage = "_root_.com.olegych.scastie.api"
  private val positionT = s"$apiPackage.Position"
  private val renderT = s"$apiPackage.Render"
  private val runtimeErrorT = s"$apiPackage.RuntimeError"
  private val runtimeT = s"$apiPackage.runtime.Runtime"
  private val domhookT = s"$apiPackage.runtime.DomHook"
  private val instrumentationRecorderT = s"$apiPackage.InstrumentationRecorder"

  private val elemArrayT =
    "_root_.scala.scalajs.js.Array[_root_.org.scalajs.dom.raw.HTMLElement]"

  private def extractExperimentalImports(code: String): (String, String) = {
    val experimentalRegex = """^\s*import\s+language\.experimental\.[^\n]+""".r

    val experimentalImports = experimentalRegex.findAllIn(code).toList
    val codeWithoutExpImports = experimentalRegex.replaceAllIn(code, m => "/*" + " " * (m.matched.length - 4) + "*/")

    val experimental = experimentalImports.mkString("\n") + (if (experimentalImports.nonEmpty) "\n" else "")
    
    (experimental, codeWithoutExpImports)
  }

  private def posToApi(position: Position, offset: Int) = {
    val (x, y) = position match {
      case Position.None => (0, 0)
      case Position.Range(_, start, end) =>
        (start - offset, end - offset)
    }
    s"$positionT($x, $y)"
  }

  def instrumentOne(term: Term, tpeTree: Option[Type], offset: Int, isScalaJs: Boolean): Patch = {

    val treeQuote =
      tpeTree match {
        case None      => s"val $$t = $term"
        case Some(tpe) => s"val $$t: $tpe = $term"
      }

    val startPos = term.pos.start - offset
    val endPos   = term.pos.end - offset

    val renderCall =
      if (!isScalaJs) s"$runtimeT.render($$t)"
      else s"$runtimeT.render($$t, attach _)"

    val replacement =
      s"""|scala.Predef.locally {
          |  $$doc.startStatement($startPos, $endPos);
          |  $treeQuote;
          |  $$doc.binder($renderCall, $startPos, $endPos);
          |  $$doc.endStatement();
          |  $$t}""".stripMargin

    Patch(term.tokens.head, term.tokens.last, replacement)
  }

  private def instrument(source: Source, offset: Int, isScalaJs: Boolean): (String, String) = {
    val instrumentedCodePatches = source.stats.collect {
      case c: Defn.Object if c.name.value == instrumentedObject =>
        c.templ.body.stats
          .filter {
            case _: Term.EndMarker => false
            case _                 => true
          }
          .collect { case term: Term => instrumentOne(term, None, offset, isScalaJs) }
    }.flatten

    val instrumentedCode = Patch(source.tokens, instrumentedCodePatches)

    val entryPoint =
      if (!isScalaJs) {
        s"""|object Main {
            |  def suppressUnusedWarnsScastie = Html
            |  val playground = $instrumentedObject
            |  def main(args: Array[String]): Unit = {
            |    playground.main(Array())
            |    scala.Predef.println("\\n" + $runtimeT.writeStatements(playground.$$doc.getResults()))
            |  }
            |}
            |""".stripMargin
      } else {
        s"""|@$jsExportTopLevelT("ScastiePlaygroundMain") class ScastiePlaygroundMain {
            |  def suppressUnusedWarnsScastie = Html
            |  val playground = $runtimeErrorT.wrap($instrumentedObject)
            |  @$jsExportT def result = playground match {
            |    case Right(p) => 
            |      p.main(Array())
            |      $runtimeT.writeStatements(p.$$doc.getResults())
            |    case Left(error) => $runtimeT.writeStatements(List())
            |  }
            |  @$jsExportT def attachedElements: $elemArrayT =
            |    playground match {
            |      case Right(p) => p.attachedElements
            |      case Left(_) => $elemArrayT()
            |    }
            |}""".stripMargin
      }
    val cleanedCode = removeExcessiveNewlines(instrumentedCode)
    (cleanedCode, entryPoint)
  }

  private def hasMainMethod(source: Source): Boolean = {
    def hasMain(templ: Template): Boolean = {
      templ.body.stats.exists {
        case q"def main(args: Array[String]): $_ = $_" => true
        case _                                         => false
      }
    }
    val apps = Set("App", "IOApp")
    def hasApp(templ: Template): Boolean =
      templ.inits.exists(p => apps(p.syntax))

    source.stats.exists {
      case c: Defn.Object if c.name.value == instrumentedObject =>
        c.templ.body.stats.exists {
          case c: Defn.Class  => hasMain(c.templ) || hasApp(c.templ)
          case t: Defn.Trait  => hasMain(t.templ) || hasApp(t.templ)
          case o: Defn.Object => hasMain(o.templ) || hasApp(o.templ)
          case _              => false
        }
      case _ => false
    }
  }

  private def removeExcessiveNewlines(code: String): String = {
    code
      .split('\n')
      .foldLeft(List.empty[String]) { case (acc, line) =>
        acc match {
          case Nil => List(line)
          case prev :: _ =>
            if (line.trim.isEmpty && prev.trim == "$t}") {
              acc
            } else if (line.trim.isEmpty && prev.trim.isEmpty) {
              acc
            } else {
              line :: acc
            }
        }
      }
      .reverse
      .mkString("\n")
  }

  def apply(code: String, target: ScalaTarget): Either[InstrumentationFailure, InstrumentationSuccess] = {
    val runtimeImport = target match {
      case Scala3(scalaVersion) => "import _root_.com.olegych.scastie.api.runtime.*"
      case _ => "import _root_.com.olegych.scastie.api.runtime._"
    }

    val isScalaJs = target.targetType == ScalaTargetType.JS

    val classBegin =
      if (!isScalaJs) s"object $instrumentedObject extends ScastieApp with $instrumentationRecorderT {"
      else s"object $instrumentedObject extends ScastieApp with $instrumentationRecorderT with $domhookT {"
    val (experimentalImports, codeWithoutExpImports) = extractExperimentalImports(code)
    val prelude = s"""$runtimeImport\n$experimentalImports$classBegin"""
    val code0 = s"""$prelude\n$codeWithoutExpImports\n}"""

    def typelevel(scalaVersion: String): Option[Dialect] = {
      if (scalaVersion.startsWith("2.12")) Some(dialects.Typelevel212)
      else if (scalaVersion.startsWith("2.11")) Some(dialects.Typelevel211)
      else None
    }

    def scala(scalaVersion: String): Option[Dialect] = {
      if (scalaVersion.startsWith("2.13")) Some(dialects.Scala213)
      else if (scalaVersion.startsWith("2.12")) Some(dialects.Scala212)
      else if (scalaVersion.startsWith("2.11")) Some(dialects.Scala211)
      else if (scalaVersion.startsWith("2.10")) Some(dialects.Scala210)
      else if (scalaVersion.startsWith("3")) Some(dialects.Scala3)
      else None
    }

    val maybeDialect = target match {
      case Typelevel(scalaVersion) => typelevel(scalaVersion)
      case target                  => scala(target.scalaVersion)
    }

    maybeDialect match {
      case Some(dialect) =>
        implicit val selectedDialect: Dialect = dialect

        try {
          code0.parse[Source] match {
            case parsed: Parsed.Success[_] =>
              if (!hasMainMethod(parsed.get)) {
                val originalCode = parsed.get.text
                val (instrumentedCode, entryPoint) = instrument(parsed.get, prelude.length + 1, isScalaJs)

                val lineMapper = LineMapper(originalCode, instrumentedCode)

                Right(InstrumentationSuccess(
                  s"""$instrumentedCode\n$entryPoint""",
                  lineMapper
                  ))
              } else {
                Left(HasMainMethod)
              }
            case e: Parsed.Error => Left(ParsingError(e))
          }
        } catch {
          case NonFatal(e) =>
            Left(InternalError(e))
        }
      case None => Left(UnsupportedDialect)
    }
  }
}
