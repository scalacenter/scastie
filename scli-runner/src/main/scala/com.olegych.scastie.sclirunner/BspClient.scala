package com.olegych.scastie.sclirunner

import com.typesafe.scalalogging.Logger
import scala.collection.mutable.{Map, HashMap}
import org.eclipse.lsp4j.jsonrpc.Launcher
import ch.epfl.scala.bsp4j._
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.CompletableFuture
import java.io.{InputStream, OutputStream}
import java.nio.file.Path
import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.FutureConverters._
import scala.jdk.CollectionConverters._
import java.util.Optional
import com.olegych.scastie.api.Problem
import com.olegych.scastie.api.Severity
import com.olegych.scastie.api


object BspClient {
  case class BspClientRun(output: List[String])

  case class NoTargetsFoundException(err: String) extends Exception
  case class NoMainClassFound(err: String) extends Exception
  case class CompilationError(err: List[Diagnostic]) extends Exception {

    private def diagSeverityToSeverity(severity: DiagnosticSeverity): Severity = {
      if (severity == DiagnosticSeverity.ERROR) api.Error
      else if (severity == DiagnosticSeverity.INFORMATION) api.Info
      else if (severity == DiagnosticSeverity.HINT) api.Info
      else if (severity == DiagnosticSeverity.WARNING) api.Warning
      else api.Error
    }

    def toProblemList: List[Problem] = {
      err.map(diagnostic =>
        Problem(
          diagSeverityToSeverity(diagnostic.getSeverity()),
          Some(diagnostic.getRange().getStart().getLine()),
          diagnostic.getMessage()
        )).toList
    }


  }
  case class FailedRunError(err: String) extends Exception

  type Callback = String => Any
}

trait ScalaCliServer extends BuildServer with ScalaBuildServer

class BspClient(private val workingDir: Path,
                private val inputStream: InputStream,
                private val outputStream: OutputStream) {
  import BspClient._

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

  def build(id: String,
            onOutput: Callback = _ => ()) = {

    runMessageEvent.put(s"$id-run", onOutput)

    bspServer.workspaceReload().asScala
    .map(d => log.info("Reloaded workspace."))
    .flatMap(_ => {
      bspServer.workspaceBuildTargets().asScala
      // Getting the first build target that is not test (which would be "run")
      .map(
        _.getTargets().stream().filter(t => {
          val isTestBuild = t.getTags().stream().anyMatch(tag => tag.equals("test"))
          !isTestBuild
        }).findFirst()
      )
      // Fail if no build target has been found.
      .map(opt => {
        if (opt.isEmpty()) throw NoTargetsFoundException("No build target found.")
        opt.get()
      })
      // Otherwise compile
      .flatMap(target => {
        val targetId = target.getId()
          // Trigger compilation
        bspServer.buildTargetCompile({
          val param = new CompileParams(Collections.singletonList(targetId))
          param.setOriginId(s"$id-compile")
          param
        }).asScala
        .map(compileResult => {
          log.info(s"Compilation result $compileResult")
          compileResult
        })
        // Handle failed compilation
        .map(compileResult => {
          if (compileResult.getStatusCode() == StatusCode.ERROR) {
            val diag = diagnostics(s"$id-compile")
            log.info(s"Error while compiling $diag.")
            diagnostics.remove(s"$id-compile")
            throw CompilationError(diag)
          }
          compileResult
        })
        // it compiled
        // Asking for main classes
        .flatMap(_ => 
          bspServer.buildTargetScalaMainClasses({
            val param = new ScalaMainClassesParams(Collections.singletonList(targetId))
            param.setOriginId(id + "-main-classes")
            param
          }).asScala
        )
        // Check if main classes exists
        .map(r => r.getItems())
        .map(classes => {
          if (classes.size() == 0) throw NoMainClassFound("No main class found.")
          classes.get(0)
        })
        // Run
        .flatMap(mainClass => {
          bspServer.buildTargetRun({
            val param = new RunParams(targetId)
            param.setDataKind("scala-main-class")
            param.setData(mainClass.getClasses().get(0))
            param.setOriginId(s"$id-run")
            param
          }).asScala
        })
        .map(result => {
          if (result.getStatusCode() == StatusCode.ERROR) throw FailedRunError(s"Failed run: $result.")
          result
        })
        .map(_ => {
          val output = logMessages(s"$id-run")
          logMessages.remove(s"$id-run")
          runMessageEvent.remove(id)

          log.info(s"Ran successfully: $output")
          BspClientRun(output.map(_.getMessage()))
        })
      })
    })
  }

  def initWorkspace: Unit = {
    val r = bspServer.buildInitialize(new InitializeBuildParams(
      "BspClient",
      "1.0.0", // TODO: maybe not hard-code the version? not really much important
      "2.1.0-M3", // TODO: same
      workingDir.toAbsolutePath().normalize().toUri().toString(),
      new BuildClientCapabilities(Collections.singletonList("scala"))
    )).get // Force to wait
    log.info(s"initialized $r")
    bspServer.onBuildInitialized()
  }

  initWorkspace

  val runMessageEvent: Map[String, Callback] = HashMap()

  val diagnostics: Map[String, List[Diagnostic]] = HashMap().withDefault(_ => List())
  val logMessages: Map[String, List[LogMessageParams]] = HashMap().withDefault(_ => List())

  class InnerClient extends BuildClient {
    def onBuildLogMessage(params: LogMessageParams): Unit = {
      val origin = params.getOriginId()

      if (origin != null) {
        logMessages.put(origin, params +: logMessages(origin))
        runMessageEvent(origin)(params.getMessage())
      }
    }
    def onBuildPublishDiagnostics(params: PublishDiagnosticsParams): Unit = {
      val origin = params.getOriginId()

      if (origin != null)
        diagnostics.put(origin, params.getDiagnostics.asScala.toList ++ diagnostics(origin))
    }
    def onBuildShowMessage(params: ShowMessageParams): Unit = ()
    def onBuildTargetDidChange(params: DidChangeBuildTarget): Unit = ()
    def onBuildTaskFinish(params: TaskFinishParams): Unit = ()
    def onBuildTaskProgress(params: TaskProgressParams): Unit = ()
    def onBuildTaskStart(params: TaskStartParams): Unit = ()
  }
}