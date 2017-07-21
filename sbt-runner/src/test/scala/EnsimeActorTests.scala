package com.olegych.scastie

package sbt

import api._

import akka.actor.ActorSystem
import akka.testkit.{TestKit, ImplicitSender, TestProbe, TestActorRef}
import org.scalatest.{FunSuiteLike, BeforeAndAfterAll}

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
        CompletionRequest(EnsimeRequestInfo(inputs, 1)),
        taskId
      ),
      probe.ref
    )

    probe.fishForMessage(30.seconds) {
      case EnsimeTaskResponse(Some(CompletionResponse(completions)), taskId0) => {
        assert(taskId0 == taskId)
        assert(
          completions.exists(completion =>
            completion.hint == "List" && 
            completion.typeInfo == "List"
          )
        )
        true
      }
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
      readyRef = Some(readyProbe.ref)
    )
  )

  readyProbe.fishForMessage(1.minute) {
    case EnsimeReady => true
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}