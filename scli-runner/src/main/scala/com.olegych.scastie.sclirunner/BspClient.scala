package com.olegych.scastie.sclirunner

import java.io.InputStream
import java.io.OutputStream
import com.typesafe.scalalogging.Logger
import ch.epfl.scala.bsp4j._
import java.util.concurrent._
import org.eclipse.lsp4j.jsonrpc.Launcher
import java.nio.file.Path
import java.util.Collections

object BspClient {

}

trait ScalaCliServer extends BuildServer with ScalaBuildServer { }

class BspClient(private val workingDir: Path, private val inputStream: InputStream, private val outputStream: OutputStream) {

  private val log = Logger("BspClient")

  private val localClient = new InnerClient()
  private val es = Executors.newFixedThreadPool(1)

  private val bspLauncher = new Launcher.Builder[ScalaCliServer]()
    .setOutput(outputStream)
    .setInput(inputStream)
    .setLocalService(localClient)
    .setExecutorService(es)
    .setRemoteInterface(classOf[ScalaCliServer])
    .create()

  private val bspServer = bspLauncher.getRemoteProxy()

  private val listeningThread = new Thread {
    override def run() = bspLauncher.startListening().get()
  }
  listeningThread.start()


  log.info("Initializing workspace")
  private val initializeRequest = bspServer.buildInitialize(new InitializeBuildParams(
    "BspClient",
    "1.0.0", // TODO: maybe not hard-code the version? not really much important
    "2.1.0-M3", // TODO: same
    workingDir.toAbsolutePath().normalize().toUri().toString(),
    new BuildClientCapabilities(Collections.singletonList("scala"))
  )).get // Force to wait

  log.info(s"result build/initialize: ${initializeRequest}")
  bspServer.onBuildInitialized()


  def build: Unit = {
    val buildTargets = bspServer.workspaceBuildTargets().get
    val target = buildTargets.getTargets().stream()
      .filter(t => {
        val isTestBuild = t.getTags().stream().anyMatch(tag => tag.equals("test"))
        !isTestBuild
      })
      .findFirst()

    if (target.isEmpty()) throw new Error("No build target") // TODO: appropriate exceptions

    val buildTargetId = target.get().getId()

    log.info("compiling")
    val compileResult = bspServer.buildTargetCompile(
      new CompileParams(Collections.singletonList(buildTargetId))
    ).get
    log.info(s"compiledResult: $compileResult")

    // get first main class
    val mainClasses = bspServer.buildTargetScalaMainClasses(new ScalaMainClassesParams(Collections.singletonList(buildTargetId))).get().getItems().get(0).getClasses().get(0)
    // TODO: should not be a problem but check anyway that the code above does not crash ^
    log.info(s"mainClasses: $mainClasses")    
    
    log.info("running")
    val run = bspServer.buildTargetRun({
      val param = new RunParams(buildTargetId)
      param.setDataKind("scala-main-class")
      param.setData(mainClasses)
      param
    }).get()
    log.info(s"run: $run")
  }

  // val run = bspServer.buildTargetRun()
  //val result = run.get()
  //println(s"result run: ${result.getStatusCode()}")


  class InnerClient extends BuildClient {
    def onBuildLogMessage(params: LogMessageParams): Unit = log.info(s"onBuildLogMessage $params")
    def onBuildPublishDiagnostics(params: PublishDiagnosticsParams): Unit = log.info(s"onBuildPublishDiagnostics $params")
    def onBuildShowMessage(params: ShowMessageParams): Unit = log.info(s"onBuildShowMessage $params")
    def onBuildTargetDidChange(params: DidChangeBuildTarget): Unit = log.info(s"onBuildTargetDidChange $params")
    def onBuildTaskFinish(params: TaskFinishParams): Unit = log.info(s"onBuildTaskFinish $params")
    def onBuildTaskProgress(params: TaskProgressParams): Unit = log.info(s"onBuildTaskProgress $params")
    def onBuildTaskStart(params: TaskStartParams): Unit = log.info(s"onBuildTaskStart $params")

    override def onConnectWithServer(server: BuildServer): Unit = log.info("connected to server")
  }

}


