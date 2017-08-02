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
    val taskId = EnsimeTaskId.create

    val inputs = Inputs.default.copy(code = "L")

    sbtActor.tell(
      EnsimeTaskRequest(
        AutoCompletionRequest(EnsimeRequestInfo(inputs, 1)),
        taskId
      ),
      probe.ref
    )

    probe.fishForMessage(30.seconds) {
      case EnsimeTaskResponse(Some(AutoCompletionResponse(completions)),
                              taskId0) =>
        assert(taskId0 == taskId)
        assert(
          completions.exists(
            completion =>
              completion.hint == "List" &&
                completion.typeInfo == "List"
          )
        )
        true
    }
  }

  // test("typeAt") {
  //   val taskId = EnsimeTaskId.create

  //   val code = "List(1)"
  //   val inputs = Inputs.default.copy(code = code)

  //   sbtActor.tell(
  //     EnsimeTaskRequest(
  //       TypeAtPointRequest(EnsimeRequestInfo(inputs, code.length - 1)),
  //       taskId
  //     ),
  //     probe.ref
  //   )

  //   probe.fishForMessage(30.seconds) {
  //     case EnsimeTaskResponse(Some(TypeAtPointResponse(symbol)), taskId0) => {
  //       println()
  //       println("===")
  //       println(symbol)
  //       println("===")
  //       println()

  //       assert(taskId0 == taskId)
  //       assert(symbol == "List[Int]")
  //       true
  //     }
  //     case e => {
  //       println()
  //       println("===")
  //       println(e)
  //       println("===")
  //       println()

  //       false
  //     }
  //   }
  // }

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
    case EnsimeReady => true
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}
