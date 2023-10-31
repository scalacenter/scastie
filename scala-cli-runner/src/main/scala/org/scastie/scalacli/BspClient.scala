package org.scastie.scalacli

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
import org.scastie.api.Problem
import org.scastie.api.Severity
import org.scastie.api
import java.util.concurrent.TimeUnit
import org.eclipse.lsp4j.jsonrpc.messages.CancelParams
import java.net.URI
import java.nio.file.Paths
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.FiniteDuration
import cats.data.EitherT
import cats.syntax.all._
import org.scastie.instrumentation.Instrument
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import coursier._
import org.scastie.buildinfo.BuildInfo
import org.scastie.api._
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError


object BspClient {
  case class BuildOutput(process: ProcessBuilder, diagnostics: List[Problem])

  val runner212 = Fetch().addDependencies(
    Dependency(Module(Organization("org.scastie"), ModuleName("runner_2.12")), BuildInfo.versionRuntime)
  ).run()

  val runner213 = Fetch().addDependencies(
    Dependency(Module(Organization("org.scastie"), ModuleName("runner_2.13")), BuildInfo.versionRuntime)
  ).run()

  val runner3 = Fetch().addDependencies(
    Dependency(Module(Organization("org.scastie"), ModuleName("runner_3")), BuildInfo.versionRuntime)
  ).run()

  private def diagSeverityToSeverity(severity: DiagnosticSeverity): Severity = {
    if (severity == DiagnosticSeverity.ERROR) api.Error
    else if (severity == DiagnosticSeverity.INFORMATION) api.Info
    else if (severity == DiagnosticSeverity.HINT) api.Info
    else if (severity == DiagnosticSeverity.WARNING) api.Warning
    else api.Error
  }

  def diagnosticToProblem(isWorksheet: Boolean)(diag: Diagnostic): Problem =
    Problem(
      diagSeverityToSeverity(diag.getSeverity()),
      Option(diag.getRange.getStart.getLine + Instrument.getMessageLineOffset(isWorksheet, isScalaCli = true)),
      diag.getMessage()
    )
}

trait ScalaCliServer extends BuildServer with ScalaBuildServer with JvmBuildServer

class BspClient(coloredStackTrace: Boolean, workingDir: Path) {
  import BspClient._

  private implicit val defaultTimeout: FiniteDuration = FiniteDuration(10, TimeUnit.SECONDS)
  val diagnostics: AtomicReference[List[Diagnostic]] = new AtomicReference(Nil)
  val gson = new Gson

  private val log = Logger("BspClient")
  private val localClient = new InnerClient()
  private val es = Executors.newFixedThreadPool(1)

  Process(Seq("scala-cli", "setup-ide", workingDir.toAbsolutePath.toString)).!
  private val processBuilder: java.lang.ProcessBuilder = new java.lang.ProcessBuilder()
  processBuilder.command("scala-cli", "--cli-version", "nightly", "bsp", workingDir.toAbsolutePath.toString)
  val scalaCliServer = processBuilder.start()

  private val bspIn = scalaCliServer.getInputStream()
  private val bspErr = scalaCliServer.getErrorStream()
  private val bspOut = scalaCliServer.getOutputStream()

  log.info(s"Starting Scala-CLI BSP in folder ${workingDir.toAbsolutePath().normalize().toString()}")


  private val bspLauncher = new Launcher.Builder[ScalaCliServer]()
    .setOutput(bspOut)
    .setInput(bspIn)
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


  case class ScalaCliBuildTarget(id: BuildTargetIdentifier, scalabuildTarget: ScalaBuildTarget)

  type BspTask[T] = EitherT[Future, BuildError, T]

  private def requestWithTimeout[T](f: ScalaCliServer => CompletableFuture[T])(implicit timeout: FiniteDuration): Future[T] =
    f(bspServer).orTimeout(timeout.length, timeout.unit).asScala

