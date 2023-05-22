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
import scala.jdk.OptionConverters._
import scala.sys.process.{ Process, ProcessBuilder }
import java.util.Optional
import com.olegych.scastie.api.Problem
import com.olegych.scastie.api.Severity
import com.olegych.scastie.api
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.eclipse.lsp4j.jsonrpc.messages.CancelParams
import java.net.URI
import java.nio.file.Paths


object BspClient {
  case class BspRun(process: ProcessBuilder, warnings: List[Diagnostic], logMessages: List[String]) {
    def toProblemList: List[Problem] = convertDiagListToProblemList(warnings)
  }

  trait BspError extends Exception {
    val logs: List[String] = List()
  }
  case class FailedRunError(err: String, override val logs: List[String] = List()) extends BspError
  case class NoTargetsFoundException(err: String, override val logs: List[String] = List()) extends BspError
  case class NoMainClassFound(err: String, override val logs: List[String] = List()) extends BspError
  case class CompilationError(err: List[Diagnostic], override val logs: List[String] = List()) extends BspError {
    def toProblemList: List[Problem] = convertDiagListToProblemList(err)
  }

  private def diagSeverityToSeverity(severity: DiagnosticSeverity): Severity = {
    if (severity == DiagnosticSeverity.ERROR) api.Error
    else if (severity == DiagnosticSeverity.INFORMATION) api.Info
    else if (severity == DiagnosticSeverity.HINT) api.Info
    else if (severity == DiagnosticSeverity.WARNING) api.Warning
    else api.Error
  }


  private def convertDiagListToProblemList(list: List[Diagnostic]) =
    list.map(diagnostic =>
      Problem(
        diagSeverityToSeverity(diagnostic.getSeverity()),
        Some(diagnostic.getRange().getStart().getLine()),
        diagnostic.getMessage()
      ))


  type Callback = String => Any
}

