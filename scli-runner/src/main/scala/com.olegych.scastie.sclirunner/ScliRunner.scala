package com.olegych.scastie.sclirunner

import scala.collection.immutable.Queue
import com.olegych.scastie.api.SnippetId
import com.olegych.scastie.api.Inputs
import com.typesafe.scalalogging.Logger
import java.nio.file.Files

// Process interaction
import scala.sys.process._
import java.nio.charset.StandardCharsets
import scala.io.{Source => IOSource}
import com.olegych.scastie.instrumentation.Instrument
import com.olegych.scastie.instrumentation.InstrumentationFailure
import com.olegych.scastie.api.ScalaDependency
import com.olegych.scastie.instrumentation.InstrumentationFailureReport
import com.olegych.scastie.instrumentation.InstrumentedInputs
import java.io.OutputStream
import java.io.InputStream
import ch.epfl.scala.bsp4j.BuildClient
import ch.epfl.scala.bsp4j.LogMessageParams
import ch.epfl.scala.bsp4j.PublishDiagnosticsParams
import ch.epfl.scala.bsp4j.ShowMessageParams
import ch.epfl.scala.bsp4j.DidChangeBuildTarget
import ch.epfl.scala.bsp4j.TaskFinishParams
import ch.epfl.scala.bsp4j.TaskProgressParams
import ch.epfl.scala.bsp4j.TaskStartParams
import org.eclipse.lsp4j.jsonrpc.Launcher
import ch.epfl.scala.bsp4j.BuildServer
import ch.epfl.scala.bsp4j.InitializeBuildParams
import ch.epfl.scala.bsp4j.BuildClientCapabilities
import java.util.Collections
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import ch.epfl.scala.bsp4j.CompileParams
import ch.epfl.scala.bsp4j.RunParams

object ScliRunner {
  case class ScliTask(snippetId: SnippetId, inputs: Inputs, ip: String, login: Option[String])
}

class ScliRunner {
  import ScliRunner._

  private val log = Logger("ScliRunner")


  // Files
  private val workingDir = Files.createTempDirectory("scastie")
  private val scalaMain = workingDir.resolve("src/main/scala/main.scala")

  private def initFiles : Unit = {
    Files.createDirectories(scalaMain.getParent())
    writeFile(scalaMain, "@main def main = println(\"hello world!a\")")
  }

  private def writeFile(path: Path, content: String): Unit = {
    if (Files.exists(path)) {
      Files.delete(path)
    }

    Files.write(path, content.getBytes, StandardOpenOption.CREATE_NEW)

    ()
  }

  initFiles

  
  def runTask(task: ScliTask, onSuccess: String => Any): Option[InstrumentationFailureReport] = {
    log.info(s"Running task $task")

    // Instrumentation
    var input = InstrumentedInputs(task.inputs)

    input match {
      case Left(failure) => Some(failure)
      case Right(notFinalCode) => {
        // notFinalCode
        val runtimeDependency = task.inputs.target.runtimeDependency.map(x => Set(x))

        val code = (task.inputs.libraries ++ runtimeDependency.getOrElse(Set()))
          .map(scalaDepToFullName).map(s => s"//> using lib \"$s\"").mkString + "\n" + notFinalCode


        None
      }
    }
  }

  // Process streams
  private var pStdin: Option[OutputStream] = None
  private var pStdout: Option[InputStream] = None
  private var pStderr: Option[InputStream] = None

  private def startBsp : Unit = {
    log.info(s"Starting Scala-CLI BSP in folder ${workingDir.toAbsolutePath().normalize().toString()}")
    val processBuilder: ProcessBuilder = Process(Seq("scala-cli", "bsp", "."), workingDir.toFile())
    val io = BasicIO.standard(true)
      .withInput(i => pStdin = Some(i)) 
      .withError(e => pStderr = Some(e))
      .withOutput(o => pStdout = Some(o))

    val process = processBuilder.run(io)

    // TODO: really bad
    while (pStdin.isEmpty || pStdout.isEmpty || pStderr.isEmpty) Thread.sleep(100)

    // Create BSP connection
    import java.util.concurrent._
    val es = Executors.newFixedThreadPool(1)

    val bspLauncher = new Launcher.Builder[BuildServer]()
      .setOutput(pStdin.get)
      .setInput(pStdout.get)
      .setLocalService(localClient)
      .setExecutorService(es)
      .setRemoteInterface(classOf[BuildServer])
      .create()

    bspServer = Some(bspLauncher.getRemoteProxy())

    listeningThread = Some(new Thread {
      override def run() = bspLauncher.startListening().get()
    })
    listeningThread.get.start()

    val targetUri = workingDir.toAbsolutePath().normalize().toUri().toString()

    log.info("Initializing workspace")
    val initialize = bspServer.get.buildInitialize(new InitializeBuildParams(
      "BspClient",
      "1.0.0", // TODO: maybe not hard-code the version? not really much important
      "2.1.0-M3", // TODO: same
      targetUri,
      new BuildClientCapabilities(Collections.singletonList("scala"))
    )).get // Force to wait
    bspServer.get.onBuildInitialized()
    val compile = bspServer.get.buildTargetCompile(new CompileParams(Collections.singletonList(new BuildTargetIdentifier(targetUri))))
    compile.get()
    val run = bspServer.get.buildTargetRun(new RunParams(new BuildTargetIdentifier(targetUri)))
    val result = run.get()

    println(s"result: ${result.getStatusCode()}")
  }

  private def scalaDepToFullName = (dep: ScalaDependency) => s"${dep.groupId}::${dep.artifact}:${dep.version}"

  // Client
  class BspClient extends BuildClient {
    def onBuildLogMessage(params: LogMessageParams): Unit = log.info(s"onBuildLogMessage $params")
    def onBuildPublishDiagnostics(params: PublishDiagnosticsParams): Unit = log.info(s"onBuildPublishDiagnostics $params")
    def onBuildShowMessage(params: ShowMessageParams): Unit = log.info(s"onBuildShowMessage $params")
    def onBuildTargetDidChange(params: DidChangeBuildTarget): Unit = log.info(s"onBuildTargetDidChange $params")
    def onBuildTaskFinish(params: TaskFinishParams): Unit = log.info(s"onBuildTaskFinish $params")
    def onBuildTaskProgress(params: TaskProgressParams): Unit = log.info(s"onBuildTaskProgress $params")
    def onBuildTaskStart(params: TaskStartParams): Unit = log.info(s"onBuildTaskStart $params")

    override def onConnectWithServer(server: BuildServer): Unit = log.info("connected to server")
  }
  private val localClient = new BspClient()
  private var bspServer: Option[BuildServer] = None
  private var listeningThread: Option[Thread] = None

  startBsp
}
