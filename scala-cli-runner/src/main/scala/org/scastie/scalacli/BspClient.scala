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
import org.scastie.api.{Problem, Severity}
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
import coursierapi.{Fetch, Dependency}
import org.scastie.buildinfo.BuildInfo
import org.scastie.api._
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import scala.util.control.NonFatal
import java.io.PrintWriter
import java.lang
import org.scastie.instrumentation.PositionMapper


object BspClient {

  private val gson = new Gson()

  case class BuildOutput(process: ProcessBuilder, diagnostics: List[Problem])

  sealed trait Runner {
    def moduleName: String
    def matches(scalaBinaryVersion: String): Boolean
  }

  case object Runner212 extends Runner {
    val moduleName = "runner_2.12"
    def matches(scalaBinaryVersion: String): Boolean = scalaBinaryVersion == "2.12"
  }

  case object Runner213 extends Runner {
    val moduleName = "runner_2.13"
    def matches(scalaBinaryVersion: String): Boolean = scalaBinaryVersion == "2.13"
  }

  case object Runner3 extends Runner {
    val moduleName = "runner_3"
    def matches(scalaBinaryVersion: String): Boolean = scalaBinaryVersion.startsWith("3")
  }

  object Runner {
    val all = List(Runner212, Runner213, Runner3)

    def forScalaVersion(scalaBinaryVersion: String): Either[BuildError, Runner] =
      all.find(_.matches(scalaBinaryVersion))
      .toRight(InternalBspError(s"Unsupported Scala version: $scalaBinaryVersion"))
  }

  private def getRunner(runner: Runner) = {
    Fetch.create()
      .addDependencies(Dependency.of("org.scastie", runner.moduleName, BuildInfo.versionRuntime))
      .fetch()
      .asScala
      .filter(_.getName.contains(runner.moduleName))
      .map(_.toURI.toString)
      .asRight
  }

  private def diagSeverityToSeverity(severity: DiagnosticSeverity): Severity = {
    if (severity == DiagnosticSeverity.ERROR) api.Error
    else if (severity == DiagnosticSeverity.INFORMATION) api.Info
    else if (severity == DiagnosticSeverity.HINT) api.Info
    else if (severity == DiagnosticSeverity.WARNING) api.Warning
    else api.Error
  }

  private def mapBspPosition(line: Int, char: Int, positionMapper: Option[PositionMapper], offset: Int): (Int, Int) = {
    positionMapper match {
      case Some(mapper) =>
        /* BSP is 0-indexed, mapper expects 1-indexed */
        val bspLine = line + 1
        /* Convert back to 0-indexed */
        (mapper.mapLine(bspLine) - 1, mapper.mapColumn(bspLine, char + 1) - 1)
      case None =>
        (line + offset, char)
    }
  }

  private def convertBspActionToScastie(bspAction: ch.epfl.scala.bsp4j.ScalaAction, positionMapper: Option[PositionMapper], offset: Int): api.ScalaAction = {
    val edit = Option(bspAction.getEdit).map { bspEdit =>
      val changes = Option(bspEdit.getChanges)
        .map(_.asScala.toList.map { bspTextEdit =>
          val bspRange = bspTextEdit.getRange
          val (startLine, startChar) = mapBspPosition(bspRange.getStart.getLine, bspRange.getStart.getCharacter, positionMapper, offset)
          val (endLine, endChar) = mapBspPosition(bspRange.getEnd.getLine, bspRange.getEnd.getCharacter, positionMapper, offset)

          val range = api.DiagnosticRange(
            start = api.DiagnosticPosition(line = startLine, character = startChar),
            end = api.DiagnosticPosition(line = endLine, character = endChar)
          )
          api.ScalaTextEdit(range = range, newText = bspTextEdit.getNewText)
        })
        .getOrElse(List.empty)

      api.ScalaWorkspaceEdit(changes = changes)
    }

    api.ScalaAction(
      title = bspAction.getTitle,
      description = Option(bspAction.getDescription),
      edit = edit
    )
  }

