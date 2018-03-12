package com.olegych.scastie.util

import akka.contrib.process.NonBlockingProcess
import java.nio.file.{Path, Paths}
import java.io.File
import scala.collection.immutable

import akka.actor.Props

object NonBlockingProcessPkill {
  def props(command: immutable.Seq[String],
            workingDir: File = Paths.get(System.getProperty("user.dir")).toFile,
            environment: Map[String, String] = Map.empty) =
    Props(new NonBlockingProcessPkill(command, workingDir.toPath, environment))
}

class NonBlockingProcessPkill(command: immutable.Seq[String],
                              workingDir: Path,
                              environment: Map[String, String])
    extends NonBlockingProcess(command, workingDir, environment) {
  override def postStop(): Unit = {
    // https://github.com/typesafehub/akka-contrib-extra/issues/72

    import sys.process._
    s"pkill -KILL -P ${process.getPID}".!

    super.postStop()
  }
}
