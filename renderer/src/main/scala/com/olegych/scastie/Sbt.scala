package com.olegych.scastie

import collection.JavaConverters.asJavaIterableConverter
import java.io.File

/**
  */
case class Sbt(dir: String) {

  import java.io

  val process = start

  var fout: io.InputStream = _
  var ferr: io.InputStream = _
  var fin: io.OutputStream = _

  import scalax.io.JavaConverters._

  //can't use .lines since it eats all input
  lazy val output = fout.asUnmanagedInput.bytes
  lazy val input = fin.asUnmanagedOutput

  def start = {
    import scala.sys.process._
    val sbtCommand = if (org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS) "xsbt.cmd " else "./xsbt.sh "
    //race condition here
//    val process = Process(sbtCommand, new io.File(dir)).run(new ProcessIO(fin = _, fout = _, ferr = _))
//    while (fin == null || fout == null || ferr == null) {
//      Thread.sleep(100)
//    }
//    waitForPrompt
//    process
    null.asInstanceOf[sys.process.Process]
  }

  val start1 = new java.lang.ProcessBuilder("xsbt.cmd").directory(new File(dir)).start()
  fin = start1.getOutputStream
  fout = start1.getInputStream

  def process(command: String): String = {
    import collection.JavaConversions._
    (command + "\r\n").foreach(c => fin.write(c.toInt))
    (command + "\r\n").foreach(c => fin.write(c.toInt))
    fin.flush()
//    fin.close()
    waitForPrompt
  }

  def waitForPrompt: String = {
    val lines = Stream.continually {
      val string = Stream.continually(fout.read()).takeWhile(_ != 10.toByte).map(_.toChar).mkString
      println(string)
      string
    }
    lines.takeWhile(_ != ">").mkString("\n")
  }
  def close() {
    process.destroy()
  }
}
