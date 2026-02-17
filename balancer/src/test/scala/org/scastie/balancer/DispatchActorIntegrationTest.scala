package org.scastie.balancer

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import org.scastie.api._
import org.scastie.sbt._
import org.scastie.util.ReconnectInfo
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent._
import scala.concurrent.duration._

class DispatchActorIntegrationTest()
    extends TestKit(
      ActorSystem("DispatchActorIntegrationTest", RemotePortConfig(0))
    )
    with ImplicitSender
    with AnyFunSuiteLike
    with BeforeAndAfterAll {

  // import system.dispatcher
  implicit val timeout: Timeout = Timeout(25.seconds)

  test("persist warnings after re-fetching snippet from database (issue #1144)") {
    val code = """@deprecated("use bar", "1.0") def foo = 1; println(foo)"""

    val sid = run(code)

    waitFor(sid, Map(sid -> code))(p =>
      p.isDone && p.compilationInfos.nonEmpty
    )

    val fetchResult = Await.result(
      (dispatchActor.ask(FetchSnippet(sid))).mapTo[Option[FetchResult]],
      15.seconds
    )

    assert(fetchResult.isDefined, "Snippet should be found in storage")
    val progresses = fetchResult.get.progresses
    assert(progresses.nonEmpty, "Fetched snippet should have progress information")

    val lastProgress = progresses.last
    assert(lastProgress.compilationInfos.nonEmpty,
      s"Fetched snippet should retain compilation warnings, but got: ${lastProgress.compilationInfos}")
  }

  test("recover from crash") {
    val crash =
      """|val f = classOf[sun.misc.Unsafe].getDeclaredField("theUnsafe")
         |f.setAccessible(true)
         |val unsafe = f.get(null).asInstanceOf[sun.misc.Unsafe]
         |println("TRYING TO CRASH JVM")
         |unsafe.putLong(0, 0)
         |println("SHOULD HAVE CRASHED!")""".stripMargin

    val code1 = "println(1)"
    val code3 = "println(2)"

    val sid1 = run(code1)
    val sid2 = run(crash)
    val sid3 = run(code3)

    val ret = Map(
      sid1 -> code1,
      sid2 -> crash,
      sid3 -> code3
    )

    waitFor(sid1, ret)(_.isDone)
    waitFor(sid2, ret)(p => p.isDone || p.isTimeout)
    waitFor(sid3, ret)(_.isDone)
  }

  private val serverAkkaPort = 15000
  private val webSystem = ActorSystem("Web", RemotePortConfig(serverAkkaPort))

  private val sbtAkkaPort = 5150
  private val sbtSystem =
    ActorSystem("SbtRunner", RemotePortConfig(sbtAkkaPort))

  private val progressActor = TestProbe()
  private val statusActor = TestProbe()
  private val sbtActorReadyProbe = TestProbe()

  private val localhost = "127.0.0.1"

  private val sbtActor =
    sbtSystem.actorOf(
      Props(
        new SbtActor(
          system = sbtSystem,
          runTimeout = 10.seconds,
          reloadTimeout = 20.seconds,
          isProduction = false,
          readyRef = Some(sbtActorReadyProbe.ref),
          reconnectInfo = Some(
            ReconnectInfo(
              serverHostname = localhost,
              serverAkkaPort = serverAkkaPort,
              actorHostname = localhost,
              actorAkkaPort = sbtAkkaPort
            )
          )
        )
      ),
      name = "SbtActor"
    )

  sbtActorReadyProbe.fishForMessage(60.seconds) {
    case SbtActorReady => {
      println("sbt ready")
      true
    }
    case msg => {
      println("***")
      println(msg)
      println("***")
      false
    }
  }

  private val dispatchActor =
    webSystem.actorOf(
      Props(new DispatchActor(progressActor.ref, statusActor.ref)),
      name = "DispatchActor"
    )

  private var id = 0
  private def run(code: String): SnippetId = {
    val wrapped =
      s"""|object Main {
          |  def main(args: Array[String]): Unit = {
          |    $code
          |  }
          |}""".stripMargin

    val inputs = SbtInputs.default.copy(code = wrapped, isWorksheetMode = false)

    val task = RunSnippet(
      InputsWithIpAndUser(inputs, UserTrace("ip-" + id, None))
    )

    id += 1

    Await.result(
      (dispatchActor.ask(task)).mapTo[SnippetId],
      15.seconds
    )
  }

  private def waitFor(sid: SnippetId, ret: Map[SnippetId, String])(
      f: SnippetProgress => Boolean
  ): Unit = {

    progressActor.fishForMessage(50.seconds) {
      case progress: SnippetProgress => {
        if (progress.snippetId.get != sid) {
          println()
          println()
          println("*******************************")
          println("expected: " + ret(sid))
          println("got: " + ret(progress.snippetId.get))
          println("*******************************")
          println()
          println()

          assert(false)
        }

        f(progress)
      }
    }
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(webSystem)
    TestKit.shutdownActorSystem(sbtSystem)
    TestKit.shutdownActorSystem(system)
  }
}

object RemotePortConfig {
  def apply(port: Int): Config =
    ConfigFactory.parseString(
      s"""|akka {
          |  actor {
          |    provider = cluster
          |    allow-java-serialization = on
          |  }
          |  remote {
          |    artery.canonical {
          |      hostname = "127.0.0.1"
          |      port = $port
          |    }
          |  }
          |}""".stripMargin
    )
}
