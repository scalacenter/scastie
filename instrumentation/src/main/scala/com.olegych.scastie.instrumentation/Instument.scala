package com.olegych.scastie.instrumentation

import com.olegych.scastie.api.ScalaTarget._
import com.olegych.scastie.api.{ScalaTarget, ScalaTargetType}

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
  def getLineOffset(isWorksheetMode: Boolean): Int = {
    if (isWorksheetMode) -2
    else 0
  }

  import InstrumentationFailure._

  private val instrumentedClass = "Playground"
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
    def tuple2(v1: Int, v2: Int) = Seq(Lit.Int(v1), Lit.Int(v2))

    val lits =
      position match {
        case Position.None => tuple2(0, 0)
        case Position.Range(_, start, end) =>
          tuple2(start.offset - offset, end.offset - offset)
      }

    Term.Apply(Term.Name(positionT), lits)
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
        case c: Defn.Class
            if c.name.value == instrumentedClass &&
              c.templ.stats.nonEmpty =>
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
            c.templ.stats.get.collect {
            case term: Term => instrumentOne(term, None, offset, isScalaJs)
          }
      }.flatten

    val instrumentedCode = Patch(source.tokens, instrumentedCodePatches)

    val entryPoint =
      if (!isScalaJs) {
        s"""|object Main {
            |  val playground = new $instrumentedClass
            |  def main(args: Array[String]): Unit = {
            |    scala.Predef.println($runtimeT.write(playground.$instrumentationMethod))
            |  }
            |}
            |""".stripMargin
      } else {
        s"""|@$jsExportTopLevelT("ScastiePlaygroundMain") class ScastiePlaygroundMain {
            |  val playground = $runtimeErrorT.wrap(new $instrumentedClass)
            |  @$jsExportT def result = $runtimeT.write(playground.right.map(_.$instrumentationMethod))
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
      templ.stats match {
        case Some(ss) =>
          ss.exists {
            case q"def main(args: Array[String]): $_ = $_" => true
            case _                                         => false
          }
        case _ => false
      }
    }
    val apps = Set("App", "IOApp")
    def hasApp(templ: Template): Boolean =
      templ.parents.exists(p => apps(p.syntax))

    source.stats.exists {
      case c: Defn.Class if c.name.value == instrumentedClass && c.templ.stats.nonEmpty =>
        c.templ.stats.get.exists {
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
      if (!isScalaJs) s"class $instrumentedClass {"
      else s"class $instrumentedClass extends $domhookT {"

    val prelude =
      s"""|$runtimeImport
          |$classBegin""".stripMargin

    val code0 =
      s"""|$prelude
          |$code 
          |}""".stripMargin

    // TODO:
    // dialects.Paradise211
    // dialects.Paradise212
    // dialects.ParadiseTypelevel211
    // dialects.ParadiseTypelevel212

    def typelevel(scalaVersion: String): Option[Dialect] = {
      if (scalaVersion.startsWith("2.12")) Some(dialects.Typelevel212)
      else if (scalaVersion.startsWith("2.11")) Some(dialects.Typelevel211)
      else None
    }

    def scala(scalaVersion: String): Option[Dialect] = {
      if (scalaVersion.startsWith("2.13")) Some(dialects.Scala212)
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
          case NonFatal(e) => {
            Left(InternalError(e))
          }
        }
      case None => Left(UnsupportedDialect)
    }
  }
}
