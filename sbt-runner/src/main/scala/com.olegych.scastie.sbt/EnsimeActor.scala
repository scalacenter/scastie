package com.olegych.scastie.sbt

import java.io.{BufferedReader, File, InputStream, InputStreamReader}
import java.nio.file.{Files, Path}

import akka.{Done, NotUsed}
import akka.util.Timeout

import scala.concurrent.duration._
import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.olegych.scastie._
import com.olegych.scastie.api._
import org.slf4j.LoggerFactory
import org.ensime.jerky.JerkyFormats

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.ensime.api._

import scala.io.Source.fromFile
import JerkyFormats._

import scala.util.{Success, Failure}

case object Heartbeat

class EnsimeActor(system: ActorSystem) extends Actor {

  import spray.json._

  private val log = LoggerFactory.getLogger(getClass)

  implicit val materializer = ActorMaterializer()
  implicit val timeout = Timeout(5.seconds)

  private val rootDir = Files.createTempDirectory("scastie-ensime")
  private val classpathFile = rootDir.resolve("classpath")
  private val ensimeVersion = "2.0.0-SNAPSHOT"
  private val sbtClasspathScript = s"""
      |import sbt._
      |import IO._
      |import java.io._
      |
      |ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }
      |
      |// allows local builds of scala
      |resolvers += Resolver.mavenLocal
      |
      |// this is where the ensime-server snapshots are hosted
      |resolvers += Resolver.sonatypeRepo("snapshots")
      |
      |libraryDependencies += "org.ensime" %% "ensime" % "$ensimeVersion"
      |
      |dependencyOverrides ++= Set(
      |   "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      |   "org.scala-lang" % "scala-library" % scalaVersion.value,
      |   "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      |   "org.scala-lang" % "scalap" % scalaVersion.value
      |)
      |
      |val saveClasspathTask = TaskKey[Unit]("saveClasspath", "Save the classpath to a file")
      |saveClasspathTask := {
      |   val managed = (managedClasspath in Runtime).value.map(_.data.getAbsolutePath)
      |   val unmanaged = (unmanagedClasspath in Runtime).value.map(_.data.getAbsolutePath)
      |   val out = file("$classpathFile")
      |   write(out, (unmanaged ++ managed).mkString(File.pathSeparator))
      |}
      |""".stripMargin

  private val config = Inputs(
    worksheetMode = true,
    code = "object Main extends App {}",
    target = ScalaTarget.Jvm.default,
    libraries = Set(),
    librariesFrom = Map(),
    sbtConfigExtra = sbtClasspathScript,
    sbtPluginsConfigExtra = "",
    showInUserProfile = false,
    forked = None
  )
  private val sbt = new Sbt(config, rootDir)

  private val ensimeCache = rootDir.resolve(".ensime_cache")
  private val httpPortFile = ensimeCache.resolve("http")
  private val tcpPortFile = ensimeCache.resolve("port")

  private var ensimeProcess: Process = _
  private var ensimeWS: ActorRef =  _
  private var hbRef: Option[Cancellable] = None

  private var nextId = 1
  private var requests = Map[Int, ActorRef]()

  private def initProject() = {
    log.info("Saving classpath")
    sbt.eval("saveClasspath", config, (_, _, _, _) => (), reload = false)
    log.info("Generating ensime config file")
    sbt.eval("ensimeConfig", config, (_, _, _, _) => (), reload = false)
  }

  def handleRPCResponse(id: Int, payload: EnsimeServerMessage) = {
    requests.get(id) match {
      case Some(ref) =>
        requests -= id
        payload match {
          case CompletionInfoList(prefix, completionList) => {
            val completions = CompletionResponse(completionList.sortBy(- _.relevance).map(ci => Completion(ci.name)))
            log.info(s"Got completions: $completions")
            ref ! completions
          }
          case x => {
            log.info(s"Payload's not recognized: $x")
            log.info("Sending it to an actor anyway...")
            ref ! x
          }
        }
      case _ =>
        log.info(s"Got response without requester $id -> $payload")
    }
  }

  def sendToEnsime(rpcRequest: RpcRequest, sender: ActorRef): Unit = {
    requests += (nextId -> sender)
    val env = RpcRequestEnvelope(rpcRequest, nextId)
    nextId += 1

    log.info(s"Sending $env")
    val json = env.toJson.prettyPrint
    ensimeWS ! TextMessage.Strict(json)
  }