  private def reloadWorkspace(): BspTask[Unit] = EitherT(requestWithTimeout(_.workspaceReload())
    .map {
      case gsonMap: LinkedTreeMap[?, ?] if !gsonMap.isEmpty =>
        val gson = new Gson()
        val error = gson.fromJson(gson.toJson(gsonMap), classOf[ResponseError])
        Left(InternalBspError(error.getMessage))
      case _ => ().asRight
    }
  )

  private def extractScalaBuildTarget(buildTarget: BuildTarget): Option[ScalaCliBuildTarget] = {
    val maybeId = Option(buildTarget.getId)
    val maybeScalaBuildTarget = Try { gson.fromJson(buildTarget.getData.toString, classOf[ScalaBuildTarget]) }.toOption

    for {
      id <- maybeId
      scalaBuildTarget <- maybeScalaBuildTarget
    } yield {
      ScalaCliBuildTarget(id, scalaBuildTarget)
    }
  }

  private def getFirstBuildTarget(): BspTask[ScalaCliBuildTarget] = EitherT {
    requestWithTimeout(_.workspaceBuildTargets).map { results =>
      Option(results.getTargets)
        .fold(List.empty[BuildTarget])(_.asScala.toList)
        .filter(!_.getTags.asScala.toList.exists(_ == "test"))
        .filter(_.getDataKind == "scala")
        .headOption
        .flatMap(extractScalaBuildTarget)
        .toRight[BuildError](InternalBspError("No build target found."))
    }
  }

  private def compile(id: String, isWorksheet: Boolean, buildTargetId: BuildTargetIdentifier): BspTask[CompileResult] = EitherT {
    val params: CompileParams = new CompileParams(Collections.singletonList(buildTargetId))
    requestWithTimeout(_.buildTargetCompile(params))(10.seconds).map(compileResult =>
      compileResult.getStatusCode match {
        case StatusCode.OK => Right(compileResult)
        case StatusCode.ERROR => Left(CompilationError(diagnostics.getAndSet(Nil).map(diagnosticToProblem(isWorksheet))))
        case StatusCode.CANCELLED => Left(InternalBspError("Compilation cancelled"))
    })
  }

  private def getMainClass(mainClasses: List[JvmMainClass], isWorksheet: Boolean): Either[BuildError, JvmMainClass] =
    mainClasses match {
      case mainClass :: Nil => mainClass.asRight
      case mainClasses if isWorksheet && mainClasses.size == 2 => mainClasses.find(_.getClassName == Instrument.entryPointName)
        .toRight(InternalBspError(s"Can't find proper main for worksheet build"))
      case _ => Left(InternalBspError(s"Multiple main classes for target"))
    }

  private def getJvmRunEnvironment(id: BuildTargetIdentifier): BspTask[JvmEnvironmentItem] = EitherT {
    val param = new JvmRunEnvironmentParams(java.util.List.of(id))
    requestWithTimeout(_.buildTargetJvmRunEnvironment(param)).map { jvmRunEnvironment =>
      Option(jvmRunEnvironment.getItems)
        .map(x => {
          log.info(x.asScala.toList.toString)
          x
        })
        .fold(List.empty[JvmEnvironmentItem])(_.asScala.toList)
        .find(_.getTarget == id)
        .toRight[BuildError](InternalBspError(s"No JvmRunEnvironmentResult available."))
    }
  }

