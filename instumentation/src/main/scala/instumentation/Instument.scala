package instrumentation

import scala.meta._
import inputs.Position
import scala.collection.immutable.Seq

object Instrument {
  private def instrument(source: Source): Source = {

    val instrumentationMethod = Term.Name("instrumentation$")

    val instrumentedCode =
      source.stats.map{
        case c: Defn.Class if c.name.value == "Worksheet$" => 
          c.copy(templ = instrument(c.templ, instrumentationMethod))
        case tree => tree
      }

    if(instrumentedCode != source.stats) {
      val main = 
        q"""
        object Main {
          val worksheet = new Worksheet$$
          def main(args: Array[String]): Unit = {
            println(worksheet.${instrumentationMethod})
          }
        }
        """

      source.copy(stats = main +: instrumentedCode)
    }
    else source // not instrumented 
  }

  private def instrument(templ: Template, instrumentationMethod: Term.Name): Template = {
    val instrumentationMap = Term.Name("instrumentation$")
    val pat = Pat.Var.Term(instrumentationMap)
    val instr =
      Seq(
        q"private val $pat = scala.collection.mutable.Map.empty[(Int, Int), (String, String)]",
        q"def $instrumentationMethod = instrumentation$$.toList"
      )

    templ.copy(stats = 
      templ.stats.map(stats => stats.map(s => instrument(s, instrumentationMap))) match {
        case Some(stats) => Some(instr ++ stats)
        case None => Some(instr)
      }
    )
  }

  private def instrument(stat: Stat, instrumentationMap: Term.Name): Stat = {
    stat match {
      case Defn.Val(mods, pats, decltpe, rhs) => 
        Defn.Val(mods, pats, decltpe, instrument(rhs, instrumentationMap))
      case e => e
    }
  }

  private def instrument(term: Term, instrumentationMap: Term.Name): Term = {
    def posToTuple(position: Position): Term.Tuple = {
      def tuple2(v1: Int, v2: Int) = Term.Tuple(Seq(Lit(v1), Lit(v2)))

      position match {
        case Position.None => tuple2(0, 0)
        case Position.Range(input, start, end) => tuple2(start.offset, end.offset)
      }
    }

    q"""
    {
      val t = $term
      $instrumentationMap(${posToTuple(term.pos)}) = render(t)
      t
    }
    """
  }
  
  def apply(code: String): String = {
    instrument(code.parse[Source].get).toString
  }
}

/*

YES:
* println()
* f(a, b..) = $rhs
* if(c) a else b
* for (gen) yield v
* a = b
* implicitly[Ordering[Int]]
* a: Int
* val/var a
* f(1)
* p.x
* p
* {a; b}
* try ...
* 1.0

NO:
* v match { case x => }
* while(c) { expr }
* do { expr } while(c) 
* for (c) expr
* type A = List
* class A / trait A
* object A
* def f = 1

*/