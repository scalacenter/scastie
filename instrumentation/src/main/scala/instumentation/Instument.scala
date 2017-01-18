package instrumentation

import scala.meta._
import inputs.Position
import scala.collection.immutable.Seq

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

    Term.Apply(Term.Name("api.Position"), lits)
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
        s"$instrumentationMap(${posToApi(term.pos, offset)}) = scastie.runtime.Runtime.render(t);",
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
            s"{ private val $instrumentationMap = scala.collection.mutable.Map.empty[api.Position, api.Render]",
            s"def $instrumentationMethod = ${instrumentationMap}.toList.map{ case (pos, r) => api.Instrumentation(pos, r) }"
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
        |    println(scastie.runtime.Runtime.write(playground.${instrumentationMethod}))
        |  }
        |}
        |""".stripMargin
  }

  def apply(code: String): String = {
    val prelude =
      s"""|import api.runtime._
          |class $instrumnedClass {""".stripMargin

    val code0 =
      s"""|$prelude
          |$code
          |}""".stripMargin

    code0.parse[Source] match {
      case parsers.Parsed.Success(k) =>
        instrument(k, offset = prelude.length + 1)
      case _ =>
        code0
    }
  }
}