  def diagnosticToProblem(isWorksheet: Boolean, positionMapper: Option[PositionMapper] = None)(diag: Diagnostic): Problem = {
    val offset = Instrument.getMessageLineOffset(isWorksheet)
    val bspRange = diag.getRange

    val (startLine, startChar) = mapBspPosition(bspRange.getStart.getLine, bspRange.getStart.getCharacter, positionMapper, offset)
    val (_, endChar) = mapBspPosition(bspRange.getEnd.getLine, bspRange.getEnd.getCharacter, positionMapper, offset)

    val actions: Option[List[org.scastie.api.ScalaAction]] = for {
      data <- Option(diag.getData())
      scalaDiag <- Try(gson.fromJson(data.toString, classOf[ScalaDiagnostic])).toOption
      bspActions <- Option(scalaDiag.getActions())
    } yield bspActions.asScala.toList.map(bspAction => convertBspActionToScastie(bspAction, positionMapper, offset))

    Problem(
      diagSeverityToSeverity(diag.getSeverity()),
      Option(startLine + 1),
      Some(startChar + 1),
      Some(endChar + 1),
      diag.getMessage(),
      actions
    )
  }

  val scalaCliExec = Seq("cs", "launch", "org.virtuslab.scala-cli:cliBootstrapped:latest.release", "-M", "scala.cli.ScalaCli", "--")
}

trait ScalaCliServer extends BuildServer with ScalaBuildServer with JvmBuildServer

class BspClient(coloredStackTrace: Boolean, workingDir: Path, compilationTimeout: FiniteDuration, reloadTimeout: FiniteDuration) {
  import BspClient._

  private implicit val defaultTimeout: FiniteDuration = FiniteDuration(10, TimeUnit.SECONDS)
  val diagnostics: AtomicReference[List[Diagnostic]] = new AtomicReference(Nil)
  val gson = new Gson

  private val log = Logger("BspClient")
  private val localClient = new InnerClient()
  private val es = Executors.newFixedThreadPool(1)

  val scalaCliExec = Seq("cs", "launch", "org.virtuslab.scala-cli:cliBootstrapped:latest.release", "-M", "scala.cli.ScalaCli", "--")
  Process(scalaCliExec ++  Seq("clean", workingDir.toAbsolutePath.toString)).!
  Process(scalaCliExec ++ Seq("setup-ide", workingDir.toAbsolutePath.toString)).!

  private val processBuilder: java.lang.ProcessBuilder = new java.lang.ProcessBuilder()
  val logFile = workingDir.toAbsolutePath.resolve("bsp.error.log")
  processBuilder
    .command((scalaCliExec ++ Seq("bsp", workingDir.toAbsolutePath.toString)):_*)
    .redirectError(logFile.toFile)

  val scalaCliServer = processBuilder.start()

  private val bspIn = scalaCliServer.getInputStream()
  private val bspOut = scalaCliServer.getOutputStream()
  private val bspErr = scalaCliServer.getErrorStream()

  log.info(s"Starting Scala-CLI BSP in folder ${workingDir.toAbsolutePath().normalize().toString()}")

  private val bspLauncher = new Launcher.Builder[ScalaCliServer]()
    .setOutput(bspOut)
    .setInput(bspIn)
    .setLocalService(localClient)
    .setExecutorService(es)
    .setRemoteInterface(classOf[ScalaCliServer])
    .create()

  private val bspServer = bspLauncher.getRemoteProxy()
  private val listening = bspLauncher.startListening()

  bspServer.buildInitialize(new InitializeBuildParams(
    "BspClient",
    "1.1.0",
    Bsp4j.PROTOCOL_VERSION,
    workingDir.toAbsolutePath.toUri.toString,
    new BuildClientCapabilities(List("scala", "java").asJava)
  )).get // Force to wait

  bspServer.onBuildInitialized()

