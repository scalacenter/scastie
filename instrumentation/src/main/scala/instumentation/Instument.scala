package instrumentation

import scala.meta._
import inputs.Position
import scala.collection.immutable.Seq

object Instrument {
  private def instrument(source: Source): Source = {

    val instrumentationMethod = Term.Name("instrumentations$")
    val instrumentationMap    = Term.Name("instrumentationMap$")

    def instrumentTemplate(templ: Template): Template = {
      val pat = Pat.Var.Term(instrumentationMap)
      val instrumentationApi =
        Seq(
          q"private val $pat = scala.collection.mutable.Map.empty[api.Position, api.Render]",
          q"""
          def $instrumentationMethod = {
            $instrumentationMap.toList.map{ 
              case (pos, r) ⇒ api.Instrumentation(pos, r)
            }
          }
          """
        )

      val result =
        templ.stats.map(stats ⇒ stats.map(s ⇒ instrumentStat(s))) match {
          case Some(instrumented) ⇒ Some(instrumentationApi ++ instrumented)
          case None               ⇒ Some(instrumentationApi)
        }

      templ.copy(stats = result)
    }

    def instrumentStat(stat: Stat): Stat = {
      stat match {
        case term: Term   ⇒ instrumentTerm(term)
        case vl: Defn.Val ⇒ vl.copy(rhs = instrumentTerm(vl.rhs, vl.decltpe))
        case vr: Defn.Var ⇒
          vr.copy(rhs = vr.rhs.map(rhs ⇒ instrumentTerm(rhs, vr.decltpe)))
        case e ⇒ e
      }
    }

    def instrumentTerm(term: Term, tpeTree: Option[Type] = None): Term = {
      def posToApi(position: Position) = {
        def tuple2(v1: Int, v2: Int) = Seq(Lit(v1), Lit(v2))

        val lits =
          position match {
            case Position.None ⇒ tuple2(0, 0)
            case Position.Range(input, start, end) ⇒
              tuple2(start.offset, end.offset)
          }

        Term.Apply(Term.Name("api.Position"), lits)
      }

      val treeQuote =
        tpeTree match {
          case None      ⇒ q"val t = $term"
          case Some(tpe) ⇒ q"val t: $tpe = $term"
        }

      q"""
      locally {
        $treeQuote
        $instrumentationMap(${posToApi(term.pos)}) = scastie.runtime.Runtime.render(t)
        t
      }
      """
    }

    val instrumentedCode =
      source.stats.map {
        case c: Defn.Class if c.name.value == "Worksheet$" ⇒
          c.copy(templ = instrumentTemplate(c.templ))
        case tree ⇒ tree
      }

    val main =
      q"""
      object Main {
        val worksheet = new Worksheet$$
        def main(args: Array[String]): Unit = {
          println(scastie.runtime.Runtime.write(worksheet.${instrumentationMethod}))
        }
      }
      """

    source.copy(stats = main +: instrumentedCode)
  }

  def apply(code: String): String = {
    instrument(code.parse[Source].get).toString
  }
}
