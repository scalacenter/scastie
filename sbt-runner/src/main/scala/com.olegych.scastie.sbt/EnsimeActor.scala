// package com.olegych.scastie.sbt

// import com.olegych.scastie.api._
// import akka.{Done, NotUsed}
// import akka.util.Timeout
// import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable}
// import akka.http.scaladsl.Http
// import akka.http.scaladsl.model.StatusCodes
// import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
// import akka.stream.{ActorMaterializer, OverflowStrategy}
// import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
// import org.ensime.jerky.JerkyFormats
// import org.ensime.api._
// import JerkyFormats._

// import scala.io.Source.fromFile
// import org.slf4j.LoggerFactory
// import java.io._
// import java.nio.file.{Files, Path}

// import scala.concurrent.duration._
// import scala.concurrent.Future
// import scala.util.{Failure, Success}

// case object Heartbeat

// case object MkEnsimeConfigRequest
// case class MkEnsimeConfigResponse(sbtDir: Path)

// class EnsimeActor(system: ActorSystem, sbtRunner: ActorRef) extends Actor {
//   import spray.json._
//   import system.dispatcher

//   private val log = LoggerFactory.getLogger(getClass)

//   implicit val materializer_ = ActorMaterializer()
//   implicit val timeout = Timeout(5.seconds)

//   private var ensimeProcess: Option[Process] = None
//   private var ensimeWS: Option[ActorRef] = None
//   private var hbRef: Option[Cancellable] = None

//   private var nextId = 1
//   private var requests = Map[Int, ActorRef]()

//   private var codeFile: Option[Path] = None

//   def handleRPCResponse(id: Int, payload: EnsimeServerMessage) = {
//     requests.get(id) match {
//       case Some(ref) =>
//         requests -= id
//         payload match {
//           case CompletionInfoList(prefix, completionList) =>
//             val completions = CompletionResponse(
//               completionList
//                 .sortBy(-_.relevance)
//                 .map(ci => {
//                   val typeInfo = ci.typeInfo match {
//                     case Some(info) => info.name
//                     case None       => ""
//                   }
//                   Completion(ci.name, typeInfo)
//                 })
//             )
//             log.info(s"Got completions: $completions")
//             ref ! completions

//           case symbolInfo: SymbolInfo =>
//             log.info(s"Got symbol info: $symbolInfo")
//             if (symbolInfo.`type`.name == "<none>")
//               ref ! TypeAtPointResponse("")
//             else if (symbolInfo.`type`.fullName.length <= 60)
//               ref ! TypeAtPointResponse(symbolInfo.`type`.fullName)
//             else
//               ref ! TypeAtPointResponse(symbolInfo.`type`.name)

//           case x =>
//             ref ! x
//         }
//       case _ =>
//         log.info(s"Got response without requester $id -> $payload")
//     }
//   }

//   def sendToEnsime(rpcRequest: RpcRequest, sender: ActorRef): Unit = {
//     requests += (nextId -> sender)
//     val env = RpcRequestEnvelope(rpcRequest, nextId)
//     nextId += 1

//     log.debug(s"Sending $env")
//     val json = env.toJson.prettyPrint
//     ensimeWS match {
//       case Some(ws) => ws ! TextMessage.Strict(json)
//       case None     => log.error("Trying to use not initialized WebSocket")
//     }
//   }

//   private def connectToEnsime(uri: String) = {
//     log.info(s"Connecting to $uri")

//     val req = WebSocketRequest(uri, subprotocol = Some("jerky"))
//     val webSocketFlow = Http()(system).webSocketClientFlow(req)

//     val messageSource: Source[Message, ActorRef] =
//       Source
//         .actorRef[TextMessage.Strict](bufferSize = 10, OverflowStrategy.fail)

//     def handleIncomingMessage(message: String) = {
//       val env = message.parseJson.convertTo[RpcResponseEnvelope]
//       env.callId match {
//         case Some(id) => handleRPCResponse(id, env.payload)
//         case None     => ()
//       }
//     }

//     val messageSink: Sink[Message, NotUsed] =
//       Flow[Message]
//         .map {
//           case msg: TextMessage.Strict => {
//             handleIncomingMessage(msg.text)
//           }
//           case msgStream: TextMessage.Streamed => {
//             msgStream.textStream.runFold("")(_ + _).onComplete {
//               case Success(msg) => handleIncomingMessage(msg)
//               case Failure(e) =>
//                 log.info(s"Couldn't process incoming text stream. $e")
//             }
//           }
//           case _ =>
//             log.info(
//               "Got unsupported ws response message type from ensime-server"
//             )
//         }
//         .to(Sink.ignore)

//     val ((ws, upgradeResponse), _) =
//       messageSource
//         .viaMat(webSocketFlow)(Keep.both)
//         .toMat(messageSink)(Keep.both)
//         .run()

//     upgradeResponse.flatMap { upgrade =>
//       if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
//         Future.successful(Done)
//       } else {
//         throw new RuntimeException(
//           s"Connection failed: ${upgrade.response.status}"
//         )
//       }
//     }

//     ensimeWS = Some(ws)

//     sendToEnsime(ConnectionInfoReq, self)
//     hbRef = Some(
//       context.system.scheduler
//         .schedule(30.seconds, 30.seconds, self, Heartbeat)
//     )
//   }

//   override def preStart() = {
//     log.info("Request ensime info from sbtRunner")
//     sbtRunner.tell(MkEnsimeConfigRequest, self)
//   }