  case class ScalaCliBuildTarget(id: BuildTargetIdentifier, scalabuildTarget: ScalaBuildTarget)

  type BspTask[T] = EitherT[Future, BuildError, T]

  private def requestWithTimeout[T](f: ScalaCliServer => CompletableFuture[T])(implicit timeout: FiniteDuration): Future[T] =
    f(bspServer).orTimeout(timeout.length, timeout.unit).asScala

  private def reloadWorkspace(retry: Int = 0): BspTask[Unit] = {
    log.trace(s"[reloadWorkspace] Starting workspace reload, retry=$retry, current diagnostics=${diagnostics.get().size}")
    EitherT(requestWithTimeout(_.workspaceReload())(using reloadTimeout).flatMap {
      case gsonMap: LinkedTreeMap[?, ?] if !gsonMap.isEmpty =>
          val gson = new Gson()
          val error = gson.fromJson(gson.toJson(gsonMap), classOf[ResponseError])
          log.info(s"Reload failed: ${error.getMessage}")
          log.trace(s"[reloadWorkspace] Reload failed, diagnostics after failure=${diagnostics.get().size}")
          if (retry < 3) {
            log.info(s"Reload failed, retry #$retry/3")
            reloadWorkspace(retry + 1).value
          } else {
            Future.successful(Left(InternalBspError(error.getMessage)))
          }
      case _ =>
        log.trace(s"[reloadWorkspace] Reload successful, diagnostics after reload=${diagnostics.get().size}")
        Future.successful(().asRight)
    })
  }

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

