package com.olegych.scastie
package instrumentation

import api.ScalaTarget
import api.ScalaTarget._

import scala.meta._
import parsers.Parsed
import inputs.Position

import scala.collection.immutable.Seq

sealed trait InstrumentationFailure
case object HasMainMethod extends InstrumentationFailure
case object UnsupportedDialect extends InstrumentationFailure
case class ParsingError(error: Parsed.Error) extends InstrumentationFailure

object Instrument {
  private val instrumnedClass = "Playground"
  private val instrumentationMethod = "instrumentations$"
  private val instrumentationMap = "instrumentationMap$"

  private def posToApi(position: Position, offset: Int) = {
    def tuple2(v1: Int, v2: Int) = Seq(Lit(v1), Lit(v2))

    val lits =
      position match {
        case Position.None => tuple2(0, 0)
        case Position.Range(input, start, end) =>
          tuple2(start.offset - offset, end.offset - offset)
      }

    Term.Apply(Term.Name("_root_.com.olegych.scastie.api.Position"), lits)
  }

  def instrumentOne(term: Term, tpeTree: Option[Type], offset: Int): Patch = {

    val treeQuote =
      tpeTree match {
        case None => s"val t = $term"
        case Some(tpe) => s"val t: $tpe = $term"
      }

    val replacement =
      Seq(
        "locally {",
        treeQuote + "; ",
        s"$instrumentationMap(${posToApi(term.pos, offset)}) = _root_.com.olegych.scastie.api.runtime.Runtime.render(t);",
        "t}"
      ).mkString("")

    Patch(term.tokens.head, term.tokens.last, replacement)
  }

  private def instrument(source: Source, offset: Int): String = {
    val instrumentedCodePatches =
      source.stats.collect {
        case c: Defn.Class
            if c.name.value == instrumnedClass &&
              c.templ.stats.nonEmpty => {

          val openCurlyBrace = c.templ.tokens.head

          val instrumentationMapCode = Seq(
            s"{ private val $instrumentationMap = _root_.scala.collection.mutable.Map.empty[_root_.com.olegych.scastie.api.Position, _root_.com.olegych.scastie.api.Render]",
            s"def $instrumentationMethod = ${instrumentationMap}.toList.map{ case (pos, r) => _root_.com.olegych.scastie.api.Instrumentation(pos, r) }"
          ).mkString("", ";", ";")

          val instrumentationMapPatch =
            Patch(openCurlyBrace, openCurlyBrace, instrumentationMapCode)

          instrumentationMapPatch +:
            c.templ.stats.get.collect {
              case term: Term => instrumentOne(term, None, offset)
              case vl: Defn.Val => instrumentOne(vl.rhs, vl.decltpe, offset)
              case vr: Defn.Var if vr.rhs.nonEmpty =>
                instrumentOne(vr.rhs.get, vr.decltpe, offset)
            }
        }
      }.flatten

    val instrumentedCode = Patch(source.tokens, instrumentedCodePatches)

    s"""|$instrumentedCode
        |object Main {
        |  val playground = new $instrumnedClass
        |  def main(args: Array[String]): Unit = {
        |    println(_root_.com.olegych.scastie.api.runtime.Runtime.write(playground.${instrumentationMethod}))
        |  }
        |}
        |""".stripMargin
  }

  private def hasMainMethod(source: Source): Boolean = {
    def hasMain(templ: Template): Boolean = {
      templ.stats match {
        case Some(ss) => {
          ss.exists {
            case q"def main(args: $_[$_]): $_ = $_" => true
            case _ => false
          }
        }
        case _ => false
      }
    }

    def hasApp(templ: Template): Boolean =
      templ.parents.exists(_.syntax == "App")

    source.stats.exists {
      case c: Defn.Class
          if c.name.value == instrumnedClass &&
            c.templ.stats.nonEmpty => {

        c.templ.stats.get.exists {
          case c: Defn.Class => hasMain(c.templ) || hasApp(c.templ)
          case t: Defn.Trait => hasMain(t.templ) || hasApp(t.templ)
          case o: Defn.Object => hasMain(o.templ) || hasApp(o.templ)
          case _ => false
        }
      }
      case _ => false
    }
  }

  def apply(code: String, target: ScalaTarget = Jvm.default): Either[InstrumentationFailure, String] = {
    val prelude =
      s"""|import _root_.com.olegych.scastie.api.runtime._
          |class $instrumnedClass {""".stripMargin

    val code0 =
      s"""|$prelude
          |$code
          |}""".stripMargin

    // TODO:
    // dialects.Paradise211
    // dialects.Paradise212
    // dialects.ParadiseTypelevel211
    // dialects.ParadiseTypelevel212

    // scalameta 1.6.0:
    // Typelevel211
    // Typelevel212

    def typelevel(scalaVersion: String): Option[Dialect] = {
      if(scalaVersion.startsWith("2.12")) Some(dialects.Scala212)
      else if(scalaVersion.startsWith("2.11")) Some(dialects.Scala211)
      else None
    }

    def scala(scalaVersion: String): Option[Dialect] = {
      if(scalaVersion.startsWith("2.12")) Some(dialects.Scala212)
      else if(scalaVersion.startsWith("2.11")) Some(dialects.Scala211)
      else if(scalaVersion.startsWith("2.10")) Some(dialects.Scala210)
      else None
    }

    val maybeDialect = 
      target match {
        case Jvm(scalaVersion) => scala(scalaVersion)
        case Js(scalaVersion, _) => scala(scalaVersion)
        case Native => scala("2.11")
        case Dotty => Some(dialects.Dotty)
        case Typelevel(scalaVersion) => typelevel(scalaVersion)
        case _ => None
      }

    maybeDialect match {
      case Some(dialect) => {
        implicit val selectedDialect = dialect

        code0.parse[Source] match {
          case Parsed.Success(k) =>
            if (!hasMainMethod(k))
              Right(instrument(k, offset = prelude.length + 1))
            else Left(HasMainMethod)
          case e: Parsed.Error => Left(ParsingError(e))
        }
      }
      case None => Left(UnsupportedDialect)
    }
  }
}
