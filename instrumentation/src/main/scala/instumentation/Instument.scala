package instrumentation

import scala.meta._
import inputs.Position
import scala.collection.immutable.Seq

object Instrument {
  private val instrumentationMethod = "instrumentations$"
  private val instrumentationMap    = "instrumentationMap$"

  private def posToApi(position: Position) = {
    def tuple2(v1: Int, v2: Int) = Seq(Lit(v1), Lit(v2))

    val lits =
      position match {
        case Position.None ⇒ tuple2(0, 0)
        case Position.Range(input, start, end) ⇒
          tuple2(start.offset, end.offset)
      }

    Term.Apply(Term.Name("api.Position"), lits)
  }

  def instrumentOne(term: Term, tpeTree: Option[Type] = None): Patch = {

    val treeQuote =
      tpeTree match {
        case None      ⇒ s"val t = $term"
        case Some(tpe) ⇒ s"val t: $tpe = $term"
      }

    val replacement =
      Seq(
        "locally {",
        treeQuote + ";",
        s"$instrumentationMap(${posToApi(term.pos)}) = scastie.runtime.Runtime.render(t);",
        "t}"
      ).mkString("")

    Patch(term.tokens.head, term.tokens.last, replacement)
  }

  private def instrument(source: Source): String = {
    
    val instrumentedCodePatches =
      source.stats.collect {
        case c: Defn.Class if 
          c.name.value == "Worksheet$" && 
          c.templ.stats.nonEmpty ⇒ {

          val openCurlyBrace = c.templ.tokens.head

          val instrumentationMapCode = Seq(
            s"{ private val $instrumentationMap = scala.collection.mutable.Map.empty[api.Position, api.Render]",
            s"def $instrumentationMethod = ${instrumentationMap}.toList.map{ case (pos, r) => api.Instrumentation(pos, r) }"
          ).mkString(";")

          val instrumentationMapPatch = Patch(openCurlyBrace, openCurlyBrace, instrumentationMapCode)

          instrumentationMapPatch +:
          c.templ.stats.get.collect {
            case term: Term   ⇒ instrumentOne(term)
            case vl: Defn.Val ⇒ instrumentOne(vl.rhs, vl.decltpe)
            case vr: Defn.Var if vr.rhs.nonEmpty ⇒ instrumentOne(vr.rhs.get, vr.decltpe)
          }
        }
      }.flatten

    val instrumentedCode = Patch(source.tokens, instrumentedCodePatches)

    s"""|$instrumentedCode
        |object Main {
        |  val worksheet = new Worksheet$$
        |  def main(args: Array[String]): Unit = {
        |    println(scastie.runtime.Runtime.write(worksheet.${instrumentationMethod}))
        |  }
        |}
        |""".stripMargin
  }

  def apply(code: String): String = {
    instrument(code.parse[Source].get)
  }
}