  private def makeProcess(
    runSettings: JvmEnvironmentItem,
    buildTarget: ScalaCliBuildTarget,
    isWorksheet: Boolean
  ): BspTask[ProcessBuilder] = EitherT.fromEither {

    val maybeRunnerClasspath: Either[BuildError, Seq[String]] = buildTarget.scalabuildTarget.getScalaBinaryVersion match {
      case "2.12" => runner212.map(_.toURI.toString).asRight
      case "2.13" => runner213.map(_.toURI.toString).asRight
      case v if v.startsWith("3") => runner3.map(_.toURI.toString).asRight
      case err => InternalBspError(s"Unsupported Scala version: $err").asLeft
    }

    val javaBinURI = URI.create(buildTarget.scalabuildTarget.getJvmBuildTarget.getJavaHome())
    val javaBinPath = Try { Paths.get(javaBinURI).resolve("bin/java").toString }.toEither.leftMap(err => InternalBspError(s"Can't find java binary: $err"))

    for {
      mainClass <- getMainClass(runSettings.getMainClasses.asScala.toList, isWorksheet)
      javaBin <- javaBinPath
      runnerClasspath <- maybeRunnerClasspath
    } yield {
      val classpath = (runnerClasspath ++ runSettings.getClasspath.asScala)
        .map(uri => Paths.get(new URI(uri))).mkString(":")

      val envVars = Map(
        "CLASSPATH" -> classpath
      ) ++ runSettings.getEnvironmentVariables.asScala

      val cmd = Seq(javaBin, "org.scastie.runner.Runner") ++ runSettings.getJvmOptions().asScala ++ Seq(mainClass.getClassName, coloredStackTrace.toString)
      val process = Process(cmd, cwd = new java.io.File(runSettings.getWorkingDirectory()), envVars.toSeq : _*)

      process
    }
  }

  def build(taskId: String, isWorksheet: Boolean, target: ScalaTarget): BspTask[BuildOutput] = {
    for {
      _ <- reloadWorkspace()
      buildTarget <- getFirstBuildTarget()
      compileResult <- compile(taskId, isWorksheet, buildTarget.id)
      jvmRunEnvironment <- getJvmRunEnvironment(buildTarget.id)
      process <- makeProcess(jvmRunEnvironment, buildTarget, isWorksheet)
    } yield BuildOutput(process, diagnostics.getAndSet(Nil).map(diagnosticToProblem(isWorksheet)))
  }

  // Kills the BSP connection and makes this object
  // un-usable.
  def end = {
    bspServer.buildShutdown().get(5, TimeUnit.SECONDS)
    bspServer.onBuildExit()
    Process("scala-cli --power bloop exit").!
    scalaCliServer.destroyForcibly()
    listeningThread.interrupt() // This will stop the thread
  }

  def initWorkspace: Unit = {
    val r = bspServer.buildInitialize(new InitializeBuildParams(
      "BspClient",
      "1.0.0",
      Bsp4j.PROTOCOL_VERSION,
      workingDir.toAbsolutePath.toUri.toString,
      new BuildClientCapabilities(Collections.singletonList("scala"))
    )).get // Force to wait
    bspServer.onBuildInitialized()
  }

  initWorkspace


  class InnerClient extends BuildClient {
    def onBuildPublishDiagnostics(params: PublishDiagnosticsParams): Unit = {
      log.debug(s"PublishDiagnosticsParams: $params")
      val incomingDiagnostics = Option(params.getDiagnostics()).fold(List.empty[Diagnostic])(_.asScala.toList)
      if (params.getReset()) diagnostics.set(incomingDiagnostics)
      else diagnostics.getAndUpdate(_ ++ incomingDiagnostics)
    }
    def onBuildLogMessage(params: LogMessageParams): Unit = log.debug(s"LogMessageParams: $params")
    def onBuildShowMessage(params: ShowMessageParams): Unit =  log.debug(s"ShowMessageParams: $params")
    def onBuildTargetDidChange(params: DidChangeBuildTarget): Unit =  log.debug(s"DidChangeBuildTarget: $params")
    def onBuildTaskFinish(params: TaskFinishParams): Unit =  log.debug(s"TaskFinishParams: $params")
    def onBuildTaskProgress(params: TaskProgressParams): Unit = log.debug(s"TaskProgressParams: $params")
    def onBuildTaskStart(params: TaskStartParams): Unit = log.debug(s"TaskStartParams: $params")
  }
}
