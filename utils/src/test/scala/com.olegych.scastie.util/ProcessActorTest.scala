package com.olegych.scastie.util

import ProcessActor._

import com.olegych.scastie.api.{ProcessOutput, ProcessOutputType}
import ProcessOutputType._

import akka.actor.{ActorSystem, Actor, ActorRef}

import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

import java.io.File

import scala.concurrent.duration._

class ProcessActorTest()
    extends TestKit(ActorSystem("ProcessActorTest"))
    with ImplicitSender
    with FunSuiteLike
    with BeforeAndAfterAll {

  print("\033c")

  test("do it") {
    (1 to 100).foreach { i =>
      println(s"--- Run $i ---")

      val command = getClass.getResource("/echo.sh").getFile
      new File(command).setExecutable(true)

      val probe = TestProbe()

      val processActor = TestActorRef(
        new ProcessReceiver(command, probe.ref)
      )

      processActor ! Input("abcd")
      processActor ! Input("1234")
      processActor ! Input("quit")

      def expected(msg0: String): Unit = {
        probe.expectMsgPF(400.milliseconds) {
          case ProcessOutput(msg1, StdOut) if msg0 == msg1 => true
          case _                                           => false
        }
      }

      expected("abcd")
      expected("1234")
    }
  }

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
}

class ProcessReceiver(command: String, probe: ActorRef) extends Actor {
  private val props =
    ProcessActor.props(command = List(command), killOnExit = false)
  private val process = context.actorOf(props, name = "process-receiver")

  override def receive: Receive = {
    case output: ProcessOutput => probe ! output
    case input: Input          => process ! input
  }
}