trait ScalaCliServer extends BuildServer with ScalaBuildServer
        with JvmBuildServer

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
    override def run() = {
      try {
        bspLauncher.startListening().get()
      } catch {
        case _: Throwable => log.info("Listening thread down.")
      }
    }
  }
  listeningThread.start()

  def resetInternalBuffers = {
    logMessages = List()
  }

  def reloadWorkspace = bspServer.workspaceReload().asScala

  def getBuildTargetId: Future[Either[BuildTargetIdentifier, NoTargetsFoundException]] =
    bspServer.workspaceBuildTargets().asScala
      .map(_.getTargets().stream().filter(t => {
        val isTestBuild = t.getTags().stream().anyMatch(tag => tag.equals("test"))
        !isTestBuild
      }).findFirst())
      .map(_.toScala)
      .map(
        _.map(target => Left(target.getId()))
        .getOrElse(Right(NoTargetsFoundException("No build target found.")))
      )
  
  def compile(id: String, buildTargetId: BuildTargetIdentifier): Future[Either[CompileResult, CompilationError]] =
    bspServer.buildTargetCompile({
      val param = new CompileParams(Collections.singletonList(buildTargetId))
      param.setOriginId(s"$id-compile")
      param
    })
    .orTimeout(10, TimeUnit.SECONDS)
    .asScala
    .map(compileResult => {
      if (compileResult.getStatusCode() == StatusCode.ERROR) {
        log.info(s"Error while compiling $diagnostics.")
        Right(CompilationError(diagnostics, logMessages.map(_.getMessage())))
      } else {
        Left(compileResult)
      }
    })
    .recover({
      case _: TimeoutException => 
        log.warn(s"Compilation timeout on snippet $id")
        sys.exit(-1)
      case k => throw k
    })

  // Throws either NoMainClassFound or UnexpectedError on unexpected result.
  def getMainClass(id: BuildTargetIdentifier): Future[Either[ScalaMainClass, BspError]] =
    bspServer.buildTargetScalaMainClasses({
      val param = new ScalaMainClassesParams(Collections.singletonList(id))
      param.setOriginId(s"$id-main-classes")
      param
    })
    .asScala
    .map(_.getItems().asScala.toList)
    .map({
      case Nil => Right(NoMainClassFound("No main class found.", logMessages.map(_.getMessage())))
      case item :: _ => item.getClasses.asScala.toList match {
        case class_ :: _ => Left(class_)
        case _ => Right(NoMainClassFound("No main class found.", logMessages.map(_.getMessage())))
      }
    })

  def getJvmRunEnvironment(id: BuildTargetIdentifier): Future[Either[JvmEnvironmentItem, FailedRunError]] =
    bspServer.jvmRunEnvironment(new JvmRunEnvironmentParams(java.util.List.of(id)))
    .asScala
    .map(_.getItems().asScala.toList)
    .map({
      case head :: next => Left(head)
      case Nil => Right(FailedRunError("No JvmRunEnvironmentResult available.", logMessages.map(_.getMessage())))
    })

  def makeProcess(mainClass: String, runSettings: JvmEnvironmentItem) = {
    val classpath = runSettings.getClasspath.asScala.map(uri => Paths.get(new URI(uri))).mkString(":")
    val envVars = Map(
      "CLASSPATH" -> classpath
    ) ++ runSettings.getEnvironmentVariables.asScala

    val process = Process(
      Seq("java", mainClass) ++ runSettings.getJvmOptions().asScala,
      cwd = new java.io.File(runSettings.getWorkingDirectory()),
      envVars.toSeq : _*
    )

    process
  }

  // Forward Right if res is Right
  // or returns the future if Left
  def withShortCircuit[T, U](res: Either[T, BspError], f: T => Future[Either[U, BspError]]): Future[Either[U, BspError]] = {
    res match {
      case Left(value) => f(value)
      case Right(k) => Future.successful(Right(k))
    }
  }

  // Returns a (T, U) if the two are left
  // if any of the two is Right, then returns Right of the first
  def combineEither[T, U](a: Either[T, BspError], b: Either[U, BspError]) = {
    a match {
      case Left(value) => b.fold(bLeft => Left(value,bLeft), Right(_))
      case Right(value) => Right(value)
    }
  }

  def build(id: String): Future[Either[BspRun, BspError]]= {
    resetInternalBuffers

    for (
      r <- reloadWorkspace;
      buildTarget <- getBuildTargetId;

      // Compile
      compilationResult <- withShortCircuit(buildTarget, target => compile(id, target));

      // Get main class
      // Note: it is combined to compilationResult so if compilationResult fails,
      // then we do not continue
      mainClass <- withShortCircuit[(BuildTargetIdentifier, CompileResult), ScalaMainClass](
        combineEither(buildTarget, compilationResult),
        {
          case ((tId: BuildTargetIdentifier, _)) => getMainClass(tId)
        }
      );

      // Get JvmRunEnv
      jvmRunEnv <- withShortCircuit[(BuildTargetIdentifier, ScalaMainClass), JvmEnvironmentItem](
        combineEither(buildTarget, mainClass),
        {
          case ((tId: BuildTargetIdentifier, _)) => getJvmRunEnvironment(tId)
        }
      )
    ) yield {
      val ret = combineEither(mainClass, jvmRunEnv) match {
        case Left((mainClass, jvmRunEnv)) => {
          Left(BspRun(
            makeProcess(mainClass.getClassName(), jvmRunEnv),
            diagnostics,
            logMessages.map(_.getMessage())
          ))
        }
        case Right(value) => Right(value)
      }
      ret
    }
  }

  // Kills the BSP connection and makes this object
  // un-usable.
  def end = {
    bspServer.buildShutdown().get(2, TimeUnit.SECONDS)
    bspServer.onBuildExit()
    listeningThread.interrupt() // This will stop the thread
  }

  def initWorkspace: Unit = {
    val r = bspServer.buildInitialize(new InitializeBuildParams(
      "BspClient",
      "1.0.0", // TODO: maybe not hard-code the version? not really much important
      "2.1.0-M4", // TODO: same
      workingDir.toAbsolutePath().normalize().toUri().toString(),
      new BuildClientCapabilities(Collections.singletonList("scala"))
    )).get // Force to wait
    log.info(s"Initialized workspace: $r")
    bspServer.onBuildInitialized()
  }

  initWorkspace

  var diagnostics: List[Diagnostic] = List()

  // Note, log messages is not really useful now but if we want to forward
  // execution progress, could be a good idea
  var logMessages: List[LogMessageParams] = List()

  class InnerClient extends BuildClient {
    def onBuildLogMessage(params: LogMessageParams): Unit = {
      logMessages = params :: logMessages
    }
    def onBuildPublishDiagnostics(params: PublishDiagnosticsParams): Unit = {
      if (params.getReset())
        diagnostics = List()

      diagnostics = params.getDiagnostics().asScala.toList ++ diagnostics
    }
    def onBuildShowMessage(params: ShowMessageParams): Unit = () // log.info(s"ShowMessageParams: $params")
    def onBuildTargetDidChange(params: DidChangeBuildTarget): Unit = () // log.info(s"DidChangeBuildTarget: $params")
    def onBuildTaskFinish(params: TaskFinishParams): Unit = () // log.info(s"TaskFinishParams: $params")
    def onBuildTaskProgress(params: TaskProgressParams): Unit = () // log.info(s"TaskProgressParams: $params")
    def onBuildTaskStart(params: TaskStartParams): Unit = () // log.info(s"TaskStartParams: $params")
  }

  private def wrapTimeout[T](id: String, cf: CompletableFuture[T]) = {
    cf.orTimeout(30, TimeUnit.SECONDS).asScala.recover((throwable => {
      throwable match {
        case _: TimeoutException => {
          // TODO: cancel
          throw FailedRunError("Timeout exceeded.")
        }
        case _ => throw throwable
      }
    }))
  }
}