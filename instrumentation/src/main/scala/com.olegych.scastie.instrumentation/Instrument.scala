package com.olegych.scastie.instrumentation

import com.olegych.scastie.api.ScalaTarget._
import com.olegych.scastie.api.{Inputs, ScalaTarget, ScalaTargetType, Instrumentation}

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

object Instrument {
  def getParsingLineOffset(inputs: Inputs): Int = {
    if (inputs.isWorksheetMode) -1 else 0
  }
  def getExceptionLineOffset(inputs: Inputs): Int = {
    if (inputs.isWorksheetMode) -2
    else
      inputs.target match {
        case _: Dotty => 0
        case _        => 0
      }
  }
  def getMessageLineOffset(inputs: Inputs): Int = {
    if (inputs.isWorksheetMode) -2
    else
      inputs.target match {
        case _: Dotty => 1
        case _        => 0
      }
  }

  import InstrumentationFailure._

  private val instrumentedObject = Instrumentation.instrumentedObject
  private val instrumentationMethod = "instrumentations$"
  private val instrumentationMap = "instrumentationMap$"

  private val emptyMapT = "_root_.scala.collection.mutable.Map.empty"
  private val jsExportT = "_root_.scala.scalajs.js.annotation.JSExport"
  private val jsExportTopLevelT =
    "_root_.scala.scalajs.js.annotation.JSExportTopLevel"

  private val apiPackage = "_root_.com.olegych.scastie.api"
  private val positionT = s"$apiPackage.Position"
  private val renderT = s"$apiPackage.Render"
  private val runtimeErrorT = s"$apiPackage.RuntimeError"
  private val instrumentationT = s"$apiPackage.Instrumentation"
  private val runtimeT = s"$apiPackage.runtime.Runtime"
  private val domhookT = s"$apiPackage.runtime.DomHook"

  private val elemArrayT =
    "_root_.scala.scalajs.js.Array[_root_.org.scalajs.dom.raw.HTMLElement]"

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

    val renderCall =
      if (!isScalaJs) s"$runtimeT.render($$t);"
      else s"$runtimeT.render($$t, attach _);"

    val replacement =
      Seq(
        "scala.Predef.locally {",
        treeQuote + "; ",
        s"$instrumentationMap(${posToApi(term.pos, offset)}) = $renderCall",
        "$t}"
      ).mkString("")

    Patch(term.tokens.head, term.tokens.last, replacement)
  }

  private def instrument(source: Source, offset: Int, isScalaJs: Boolean): String = {
    val instrumentedCodePatches =
      source.stats.collect {
        case c: Defn.Object if c.name.value == instrumentedObject =>
          val openCurlyBrace =
            if (!isScalaJs) c.templ.tokens.head
            else c.templ.tokens.find(_.toString == "{").get

          val instrumentationMapCode = Seq(
            s"{ private val $instrumentationMap = $emptyMapT[$positionT, $renderT]",
            s"def $instrumentationMethod = $instrumentationMap.toList.map{ case (pos, r) => $instrumentationT(pos, r) }"
          ).mkString("", ";", ";")

          val instrumentationMapPatch =
            Patch(openCurlyBrace, openCurlyBrace, instrumentationMapCode)

          instrumentationMapPatch +:
            c.templ.stats.collect {
            case term: Term => instrumentOne(term, None, offset, isScalaJs)
          }
      }.flatten

    val instrumentedCode = Patch(source.tokens, instrumentedCodePatches)

    val entryPoint =
      if (!isScalaJs) {
        s"""|object Main {
            |  def suppressUnusedWarnsScastie = Html
            |  val playground = $instrumentedObject
            |  def main(args: Array[String]): Unit = {
            |    scala.Predef.println("\\n" + $runtimeT.write(playground.$instrumentationMethod))
            |  }
            |}
            |""".stripMargin
      } else {
        s"""|@$jsExportTopLevelT("ScastiePlaygroundMain") class ScastiePlaygroundMain {
            |  def suppressUnusedWarnsScastie = Html
            |  val playground = $runtimeErrorT.wrap($instrumentedObject)
            |  @$jsExportT def result = $runtimeT.write(playground.map(_.$instrumentationMethod))
            |  @$jsExportT def attachedElements: $elemArrayT =
            |    playground match {
            |      case Right(p) => p.attachedElements
            |      case Left(_) => $elemArrayT()
            |    }
            |}""".stripMargin
      }

    s"""|$instrumentedCode
        |$entryPoint""".stripMargin
  }

  private def hasMainMethod(source: Source): Boolean = {
    def hasMain(templ: Template): Boolean = {
      templ.stats.exists {
        case q"def main(args: Array[String]): $_ = $_" => true
        case _                                         => false
      }
    }
    val apps = Set("App", "IOApp")
    def hasApp(templ: Template): Boolean =
      templ.inits.exists(p => apps(p.syntax))

    source.stats.exists {
      case c: Defn.Object if c.name.value == instrumentedObject =>
        c.templ.stats.exists {
          case c: Defn.Class  => hasMain(c.templ) || hasApp(c.templ)
          case t: Defn.Trait  => hasMain(t.templ) || hasApp(t.templ)
          case o: Defn.Object => hasMain(o.templ) || hasApp(o.templ)
          case _              => false
        }
      case _ => false
    }
  }

  def apply(
      code: String,
      target: ScalaTarget = Jvm.default
  ): Either[InstrumentationFailure, String] = {
    val runtimeImport = "import _root_.com.olegych.scastie.api.runtime._"

    val isScalaJs = target.targetType == ScalaTargetType.JS

    val classBegin =
      if (!isScalaJs) s"object $instrumentedObject {"
      else s"object $instrumentedObject extends $domhookT {"

    val prelude =
      s"""|$runtimeImport
          |$classBegin""".stripMargin

    val code0 =
      s"""|$prelude
          |$code 
          |}""".stripMargin

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
      else None
    }

    val maybeDialect =
      target match {
        case Jvm(scalaVersion)       => scala(scalaVersion)
        case Js(scalaVersion, _)     => scala(scalaVersion)
        case Native(scalaVersion, _) => scala(scalaVersion)
        case Dotty(_)                => Some(dialects.Dotty)
        case Typelevel(scalaVersion) => typelevel(scalaVersion)
        case _                       => None
      }

    maybeDialect match {
      case Some(dialect) =>
        implicit val selectedDialect: Dialect = dialect

        try {
          code0.parse[Source] match {
            case Parsed.Success(source) =>
              if (!hasMainMethod(source))
                Right(instrument(source, prelude.length + 1, isScalaJs))
              else Left(HasMainMethod)
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
