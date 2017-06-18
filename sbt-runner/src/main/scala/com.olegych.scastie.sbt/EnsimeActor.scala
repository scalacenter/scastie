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
import com.olegych.scastie.api._
import org.slf4j.LoggerFactory
import org.ensime.jerky.JerkyFormats

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.ensime.api._

import scala.io.Source.fromFile
import JerkyFormats._

import scala.util.{Failure, Success}

case object Heartbeat

class EnsimeActor(system: ActorSystem) extends Actor {
  import spray.json._

  private val log = LoggerFactory.getLogger(getClass)
  private val ensimeLog = LoggerFactory.getLogger("ensime")

  implicit val materializer = ActorMaterializer()
  implicit val timeout = Timeout(5.seconds)

  private val rootDir = Files.createTempDirectory("scastie-ensime")
  private val ensimeVersion = "2.0.0-SNAPSHOT"
  private val ensimeConfigFile = rootDir.resolve(".ensime")
  private val ensimeCacheDir = rootDir.resolve(".ensime_cache")
  Files.createDirectories(ensimeCacheDir)

  private val sbtConfigExtra = s"""
      |// this is where the ensime-server snapshots are hosted
      |resolvers += Resolver.sonatypeRepo("snapshots")
      |libraryDependencies += "org.ensime" %% "ensime" % "$ensimeVersion"
      |""".stripMargin
  private val sbtPluginsConfigExtra = s"""addSbtPlugin("org.ensime" % "sbt-ensime" % "1.12.11")""".stripMargin
  private val defaultConfig = Inputs.default
  private val sbt = new Sbt(
    defaultConfig,
    rootDir,
    secretSbtConfigExtra = sbtConfigExtra,
    secretSbtPluginsConfigExtra = sbtPluginsConfigExtra
  )

  private val httpPortFile = ensimeCacheDir.resolve("http")

  private var ensimeProcess: Process = _
  private var ensimeWS: ActorRef =  _
  private var hbRef: Option[Cancellable] = None

  private var nextId = 1
  private var requests = Map[Int, ActorRef]()

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
          case x => ref ! x
        }
      case _ =>
        log.info(s"Got response without requester $id -> $payload")
    }
  }

  def sendToEnsime(rpcRequest: RpcRequest, sender: ActorRef): Unit = {
    requests += (nextId -> sender)
    val env = RpcRequestEnvelope(rpcRequest, nextId)
    nextId += 1

    log.debug(s"Sending $env")
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
      val env = message.parseJson.convertTo[RpcResponseEnvelope]
      env.callId match {
        case Some(id) => handleRPCResponse(id, env.payload)
        case None => ()
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
          case _ => log.info("Got unsupported ws response message type from ensime-server")
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
    log.info("Generating ensime config file")
    sbt.eval("ensimeConfig", defaultConfig, (_, _, _, _) => (), reload = false)

    log.info("Form classpath using .ensime file")
    val ensimeConf = fromFile(ensimeConfigFile.toFile).mkString

    def parseEnsimeConfFor(field: String): String = {
      s":$field \\(.*?\\)".r findFirstIn ensimeConf match {
        case Some(x) => {
          // we need to take everything inside ("...") & replace " " with : to form a classpath string
          x.substring(x.indexOf("(") + 2 , x.length - 2).replace("\" \"", ":")
        }
        case None => throw new Exception("Can't parse ensime config!")
      }
    }
    val classpath = parseEnsimeConfFor("ensime-server-jars") +
      parseEnsimeConfFor("scala-compiler-jars") +
      parseEnsimeConfFor("compile-deps")

    log.info("Starting Ensime server")
    ensimeProcess = new ProcessBuilder(
      "java",
      "-Densime.config=" + ensimeConfigFile,
      "-classpath", classpath,
      "-Densime.explode.on.disconnect=true",
      "org.ensime.server.Server"
    ).directory(rootDir.toFile).start()

    val stdout = ensimeProcess.getInputStream
    streamLogger(stdout)
    val stderr = ensimeProcess.getErrorStream
    streamLogger(stderr)

    connectToEnsime(f"ws://127.0.0.1:${waitForAndReadPort(httpPortFile)}/websocket")

    log.info("Warming up Ensime...")
    sendToEnsime(CompletionsReq(
      fileInfo = SourceFileInfo(RawFile(new File(sbt.codeFile.toString).toPath), Some(defaultConfig.code)),
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

  def streamLogger(inputStream: InputStream): Unit = {
    Future {
      val is = new BufferedReader(new InputStreamReader(inputStream))
      var line = is.readLine()
      while(line != null) {
        ensimeLog.info(s"$line")
        line = is.readLine()
      }
    }
    ()
  }

  def receive = {
    case CompletionRequest(inputs, position) => {
      log.info("Completion request at EnsimeActor")

      sbt.evalIfNeedsReload("ensimeConfig", inputs, (_, _, _, _) => (), reload = false)

      sendToEnsime(CompletionsReq(
        fileInfo = SourceFileInfo(RawFile(new File(sbt.codeFile.toString).toPath), Some(inputs.code)),
        point = position, maxResults = 100, caseSens = false, reload = false
      ), sender)
    }

    case Heartbeat =>
      sendToEnsime(ConnectionInfoReq, self)

    case x => {
      log.debug(s"Got $x at EnsimeActor")
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
