package com.olegych.scastie.sbt

import com.olegych.scastie.util.ScastieFileUtil.{slurp, write}
import com.olegych.scastie.util.ProcessUtils
import com.olegych.scastie.api._

import com.olegych.scastie.buildinfo.BuildInfo.sbtVersion

import scala.util.Random
import System.{lineSeparator => nl}

import org.slf4j.LoggerFactory
import java.nio.file._
import java.io.{BufferedReader, IOException, InputStreamReader}
import java.nio.charset.StandardCharsets

class Sbt(defaultInputs: Inputs, javaOptions: Seq[String]) {

  private val log = LoggerFactory.getLogger(getClass)

  private val uniqueId = Random.alphanumeric.take(10).mkString

  val sbtDir: Path = Files.createTempDirectory("scastie")
  private val buildFile = sbtDir.resolve("build.sbt")
  private val prompt = s"""shellPrompt := (_ => "$uniqueId\\n")"""

  private val projectDir = sbtDir.resolve("project")
  Files.createDirectories(projectDir)

  log.info(s"sbtVersion: $sbtVersion")

  write(projectDir.resolve("build.properties"), s"sbt.version = $sbtVersion")

  private val pluginFile = projectDir.resolve("plugins.sbt")

  private val codeFile = sbtDir.resolve("src/main/scala/main.scala")
  Files.createDirectories(codeFile.getParent)

  def scalaJsContent(): Option[String] = {
    slurp(sbtDir.resolve(ScalaTarget.Js.targetFilename))
  }

  def scalaJsSourceMapContent(): Option[String] = {
    slurp(sbtDir.resolve(ScalaTarget.Js.sourceMapFilename))
  }

  private var currentInputs = defaultInputs
  setInputs(defaultInputs)

  private val (process, fin, fout) = {
    log.info("Starting sbt process")
    val builder =
      new ProcessBuilder("sbt")
        .directory(sbtDir.toFile)
        .redirectErrorStream(true)

    builder
      .environment()
      .put(
        "SBT_OPTS",
        (javaOptions ++ Seq(
          "-Djline.terminal=jline.UnsupportedTerminal",
          "-Dsbt.log.noformat=true"
        )).mkString(" ")
      )

    val process = builder.start()

    val in = new BufferedReader(
      new InputStreamReader(process.getInputStream, StandardCharsets.UTF_8)
    )

    process.getOutputStream

    (process, process.getOutputStream, in)
  }

  def warmUp(): Unit = {
    log.info("warming up sbt")
    val Right(in) = SbtRunner.instrument(defaultInputs)
    eval("run", in, (line, _, _, _) => log.info(line), reload = false)
    log.info("warming up sbt done")
  }

  private def collect(
      lineCallback: (String, Boolean, Boolean, Boolean) => Unit,
      reload: Boolean
  ): Boolean = {
    val chars = new collection.mutable.Queue[Character]()
    var read = 0
    var done = false
    var sbtError = false

    while (read != -1 && !done) {

      read = fout.read()
      if (read == 10) {
        val line = chars.mkString

        println(line)

        val prompt = line == uniqueId
        sbtError = line == "[error] Type error in expression"
        done = prompt || sbtError

        lineCallback(line, done, sbtError, reload)
        chars.clear()
      } else {
        chars += read.toChar
      }
    }
    if (sbtError) {
      setInputs(defaultInputs)
      process("r", noop, reload = false)
    }

    sbtError
  }

  type LineCallback = (String, Boolean, Boolean, Boolean) => Unit
  val noop: LineCallback = (line, _, _, _) => log.info(line)

  collect(noop, reload = false)

  private def process(command: String,
                      lineCallback: LineCallback,
                      reload: Boolean): Boolean = {
    log.info("running: {}", command)

    try {
      fin.write((command + nl).getBytes)
      fin.flush()
      collect(lineCallback, reload)
    } catch {
      case e: IOException =>
        // when the snippet is pkilled (timeout) the sbt output stream is closed
        if (e.getMessage == "Stream closed") true
        else throw e
    }

  }

  def kill(): Unit = {
    ProcessUtils.kill(process)
  }

  def needsReload(inputs: Inputs): Boolean =
    currentInputs.needsReload(inputs)

  def exit(): Unit = {
    process("exit", noop, reload = false)
    ()
  }

  private def writeFile(path: Path, content: String): Unit = {
    if (Files.exists(path)) {
      Files.delete(path)
    }

    Files.write(path, content.getBytes, StandardOpenOption.CREATE_NEW)

    ()
  }

  private def setInputs(inputs: Inputs): Unit = {
    writeFile(pluginFile, inputs.sbtPluginsConfig + nl)
    writeFile(buildFile, prompt + nl + inputs.sbtConfig)
    currentInputs = inputs
  }

  def eval(command: String,
           inputs: Inputs,
           lineCallback: LineCallback,
           reload: Boolean): Boolean = {

    val isReloading = needsReload(inputs)

    setInputs(inputs)

    val reloadError =
      if (isReloading) process("reload", lineCallback, reload = true)
      else false

    if (!reloadError) {
      write(codeFile, inputs.code, truncate = true)
      process(command, lineCallback, reload)
    }

    reloadError
  }
}
