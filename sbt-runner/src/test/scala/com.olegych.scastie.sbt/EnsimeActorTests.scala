package com.olegych.scastie.sbt

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import com.olegych.scastie.api._
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.duration._

class EnsimeActorTests()
    extends TestKit(ActorSystem("EnsimeActorTests"))
    with ImplicitSender
    with FunSuiteLike
    with BeforeAndAfterAll {

  test("autocomplete") {
    autocompleteEnd("List(1).ma")(
      autocompletions =>
        autocompletions.exists(
          completion =>
            completion.hint == "max" &&
              completion.signature == "(Ordering[B]) => Int" &&
              completion.resultType == "Int"
      )
    )
  }

  test("autocomplete failure") {
    autocomplete("L", 100)(_.isEmpty)
  }

  test("autocompletion should fail when the code does not compile") {
    // https://github.com/ensime/ensime-server/issues/1850
    pending
  }

  test("autocomplete after restart") {
    autocompleteEnd("List(1).")(_.nonEmpty)
    autocompleteEnd("List(1).", ScalaTarget.Js.default)(_.nonEmpty)
    // ^ returns None
    autocompleteEnd("List(1).")(_.nonEmpty)
  }

  test("typeAt 1") {
    // https://github.com/scalacenter/scastie/issues/311

    // SymbolInfo(<empty>,<empty>,None,BasicTypeInfo(<empty>,Object,<empty>,List(),List(),None,List()))
    typeAt(
      code = "val foobar = List(1)",
      //            ^
      offset = 7
    )(_ == "List[Int]")

    pending
  }

  test("typeAt 2") {
    // https://github.com/scalacenter/scastie/issues/311

    // SymbolInfo(<empty>,<empty>,None,BasicTypeInfo(<empty>,Object,<empty>,List(),List(),None,List()))
    typeAt(
      code = "val foobar = 42",
      //            ^
      offset = 7
    )(_ == "List[Int]")

    pending
  }

  private def autocomplete(inputs: Inputs, offset: Int)(
      fish: List[Completion] => Boolean
  ): Unit = {

    val taskId = EnsimeTaskId.create
    sbtActor.tell(
      EnsimeTaskRequest(
        AutoCompletionRequest(EnsimeRequestInfo(inputs, offset)),
        taskId
      ),
      probe.ref
    )

    probe.fishForMessage(30.seconds) {
      case EnsimeTaskResponse(Some(AutoCompletionResponse(completions)),
                              taskId0) =>
        assert(taskId0 == taskId)
        assert(fish(completions))
        true
    }
  }

  private def autocomplete(code: String,
                           offset: Int,
                           target: ScalaTarget = ScalaTarget.Jvm.default)(
      fish: List[Completion] => Boolean
  ): Unit = {
    autocomplete(
      inputs = Inputs.default.copy(code = code, target = target),
      offset = offset
    )(fish)
  }

  private def autocompleteEnd(code: String,
                              target: ScalaTarget = ScalaTarget.Jvm.default)(
      fish: List[Completion] => Boolean
  ): Unit = {
    autocomplete(
      inputs = Inputs.default.copy(code = code, target = target),
      offset = code.length
    )(fish)
  }

  private def typeAt(code: String, offset: Int)(fish: String => Boolean): Unit = {
    val taskId = EnsimeTaskId.create

    val inputs = Inputs.default.copy(code = code)

    sbtActor.tell(
      EnsimeTaskRequest(
        TypeAtPointRequest(EnsimeRequestInfo(inputs, offset)),
        taskId
      ),
      probe.ref
    )

    probe.fishForMessage(30.seconds) {
      case EnsimeTaskResponse(Some(TypeAtPointResponse(symbol)), taskId0) => {
        println()
        println("===")
        println(symbol)
        println("===")
        println()

        assert(taskId0 == taskId)
        assert(fish(symbol))
        true
      }
      case e => {
        println()
        println("===")
        println(e)
        println("===")
        println()

        false
      }
    }
  }

  private val probe = TestProbe()
  private val readyProbe = TestProbe()

  private val sbtActor = TestActorRef(
    new SbtActor(
      system = system,
      runTimeout = 20.seconds,
      production = false,
      withEnsime = true,
      readyRef = Some(readyProbe.ref),
      reconnectInfo = None
    )
  )

  readyProbe.fishForMessage(3.minute) {
    case EnsimeReady => {
      println("===============")
      println("==EnsimeReady==")
      println("===============")
      true
    }
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}
