package com.olegych.scastie.sbt

import com.olegych.scastie.api._
import com.olegych.scastie.proto._

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}

import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import scala.concurrent.duration._

class EnsimeActorTests()
    extends TestKit(ActorSystem("EnsimeActorTests"))
    with ImplicitSender
    with FunSuiteLike
    with BeforeAndAfterAll {

  test("autocomplete") {
    val taskId = EnsimeTaskId.create

    val inputs = InputsHelper.default.copy(code = "L")

    sbtActor.tell(
      EnsimeTaskRequest(
        taskId = taskId,
        request = CompletionRequest(
          EnsimeRequest.Info(
            inputs = inputs,
            offset = 1
          )
        )
      ),
      probe.ref
    )

    probe.fishForMessage(30.seconds) {
      case EnsimeTaskResponse(taskId0, Some(CompletionResponse(completions))) =>
        assert(taskId0 == taskId)
        assert(
          completions.exists(
            completion =>
              completion.hint == "List" &&
                completion.symbol == "List"
          )
        )
        true
    }
  }

  test("typeAt") {
    def tobefixed(): Unit = {

      val taskId = EnsimeTaskId.create

      val code = "List(1)"
      val inputs = InputsHelper.default.copy(code = code)

      sbtActor.tell(
        EnsimeTaskRequest(
          taskId = taskId,
          request = TypeAtPointRequest(
            EnsimeRequest.Info(
              inputs = inputs,
              offset = code.length - 1
            )
          )
        ),
        probe.ref
      )

      probe.fishForMessage(30.seconds) {
        case EnsimeTaskResponse(taskId0, Some(TypeAtPointResponse(symbol))) => {
          println()
          println("===")
          println(symbol)
          println("===")
          println()

          assert(taskId0 == taskId)
          assert(symbol == "List[Int]")
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


    pending
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
    case EnsimeReady => true
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
}
