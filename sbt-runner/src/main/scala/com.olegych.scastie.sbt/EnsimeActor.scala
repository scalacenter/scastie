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

case object MkEnsimeConfigRequest
case class MkEnsimeConfigResponse(sbtDir: Path)

class EnsimeActor(system: ActorSystem, sbtRunner: ActorRef) extends Actor {
  import spray.json._

  private val log = LoggerFactory.getLogger(getClass)
  private val ensimeLog = LoggerFactory.getLogger("EnsimeOutput")

  implicit val materializer_ = ActorMaterializer()
  implicit val timeout = Timeout(5.seconds)

  private var ensimeProcess: Process = _
  private var ensimeWS: ActorRef = _
  private var hbRef: Option[Cancellable] = None

  private var nextId = 1
  private var requests = Map[Int, ActorRef]()

  private var codeFile: Path = _

  def handleRPCResponse(id: Int, payload: EnsimeServerMessage) = {
    requests.get(id) match {
      case Some(ref) =>
        requests -= id
        payload match {
          case CompletionInfoList(prefix, completionList) =>
            val completions = CompletionResponse(
              completionList
                .sortBy(-_.relevance)
                .map(ci => {
                  val typeInfo = ci.typeInfo match {
                    case Some(info) => info.name
                    case None => ""
                  }
                  Completion(ci.name, typeInfo)
                })
            )
            log.info(s"Got completions: $completions")
            ref ! completions

          case symbolInfo: SymbolInfo =>
            log.info(s"Got symbol info: $symbolInfo")
            if (symbolInfo.`type`.name == "<none>")
              ref ! TypeAtPointResponse("")
            else if (symbolInfo.`type`.fullName.length <= 60)
              ref ! TypeAtPointResponse(symbolInfo.`type`.fullName)
            else
              ref ! TypeAtPointResponse(symbolInfo.`type`.name)

          case x =>
            ref ! x
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
              case Failure(e) =>
                log.info(s"Couldn't process incoming text stream. $e")
            }
          }
          case _ =>
            log.info(
              "Got unsupported ws response message type from ensime-server"
            )
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
        throw new RuntimeException(
          s"Connection failed: ${upgrade.response.status}"
        )
      }
    }

    ensimeWS = ws

    sendToEnsime(ConnectionInfoReq, self)
    hbRef = Some(
      context.system.scheduler
        .schedule(30.seconds, 30.seconds, self, Heartbeat)
    )
  }

  override def preStart() = {
    log.info("Request ensime info from sbtRunner [TELL!]")
    sbtRunner.tell(MkEnsimeConfigRequest, self)
  }

  private def startEnsimeServer(sbtDir: Path) = {
    val ensimeConfigFile = sbtDir.resolve(".ensime")
    val ensimeCacheDir = sbtDir.resolve(".ensime_cache")
    Files.createDirectories(ensimeCacheDir)

    val httpPortFile = ensimeCacheDir.resolve("http")

    log.info("Form classpath using .ensime file")
    val ensimeConf = fromFile(ensimeConfigFile.toFile).mkString

    def parseEnsimeConfFor(field: String): String = {
      s":$field \\(.*?\\)".r findFirstIn ensimeConf match {
        case Some(x) => {
          // we need to take everything inside ("...") & replace " " with : to form a classpath string
          x.substring(x.indexOf("(") + 2, x.length - 2).replace("\" \"", ":")
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
      "-classpath",
      classpath,
      "-Densime.explode.on.disconnect=true",
      "org.ensime.server.Server"
    ).directory(sbtDir.toFile).start()

    val stdout = ensimeProcess.getInputStream
    streamLogger(stdout)
    val stderr = ensimeProcess.getErrorStream
    streamLogger(stderr)

    connectToEnsime(
      f"ws://127.0.0.1:${waitForAndReadPort(httpPortFile)}/websocket"
    )

    log.info("Warming up Ensime...")
    codeFile = sbtDir.resolve("src/main/scala/main.scala")
    sendToEnsime(
      CompletionsReq(
        fileInfo =
          SourceFileInfo(RawFile(new File(codeFile.toString).toPath),
                         Some(Inputs.defaultCode)),
        point = 2,
        maxResults = 100,
        caseSens = false,
        reload = false
      ),
      self
    )

    log.info("EnsimeActor is ready!")
  }

  override def postStop(): Unit = {
    hbRef.foreach(_.cancel())
    if (ensimeProcess != null && ensimeProcess.isAlive) {
      log.info("Killing Ensime server")
      ensimeProcess.destroy()
    }
  }

  def streamLogger(inputStream: InputStream): Unit = {
    Future {
      val is = new BufferedReader(new InputStreamReader(inputStream))
      var line = is.readLine()
      while (line != null) {
        ensimeLog.info(s"$line")
        line = is.readLine()
      }
    }
    ()
  }

  def receive = {
    case MkEnsimeConfigResponse(sbtDir: Path) => {
      log.info("Got MkEnsimeConfigResponse")
      startEnsimeServer(sbtDir)
    }

    case TypeAtPointRequest(inputs, position) => {
      log.info("TypeAtPoint request at EnsimeActor")

      if (!inputs.worksheetMode) {
        sendToEnsime(
          SymbolAtPointReq(
            file = Right(
              SourceFileInfo(
                RawFile(
                  new File(codeFile.toString).toPath),
                Some(inputs.code)
              )
            ),
            point = position
          ),
          sender
        )
      }
    }

    case CompletionRequest(inputs, position) => {
      log.info("Completion request at EnsimeActor")

      if (!inputs.worksheetMode) {
        sendToEnsime(
          CompletionsReq(
            fileInfo =
              SourceFileInfo(RawFile(new File(codeFile.toString).toPath),
                Some(inputs.code)),
            point = position,
            maxResults = 100,
            caseSens = false,
            reload = false
          ),
          sender
        )
      }
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

    while (count < 30 && res.isEmpty) {
      if (file.exists) {
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
