package com.olegych.scastie.ensime

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
    List(
      () => autocompleteEnd("List(1).")(_.nonEmpty),
      () =>
        autocompleteEndFail("import org.scalajs.dom.", ScalaTarget.Js.default),
      () => blockUntilReady(),
      () =>
        autocompleteEnd("import org.scalajs.dom.", ScalaTarget.Js.default)(
          _.nonEmpty
      ),
      () => autocompleteEndFail("List(1)."),
      () => blockUntilReady(),
      () => autocompleteEnd("List(1).")(_.nonEmpty)
    ).zipWithIndex.foreach {
      case (step, i) =>
        println(s"--- $i ---")
        println(step())
        println(s"----------")
    }
  }

  // test("invalid config") {

  //   val bogusSbtConfig = "er"

  //   autocompleteBase(
  //     Inputs.default.copy(
  //       sbtConfigExtra = bogusSbtConfig,
  //       code = "1"
  //     ),
  //     offset = 0
  //   )(
  //     fish = _ => true,
  //     shouldFail = true
  //   )

  //   autocompleteEnd("List(1).", )(_.nonEmpty)
  // }

  test("typeAt 1") {
    if (false) {
      // https://github.com/scalacenter/scastie/issues/311
      // SymbolInfo(<empty>,<empty>,None,BasicTypeInfo(<empty>,Object,<empty>,List(),List(),None,List()))

      typeAt(
        code = "val foobar = List(1)",
        //            ^
        offset = 7
      )(_ == "List[Int]")
    }

    pending
  }

  test("typeAt 2") {
    if (false) {
      // https://github.com/scalacenter/scastie/issues/311
      // SymbolInfo(<empty>,<empty>,None,BasicTypeInfo(<empty>,Object,<empty>,List(),List(),None,List()))
      typeAt(
        code = "val foobar = 42",
        //            ^
        offset = 7
      )(_ == "List[Int]")
    }

    pending
  }

  private def autocomplete(inputs: Inputs, offset: Int)(
      fish: List[Completion] => Boolean
  ): Unit = {
    autocompleteBase(inputs, offset)(fish, shouldFail = false)
  }

  private def autocompleteBase(inputs: Inputs, offset: Int)(
      fish: List[Completion] => Boolean,
      shouldFail: Boolean = false
  ): Unit = {

    val taskId = EnsimeTaskId.create

    ensimeActor.tell(
      EnsimeTaskRequest(
        AutoCompletionRequest(EnsimeRequestInfo(inputs, offset)),
        taskId
      ),
      probe.ref
    )

    probe.fishForMessage(20.seconds) {
      case EnsimeTaskResponse(Some(AutoCompletionResponse(completions)),
                              taskId0) => {
        if (!shouldFail) {
          assert(taskId0 == taskId)
          assert(fish(completions))
          true
        } else {
          false
        }
      }

      case other => {
        if (!shouldFail) {
          println("--- other ---")
          println(inputs)
          println("-------------")
          println(other)
          println("-------------")
        }
        shouldFail
      }
    }
  }

  private def autocomplete(code: String,
                           offset: Int,
                           target: ScalaTarget = ScalaTarget.Jvm.default)(
      fish: List[Completion] => Boolean,
      shouldFail: Boolean = false
  ): Unit = {
    autocompleteBase(
      inputs = Inputs.default.copy(code = code, target = target),
      offset = offset
    )(fish, shouldFail)
  }

  private def autocompleteEnd(code: String,
                              target: ScalaTarget = ScalaTarget.Jvm.default)(
      fish: List[Completion] => Boolean,
      shouldFail: Boolean = false
  ): Unit = {
    autocompleteBase(
      inputs = Inputs.default.copy(code = code, target = target),
      offset = code.length
    )(fish, shouldFail)
  }

  private def autocompleteEndFail(
      code: String,
      target: ScalaTarget = ScalaTarget.Jvm.default
  ): Unit = {

    autocompleteEnd(code, target)(
      fish = _ => false,
      shouldFail = true
    )
  }

  private def typeAt(code: String, offset: Int)(fish: String => Boolean): Unit = {
    val taskId = EnsimeTaskId.create

    val inputs = Inputs.default.copy(code = code)

    ensimeActor.tell(
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

  private val timeout = 1.minute
  private val ensimeActor = TestActorRef(
    new EnsimeRunner(
      system = system,
      dispatchActor = readyProbe.ref,
      sbtReloadTimeout = timeout
    )
  )

  def blockUntilReady(): Unit = {
    readyProbe.fishForMessage(3.minute) {
      case EnsimeServerState.Ready => {
        println("===============")
        println("==EnsimeReady==")
        println("===============")
        true
      }
      case other => {
        println(other)
        false
      }
    }
  }

  blockUntilReady()

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
