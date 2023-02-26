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

object BspClient {
  case class BspClientRun(output: List[String])
  
  case class NoTargetsFoundException(err: String) extends Exception
  case class NoMainClassFound(err: String) extends Exception
  case class CompilationError(err: PublishDiagnosticsParams) extends Exception
}

trait ScalaCliServer extends BuildServer with ScalaBuildServer

class BspClient(private val workingDir: Path, private val inputStream: InputStream, private val outputStream: OutputStream) {
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

  // Runs asynchronously a build
  def build(id: String) = {
    val future = new CompletableFuture[BspClientRun]()

    bspServer.workspaceBuildTargets().thenAccept(buildTargets => {
      // Finds the first "run" target
      val target = buildTargets.getTargets().stream()
        .filter(t => {
          val isTestBuild = t.getTags().stream().anyMatch(tag => tag.equals("test"))
          !isTestBuild
        })
        .findFirst()

      if (target.isEmpty()) future.completeExceptionally(NoTargetsFoundException("No build target found."))
      else {
        val targetId = target.get().getId()

        // trigger compile
        bspServer.buildTargetCompile({
          val param = new CompileParams(Collections.singletonList(targetId))
          param.setOriginId(id + "-compile")
          param
        }).thenAccept(compileResult => {

          if (compileResult.getStatusCode() == StatusCode.ERROR) {
            // failed compilation
            future.completeExceptionally(CompilationError(diagnostics(id + "-compile")))
            diagnostics.remove(id + "-compile")
          } else {

            // get first main class
            bspServer.buildTargetScalaMainClasses({
              val param = new ScalaMainClassesParams(Collections.singletonList(targetId))
              param.setOriginId(id + "-main-classes")
              param
            }).thenAccept(scalaMainClasses => {
              val classes = scalaMainClasses.getItems()

              if (classes.size() == 0) future.completeExceptionally(NoMainClassFound("No main class found."))
              else {
                val mainClass = classes.get(0)

                // run
                bspServer.buildTargetRun({
                  val param = new RunParams(targetId)
                  param.setDataKind("scala-main-class")
                  param.setData(mainClass.getClasses().get(0))
                  param.setOriginId(id + "-run")
                  param
                }).thenAccept(result => {
                  future.complete(BspClientRun(logMessages(id + "-run").map(_.getMessage())))
                  logMessages.remove(id + "-run")
                })
              }
            })
          }
        })
      }
    })

    future
  }

  val diagnostics: Map[String, PublishDiagnosticsParams] = HashMap()
  val logMessages: Map[String, List[LogMessageParams]] = HashMap().withDefault(_ => List())

  class InnerClient extends BuildClient {
    def onBuildLogMessage(params: LogMessageParams): Unit = {
      val origin = params.getOriginId()

      if (origin != null)
        logMessages.put(origin, params +: logMessages(origin))
    }
    def onBuildPublishDiagnostics(params: PublishDiagnosticsParams): Unit = {
      val origin = params.getOriginId()

      if (origin != null)
        diagnostics.put(origin, params)
    }
    def onBuildShowMessage(params: ShowMessageParams): Unit = () // log.info(s"onBuildShowMessage $params")
    def onBuildTargetDidChange(params: DidChangeBuildTarget): Unit = () // log.info(s"onBuildTargetDidChange $params")
    def onBuildTaskFinish(params: TaskFinishParams): Unit = () // log.info(s"onBuildTaskFinish $params")
    def onBuildTaskProgress(params: TaskProgressParams): Unit = () // log.info(s"onBuildTaskProgress $params")
    def onBuildTaskStart(params: TaskStartParams): Unit = () // log.info(s"onBuildTaskStart $params")
  }
}