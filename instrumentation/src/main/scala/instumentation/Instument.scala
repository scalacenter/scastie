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
          case None        ⇒ Some(instrumentationApi)
        }

      templ.copy(stats = result)
    }

    def instrumentStat(stat: Stat): Stat = {
      stat match {

        case q"if ($cond) $texpr else $fexpr" ⇒
          q"if ($cond) { ${instrumentTerm(texpr)} } else { ${instrumentTerm(fexpr)} }"

        case apply: Term.Apply       ⇒ instrumentTerm(apply) // f(1)
        case forYield: Term.ForYield ⇒ instrumentTerm(forYield)
        // case _: Select ⇒ instrumentTerm (stat) // p.x
        // case _: Ident ⇒ instrumentTerm  (stat) // p
        // case b: Block ⇒ instrumentTerm  (stat) // {a; b}
        // case _: Try ⇒ instrumentTerm  (stat) // try ...

        case vl: Defn.Val ⇒ vl.copy(rhs = instrumentTerm(vl.rhs, vl.decltpe))
        case vr: Defn.Var ⇒
          vr.copy(rhs = vr.rhs.map(rhs ⇒ instrumentTerm(rhs, vr.decltpe)))

        case sel: Term.Select ⇒ instrumentTerm(sel) // a.foo

        // case Defn.Var(mods, pats, decltpe, rhs) ⇒ Defn.Val(mods, pats, decltpe, instrumentTerm(rhs))
        // class Name(value: Predef.String @nonEmpty)
        // class Interpolate(prefix: Name, parts: Seq[Lit] @nonEmpty, args: Seq[Term])
        // class Xml(parts: Seq[Lit] @nonEmpty, args: Seq[Term])

        // class Apply(fun: Term, args: Seq[Arg])
        // class ApplyType(fun: Term, targs: Seq[Type] @nonEmpty)
        // class ApplyInfix(lhs: Term, op: Name, targs: Seq[Type], args: Seq[Arg])
        case ap: Term.ApplyInfix ⇒ instrumentTerm(ap) // implicitly[V]
        // class ApplyUnary(op: Name, arg: Term)

        case as: Term.Assign ⇒ as.copy(rhs = instrumentTerm(as.rhs)) // a = 1
        // class Assign(lhs: Term.Ref, rhs: Term)
        // class Update(fun: Term, argss: Seq[Seq[Arg]] @nonEmpty, rhs: Term)

        // class Ascribe(expr: Term, tpe: Type)
        // class Annotate(expr: Term, annots: Seq[Mod.Annot] @nonEmpty)
        // class Tuple(args: Seq[Term] @nonEmpty)
        // class Block(stats: Seq[Stat])
        // class If(cond: Term, thenp: Term, elsep: Term)
        // class Match(expr: Term, cases: Seq[Case] @nonEmpty)
        // class TryWithCases(expr: Term, catchp: Seq[Case], finallyp: Option[Term])
        // class TryWithTerm(expr: Term, catchp: Term, finallyp: Option[Term])
        // class Function(params: Seq[Term.Param], body: Term)
        // class PartialFunction(cases: Seq[Case] @nonEmpty)
        // class ForYield(enums: Seq[Enumerator] @nonEmpty, body: Term)
        // class New(templ: Template) extends Term

        case e ⇒
          // println(e)
          e
      }
    }

    def instrumentTerm(term: Term, tpeTree: Option[Type] = None, local: Boolean = false): Term = {
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

      val block =  
        q"""
        {
          $treeQuote
          $instrumentationMap(${posToApi(term.pos)}) = scastie.runtime.Runtime.render(t)
          t
        }
        """

      if(local) {
        q"""
        locally {
          $block
        }
        """
      } else block
    }

    val instrumentedCode =
      source.stats.map {
        case c: Defn.Class if c.name.value == "Worksheet$" ⇒
          c.copy(templ = instrumentTemplate(c.templ))
        case tree ⇒ tree
      }

    if (instrumentedCode != source.stats) {
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
    } else source // not instrumented
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
 * v match { case x ⇒ }
 * while(c) { expr }
 * do { expr } while(c)
 * for (c) expr
 * type A = List
 * class A / trait A
 * object A
 * def f = 1

 */