//   private def startEnsimeServer(sbtDir: Path) = {
//     val ensimeConfigFile = sbtDir.resolve(".ensime")
//     val ensimeCacheDir = sbtDir.resolve(".ensime_cache")
//     Files.createDirectories(ensimeCacheDir)

//     val httpPortFile = ensimeCacheDir.resolve("http")

//     log.info("Form classpath using .ensime file")
//     val ensimeConf = fromFile(ensimeConfigFile.toFile).mkString

//     def parseEnsimeConfFor(field: String): String = {
//       s":$field \\(.*?\\)".r findFirstIn ensimeConf match {
//         case Some(x) => {
//           // we need to take everything inside ("...") & replace " " with : to form a classpath string
//           x.substring(x.indexOf("(") + 2, x.length - 2).replace("\" \"", ":")
//         }
//         case None => throw new Exception("Can't parse ensime config!")
//       }
//     }
//     val classpath = parseEnsimeConfFor("ensime-server-jars") +
//       parseEnsimeConfFor("scala-compiler-jars") +
//       parseEnsimeConfFor("compile-deps")

//     log.info("Starting Ensime server")
//     ensimeProcess = Some(
//       new ProcessBuilder(
//         "java",
//         "-Densime.config=" + ensimeConfigFile,
//         "-classpath",
//         classpath,
//         "-Densime.explode.on.disconnect=true",
//         "org.ensime.server.Server"
//       ).directory(sbtDir.toFile).start()
//     )

//     val stdout = ensimeProcess.get.getInputStream
//     streamLogger(stdout)
//     val stderr = ensimeProcess.get.getErrorStream
//     streamLogger(stderr)

//     connectToEnsime(
//       f"ws://127.0.0.1:${waitForAndReadPort(httpPortFile)}/websocket"
//     )

//     log.info("Warming up Ensime...")
//     codeFile = Some(sbtDir.resolve("src/main/scala/main.scala"))
//     sendToEnsime(
//       CompletionsReq(
//         fileInfo =
//           SourceFileInfo(RawFile(new File(codeFile.get.toString).toPath),
//                          Some(Inputs.defaultCode)),
//         point = 2,
//         maxResults = 100,
//         caseSens = false,
//         reload = false
//       ),
//       self
//     )

//     log.info("EnsimeActor is ready!")
//   }

//   override def postStop(): Unit = {
//     hbRef.foreach(_.cancel())
//     if (ensimeProcess.isDefined && ensimeProcess.get.isAlive) {
//       log.info("Killing Ensime server")
//       ensimeProcess.get.destroy()
//     }
//   }

//   def streamLogger(inputStream: InputStream): Unit = {
//     Future {
//       val is = new BufferedReader(new InputStreamReader(inputStream))
//       var line = is.readLine()
//       while (line != null) {
//         log.info(s"$line")
//         line = is.readLine()
//       }
//     }
//     ()
//   }

//   def receive = {
//     case MkEnsimeConfigResponse(sbtDir: Path) =>
//       log.info("Got MkEnsimeConfigResponse")
//       try {
//         startEnsimeServer(sbtDir)
//       } catch {
//         case e: FileNotFoundException => log.error(e.getMessage)
//       }

//     case TypeAtPointRequest(inputs, position) =>
//       log.info("TypeAtPoint request at EnsimeActor")
//       processRequest(
//         sender,
//         inputs,
//         position,
//         (code: String, pos: Int) => {
//           SymbolAtPointReq(
//             file = Right(
//               SourceFileInfo(
//                 RawFile(new File(codeFile.get.toString).toPath),
//                 Some(code)
//               )
//             ),
//             point = pos
//           )
//         }
//       )

//     case CompletionRequest(inputs, position) =>
//       log.info("Completion request at EnsimeActor")
//       processRequest(
//         sender,
//         inputs,
//         position,
//         (code: String, pos: Int) => {
//           CompletionsReq(
//             fileInfo =
//               SourceFileInfo(RawFile(new File(codeFile.get.toString).toPath),
//                              Some(code)),
//             point = pos,
//             maxResults = 100,
//             caseSens = false,
//             reload = false
//           )
//         }
//       )

//     case Heartbeat =>
//       sendToEnsime(ConnectionInfoReq, self)

//     case x =>
//       log.debug(s"Got $x at EnsimeActor")
//   }

//   private def processRequest(
//       sender: ActorRef,
//       inputs: Inputs,
//       position: Int,
//       rpcRequestFun: (String, Int) => RpcRequest
//   ): Unit = {
//     val (code, pos) = if (inputs.worksheetMode) {
//       (s"object Main extends App { ${inputs.code} }", position + 26)
//     } else {
//       (inputs.code, position)
//     }

//     if (codeFile.isDefined) {
//       sendToEnsime(rpcRequestFun(code, pos), sender)
//     } else {
//       log.info(
//         "Can't process request: code file's not defined – are sure Ensime started?"
//       )
//     }
//   }

//   private def waitForAndReadPort(path: Path): Int = {
//     var count = 0
//     var res: Option[Int] = None
//     val file = path.toFile
//     log.info(s"Trying to read port file at: $path")

//     while (count < 30 && res.isEmpty) {
//       if (file.exists) {
//         val handler = fromFile(file)
//         val contents = fromFile(file).mkString
//         handler.close()

//         res = Some(Integer.parseInt(contents.trim))
//       } else {
//         Thread.sleep(1000)
//       }
//       count += 1
//     }
//     res match {
//       case Some(p) =>
//         p
//       case None =>
//         throw new IllegalStateException(s"Port file $file not available")
//     }
//   }
// }
