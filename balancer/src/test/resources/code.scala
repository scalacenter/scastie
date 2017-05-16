import cats.free.Free
import cats.instances.future._
import cats.~>
import freek._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.higherKinds

object Main extends App {

  object MainProgram {
    sealed trait Foo1[A]
    final case class Bar11(s: Int) extends Foo1[Int]

    sealed trait Foo2[A]
    final case class Bar21(s: String) extends Foo2[String]

    type PRG = Foo1 :|: Foo2 :|: NilDSL
    val PRG = DSL.Make[PRG]

    val program = for {
      _ <- Bar11(5).freek[PRG]
      x <- Bar21("1.234").freek[PRG]
    } yield x
  }

  object SubProgram {

    sealed trait Foo3[A]
    final case class Bar31(s: String) extends Foo3[Float]

    sealed trait Foo4[A]
    final case class Bar41(s: Float) extends Foo4[String]

    type PRG = Foo3 :|: Foo4 :|: NilDSL
    val PRG = DSL.Make[PRG]

    // this is our transpiler transforming a Foo2 into another free program
    val transpiler = new (MainProgram.Foo2 ~> Free[PRG.Cop, ?]) {

      def apply[A](f: MainProgram.Foo2[A]): Free[PRG.Cop, A] = f match {
        case MainProgram.Bar21(s) =>
          for {
            f <- Bar31(s).freek[PRG]
            s <- Bar41(f).freek[PRG]
          } yield (s)
      }
    }
  }

  import SubProgram._
  import MainProgram._

  // 1/ CopKNat[MainProgram.PRG.Cop] creates a MainProgram.PRG.Cop ~> MainProgram.PRG.Cop
  // 2/ .replace creates a natural trans that replaces MainProgram.Foo2 in MainProgram.PRG.Cop by Free[SubProgram.PRG.Cop, ?] using transpiler
  // 3/ The result is a terrible natural transformation (don't try to write that type, it's too ugly, let's scalac do it) :
  //    (Foo1 :|: Foo2 :|: NilDSL) ~> (Foo1 :|: Free[SubProgram.PRG.Cop, ?] :|: NilDSL)
  val transpileNat = CopKNat[MainProgram.PRG.Cop].replace(SubProgram.transpiler)

  // Transpile does 2 operations:
  // 1/ Replaces Foo2 in MainProgram.PRG.Cop by Free[SubProgram.PRG.Cop, A]
  //    -> SubProgram.transpiler natural transformation converts Foo2 into the free program Free[SubProgram.PRG.Cop, A]
  //    -> New PRG.Cop is then Foo1 :|: Free[SubProgram.PRG.Cop, ?] :|: NilDSL
  //
  // 2/ Flattens Free[(Foo1 :|: Free[(Foo3 :|: Foo4 :|: NilDSL)#Cop, ?] :|: NilDSL)#Cop, A] into
  //    Free[(Foo1 :|: Foo3 :|: Foo4 :|: NilDSL)#Cop, A]
  val free = MainProgram.program.transpile(transpileNat)
  // Same as
  // val free2 = MainProgram.f.compile(transpileNat).flatten

  // Write our interpreters for new program (Foo1, Foo3, Foo4)
  val foo1Future = new (Foo1 ~> Future) {
    def apply[A](a: Foo1[A]): Future[A] = a match {
      case Bar11(i) =>
        Future {
          i
        }
    }
  }

  val foo3Future = new (Foo3 ~> Future) {
    def apply[A](a: Foo3[A]): Future[A] = a match {
      case Bar31(s) =>
        Future {
          s.toFloat + 10f
        }
    }
  }

  val foo4Future = new (Foo4 ~> Future) {
    def apply[A](a: Foo4[A]): Future[A] = a match {
      case Bar41(s) =>
        Future {
          s.toString + "xxx"
        }
    }
  }

  val finalinterpreter = foo1Future :&: foo3Future :&: foo4Future
  val finalProgramm = free.interpret(finalinterpreter)
  val r = Await.result(finalProgramm, 2.seconds)
  println("r:" + r)

}