  private def connectToEnsime(uri: String) = {
    log.info(s"Connecting to $uri")

    val req = WebSocketRequest(uri, subprotocol = Some("jerky"))
    val webSocketFlow = Http()(system).webSocketClientFlow(req)

    val messageSource: Source[Message, ActorRef] =
      Source
        .actorRef[TextMessage.Strict](bufferSize = 10, OverflowStrategy.fail)

    def handleIncomingMessage(message: String) = {
      try {
        val env = message.parseJson.convertTo[RpcResponseEnvelope]
        env.callId match {
          case Some(id) => {
            log.info(s"Received message for $id")
            handleRPCResponse(id, env.payload)
          }
          case None => {
            log.info(s"Received message with no id.")
          }
        }
      } catch {
        case e: Throwable => log.info(e)
      }
    }

    val messageSink: Sink[Message, NotUsed] =
      Flow[Message]
        .map {
          case msg: TextMessage.Strict => {
            handleIncomingMessage(msg.text)
          }
          case msgStream: TextMessage.Streamed => {
            msgStream.textStream.runFold("")(_ + _).onComplete {
              case Success(msg) => handleIncomingMessage(msg)
              case Failure(e) => log.info(s"Couldn't process incoming text stream. $e")
            }
          }
          case _ => log.info("Unsupported ws response message type from ENSIME")
        }
        .to(Sink.ignore)

    val ((ws, upgradeResponse), _) =
      messageSource
        .viaMat(webSocketFlow)(Keep.both)
        .toMat(messageSink)(Keep.both)
        .run()

    upgradeResponse.flatMap { upgrade =>
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        Future.successful(Done)
      } else {
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
      }
    }

    ensimeWS = ws

    sendToEnsime(ConnectionInfoReq, self)
    hbRef = Some(context.system.scheduler.schedule(30.seconds, 30.seconds, self, Heartbeat))
  }

  override def preStart() = {
    initProject()

    log.info("Starting Ensime server")
    val toolsJarPath = buildinfo.BuildInfo.JdkDir.toPath.resolve("lib/tools.jar")
    val classpathFileHandler = fromFile(classpathFile.toFile)
    val classpath = classpathFileHandler.mkString + ":" + toolsJarPath
    classpathFileHandler.close()

    ensimeProcess = new ProcessBuilder(
      "java",
      "-Densime.config=" + sbt.ensimeConfigFile,
      "-classpath", classpath,
      "-Densime.explode.on.disconnect=true",
      "org.ensime.server.Server"
    ).directory(rootDir.toFile).start()

    // val stdout = ensimeProcess.getInputStream
    // streamLogger(stdout, "[EnsimeServer]")

    val stderr = ensimeProcess.getErrorStream
    streamLogger(stderr, "[EnsimeServer]")

    connectToEnsime(f"ws://127.0.0.1:${waitForAndReadPort(httpPortFile)}/websocket")

    log.info("Warming up Ensime...")
    sendToEnsime(CompletionsReq(
      fileInfo = SourceFileInfo(RawFile(new File(sbt.codeFile.toString).toPath), Some(config.code)),
      point = 2, maxResults = 100, caseSens = false, reload = false
    ), self)

    log.info("EnsimeActor is ready!")
  }

  override def postStop(): Unit = {
    log.info("ensimeActor: postStop")
    hbRef.foreach(_.cancel())
    if (ensimeProcess.isAlive) {
      log.info("Killing Ensime server")
      ensimeProcess.destroy()
    }
  }

  def streamLogger(inputStream: InputStream, opTag: String): Unit = {
    Future {
      val is = new BufferedReader(new InputStreamReader(inputStream))
      var line = is.readLine()
      while(line != null) {
        log.info(s"$opTag: $line")
        line = is.readLine()
      }
    }
  }

  def receive = {
    case CompletionRequest(inputs, position) => {
      log.info("Completion request at EnsimeActor")

      val req = CompletionsReq(
        fileInfo = SourceFileInfo(RawFile(new File(sbt.codeFile.toString).toPath), Some(inputs.code)),
        point = position, maxResults = 100, caseSens = false, reload = false
      )

      sendToEnsime(req, sender)
    }

    case c: ConnectionInfo => {
      log.info("ConnectionInfo request at EnsimeActor")
    }

    case Heartbeat =>
      sendToEnsime(ConnectionInfoReq, self)

    case x => {
      log.info(s"Unknown request at EnsimeActor: $x")
    }
  }

  private def waitForAndReadPort(path: Path): Int = {
    var count = 0
    var res: Option[Int] = None
    val file = path.toFile
    log.info(s"Trying to read port file at: $path")

    while(count < 30 && res.isEmpty) {
      if(file.exists) {
        val handler = fromFile(file)
        val contents = fromFile(file).mkString
        handler.close()

        res = Some(Integer.parseInt(contents.trim))
      } else {
        Thread.sleep(1000)
      }
      count += 1
    }
    res match {
      case Some(p) =>
        p
      case None =>
        throw new IllegalStateException(s"Port file $file not available")
    }
  }


}
