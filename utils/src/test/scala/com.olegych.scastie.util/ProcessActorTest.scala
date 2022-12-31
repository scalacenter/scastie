package com.olegych.scastie.util

import java.io.File
import java.nio.file.{Files, StandardCopyOption}

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import com.olegych.scastie.api.ProcessOutput
import com.olegych.scastie.api.ProcessOutputType._
import com.olegych.scastie.util.ProcessActor._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.duration._

class ProcessActorTest() extends TestKit(ActorSystem("ProcessActorTest")) with ImplicitSender with AnyFunSuiteLike with BeforeAndAfterAll {

  test("do it") {
    (1 to 10).foreach { i =>
      println(s"--- Run $i ---")

      val command = new File("target", "echo.sh")
      Files.copy(getClass.getResourceAsStream("/echo.sh"), command.toPath, StandardCopyOption.REPLACE_EXISTING)
      command.setExecutable(true)

      val probe = TestProbe()

      val processActor = TestActorRef(new ProcessReceiver(command.getPath, probe.ref))

      processActor ! Input("abcd")
      processActor ! Input("1234")
      processActor ! Input("quit")

      def expected(msg0: String): Unit = {
        probe.expectMsgPF(8000.milliseconds) {
          case ProcessOutput(msg1, StdOut, _) if msg0.trim == msg1.trim => true
          case ProcessOutput(msg1, StdOut, _) =>
            println(s""""$msg1" != "$msg0"""")
            false
        }
      }

      expected("abcd")
      expected("1234")
    }
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}

class ProcessReceiver(command: String, probe: ActorRef) extends Actor {
  private val props =
    ProcessActor.props(command = List("bash", "-c", command.replace("\\", "/")), killOnExit = false)
  private val process = context.actorOf(props, name = "process-receiver")

  override def receive: Receive = {
    case output: ProcessOutput => probe ! output
    case input: Input          => process ! input
  }
}
