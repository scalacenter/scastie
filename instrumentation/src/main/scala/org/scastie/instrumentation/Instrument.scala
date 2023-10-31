package org.scastie.instrumentation

import org.scastie.api._
import org.scastie.runtime.api._

import scala.collection.immutable.Seq
import scala.meta._
import scala.meta.inputs.Position
import scala.meta.parsers.Parsed
import scala.util.control.NonFatal
import org.scastie.buildinfo.BuildInfo
import org.scastie.api.ScalaTargetType.Scala2
import org.scastie.api.ScalaTargetType.JS

sealed trait InstrumentationFailure

object InstrumentationFailure {
  case object HasMainMethod extends InstrumentationFailure
  case object UnsupportedDialect extends InstrumentationFailure
  case class ParsingError(error: Parsed.Error) extends InstrumentationFailure
  case class InternalError(exception: Throwable) extends InstrumentationFailure
}

object Instrument {
  def getParsingLineOffset(isWorksheet: Boolean): Int = if (isWorksheet) -1 else 0
  def getExceptionLineOffset(isWorksheet: Boolean): Int = if (isWorksheet) -2 else 0
  def getMessageLineOffset(isWorksheet: Boolean, isScalaCli: Boolean): Int = (isWorksheet, isScalaCli) match {
      case (true, _) => -2
      case (false, true) => 1
      case (false, false) => 0
  }

  import InstrumentationFailure._

  private val instrumentedObject = Instrumentation.instrumentedObject
  private val instrumentationMethod = "instrumentations$"
  private val instrumentationMap = "instrumentationMap$"

  private val emptyMapT = "_root_.scala.collection.mutable.Map.empty"
  private val jsExportT = "_root_.scala.scalajs.js.annotation.JSExport"
  private val jsExportTopLevelT =
    "_root_.scala.scalajs.js.annotation.JSExportTopLevel"

  private val runtimePackage= "_root_.org.scastie.runtime"
  private val runtimeApiPackage = "_root_.org.scastie.runtime.api"
  private val positionT = s"$runtimeApiPackage.Position"
  private val renderT = s"$runtimeApiPackage.Render"
  private val runtimeErrorT = s"$runtimePackage.RuntimeError"
  private val instrumentationT = s"$runtimeApiPackage.Instrumentation"
  private val runtimeT = s"$runtimePackage.Runtime"
  private val domhookT = s"$runtimePackage.DomHook"

  val entryPointName = "Main"

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
          val openCurlyBrace = c.templ.tokens.find(_.toString == "{").get

          val instrumentationMapCode = Seq(
            s"{ private val $instrumentationMap = $emptyMapT[$positionT, $renderT]",
            s"def $instrumentationMethod = $instrumentationMap.toList.map{ case (pos, r) => $instrumentationT(pos, r) }"
          ).mkString("", ";", ";")

          val instrumentationMapPatch =
            Patch(openCurlyBrace, openCurlyBrace, instrumentationMapCode)

          instrumentationMapPatch +:
            c.templ.stats
            .filter {
              case _: Term.EndMarker => false
              case _                 => true
            }
            .collect {
              case term: Term => instrumentOne(term, None, offset, isScalaJs)
            }
      }.flatten

    val instrumentedCode = Patch(source.tokens, instrumentedCodePatches)

    val entryPoint =
      if (!isScalaJs) {
        s"""|object $entryPointName {
            |  def suppressUnusedWarnsScastie = Html
            |  val playground = $instrumentedObject
            |  def main(args: Array[String]): Unit = {
            |    playground.main(Array())
            |    scala.Predef.println("\\n" + $runtimeT.write(playground.$instrumentationMethod))
            |  }
            |}
            |""".stripMargin
      } else {
        s"""|@$jsExportTopLevelT("ScastiePlaygroundMain") class ScastiePlaygroundMain {
            |  def suppressUnusedWarnsScastie = Html
            |  val playground = $runtimeErrorT.wrap($instrumentedObject)
            |  @$jsExportT def result = $runtimeT.write(playground.map{ playground => playground.main(Array()); playground.$instrumentationMethod })
            |  @$jsExportT def attachedElements: $elemArrayT =
            |    playground match {
            |      case Right(p) => p.attachedElements
            |      case Left(_) => $elemArrayT()
            |    }
            |}""".stripMargin
      }

    s"""$instrumentedCode\n$entryPoint"""
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

  def separateDirectives(code: String, targetType: ScalaTargetType, additionalDirectives: Seq[String]): (String, String) = {

    if (targetType == ScalaTargetType.ScalaCli) {
      val (usingDirectives, remainingLines) = code.linesWithSeparators.span {
        case line if line.startsWith("//>") => true
        case _ => false
      }
      val allDirectives = usingDirectives ++ additionalDirectives
      (allDirectives.mkString, remainingLines.mkString)
    } else {
      ("", code)
    }
  }

  def apply(code: String, target: ScalaTarget): Either[InstrumentationFailure, String] = {
    val runtimeImport = target match {
      case Scala3(scalaVersion) => "import _root_.org.scastie.runtime.*"
      case _ => "import _root_.org.scastie.runtime._"
    }

    val isScalaJs = target.targetType == ScalaTargetType.JS

    lazy val scalaCliRuntime = target.runtimeDependency.renderScalaCli.appended('\n')
    val (usingDirectives, remainingCode) = separateDirectives(code, target.targetType, Seq(scalaCliRuntime))

    val classBegin =
      if (!isScalaJs) s"object $instrumentedObject extends ScastieApp {"
      else s"object $instrumentedObject extends ScastieApp with $domhookT {"
    val prelude = s"""$runtimeImport\n$classBegin"""
    val code0 = s"""$usingDirectives$prelude\n$remainingCode\n}"""

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

    val offset = target match {
      case _: ScalaCli => scalaCliRuntime.length + prelude.length + 1
      case _ => prelude.length + 1
    }

    maybeDialect match {
      case Some(dialect) =>
        implicit val selectedDialect: Dialect = dialect

        try {
          code0.parse[Source] match {
            case parsed: Parsed.Success[_] =>
              if (!hasMainMethod(parsed.get)) {
                Right(instrument(parsed.get, offset, isScalaJs))
              } else Left(HasMainMethod)
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