  private def compile(id: String, isWorksheet: Boolean, buildTargetId: BuildTargetIdentifier, positionMapper: Option[PositionMapper]): BspTask[CompileResult] = EitherT {
    val currentDiagnostics = diagnostics.get()
    log.trace(s"[$id - compile] Starting compilation, current diagnostics count: ${currentDiagnostics.size}")

    val params: CompileParams = new CompileParams(Collections.singletonList(buildTargetId))
    requestWithTimeout(_.buildTargetCompile(params))(using compilationTimeout).map { compileResult =>
      val statusCode = compileResult.getStatusCode
      val diagsBeforeGet = diagnostics.get()
      log.trace(s"[$id - compile] Compilation finished, statusCode=$statusCode, diagnostics before get: ${diagsBeforeGet.size}")

      statusCode match {
        case StatusCode.OK =>
          log.trace(s"[$id - compile] Status OK")
          Right(compileResult)
        case StatusCode.ERROR =>
          val problems = diagnostics.getAndSet(Nil).map(diagnosticToProblem(isWorksheet, positionMapper))
          log.trace(s"[$id - compile] Status ERROR, problems count: ${problems.size}")
          if (problems.isEmpty) {
            log.warn(s"[$id - compile] ERROR status but no diagnostics!")
          }
          Left(CompilationError(problems))
        case StatusCode.CANCELLED =>
          log.trace(s"[$id - compile] Status CANCELLED")
          Left(InternalBspError("Compilation cancelled"))
      }
    }
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
    val javaBinURI = URI.create(buildTarget.scalabuildTarget.getJvmBuildTarget.getJavaHome())
    val javaBinPath = Try { Paths.get(javaBinURI).resolve("bin/java").toString }
      .toEither
      .leftMap(err => InternalBspError(s"Can't find java binary: $err"))

    for {
      runner <- Runner.forScalaVersion(buildTarget.scalabuildTarget.getScalaBinaryVersion())
      runnerClasspath <- getRunner(runner)
      mainClass <- getMainClass(runSettings.getMainClasses.asScala.toList, isWorksheet)
      javaBin <- javaBinPath
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

  def build(taskId: String, isWorksheet: Boolean, target: ScalaTarget, positionMapper: Option[PositionMapper]): BspTask[BuildOutput] = {
    log.trace(s"[$taskId - build] Starting build")
    println("Reloading")
    for {
      _ <- reloadWorkspace()
      _ = log.trace(s"[$taskId - build] Workspace reloaded")
      _ = println("Build target")
      buildTarget <- getFirstBuildTarget()
      _ = log.trace(s"[$taskId - build] Got build target")
      _ = println("Compile")
      compileResult <- compile(taskId, isWorksheet, buildTarget.id, positionMapper)
      _ = log.trace(s"[$taskId - build] Compilation done")
      _ = println("Get jvm")
      jvmRunEnvironment <- getJvmRunEnvironment(buildTarget.id)
      _ = log.trace(s"[$taskId - build] Got JVM environment")
      _ = println("Create process")
      process <- makeProcess(jvmRunEnvironment, buildTarget, isWorksheet)
      finalDiags = diagnostics.getAndSet(Nil)
      _ = log.trace(s"[$taskId - build] Build complete, final diagnostics: ${finalDiags.size}")
    } yield BuildOutput(process, finalDiags.map(diagnosticToProblem(isWorksheet, positionMapper)))
  }

  // Kills the BSP connection and makes this object
  // un-usable.
  def end() = {
    Process(BspClient.scalaCliExec ++ Seq("--power", "bloop", "exit")).!
    try {
      log.info("Sending buildShutdown.")
      bspServer.buildShutdown().get(30, TimeUnit.SECONDS)
      log.info("buildShutdown finished.")
    } catch {
      case NonFatal(e) =>
        log.error(s"Ignoring $e while shutting down BSP server")
    } finally {
      log.info("Process finalisation has started.")
      bspServer.onBuildExit()
      log.info("Graceful process destruction requested.")
      scalaCliServer.destroy()
      log.info("Forceful process destruction requested.")
      scalaCliServer.destroyForcibly()
    }

    try {
      log.info("Awaiting process termination confirmation.")
      scalaCliServer.onExit().get(30, TimeUnit.SECONDS)
      log.info("Process successfully terminated.")
    } catch {
      case NonFatal(e) =>
        log.error(s"Ignoring $e while shutting down BSP server")
    } finally {
      if (scalaCliServer.isAlive()) {
        log.error("Destroying the process forcefully.")
        val pid = scalaCliServer.pid()
        Process(s"kill -9 $pid").!
      }

      log.info("Closing streams.")

      bspIn.close()
      bspOut.close()
      bspErr.close()

      log.info("Stopping listening thread.")
      listening.cancel(true)
    }
  }

  class InnerClient extends BuildClient {
    def onBuildPublishDiagnostics(params: PublishDiagnosticsParams): Unit = {
      log.debug(s"PublishDiagnosticsParams: $params")
      val incomingDiagnostics = Option(params.getDiagnostics()).fold(List.empty[Diagnostic])(_.asScala.toList)
      val reset = params.getReset()
      val beforeCount = diagnostics.get().size

      log.trace(s"[BspClient:onBuildPublishDiagnostics] reset=$reset, incoming=${incomingDiagnostics.size}, before=$beforeCount")

      if (reset) diagnostics.set(incomingDiagnostics)
      else diagnostics.getAndUpdate(_ ++ incomingDiagnostics)

      val afterCount = diagnostics.get().size
      log.trace(s"[BspClient:onBuildPublishDiagnostics] after=$afterCount")
    }
    def onBuildLogMessage(params: LogMessageParams): Unit = log.debug(s"LogMessageParams: $params")
    def onBuildShowMessage(params: ShowMessageParams): Unit =  log.debug(s"ShowMessageParams: $params")
    def onBuildTargetDidChange(params: DidChangeBuildTarget): Unit =  log.debug(s"DidChangeBuildTarget: $params")
    def onBuildTaskFinish(params: TaskFinishParams): Unit =  log.debug(s"TaskFinishParams: $params")
    def onBuildTaskProgress(params: TaskProgressParams): Unit = log.debug(s"TaskProgressParams: $params")
    def onBuildTaskStart(params: TaskStartParams): Unit = log.debug(s"TaskStartParams: $params")
  }
}
