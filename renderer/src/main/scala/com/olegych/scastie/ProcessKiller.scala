package com.olegych.scastie

import org.apache.commons.lang3.SystemUtils

/**
  */
trait ProcessKiller {
  def kill(p: Process): Boolean
}

object ProcessKiller {
  val instance = if (SystemUtils.IS_OS_WINDOWS) new WindowsProcessKiller else new UnixProcessKiller
}

class WindowsProcessKiller extends ProcessKiller {
  def kill(p: Process) = {
    val handleField = p.getClass.getDeclaredField("handle")
    handleField.setAccessible(true)
    //todo convert handle to pid
    //    val pid = handleField.get(p).asInstanceOf[Long]
    //    import sys.process._
    //    s"TASKKILL /F /T /PID $pid".! == 0
    p.destroy()
    true
  }
}

class UnixProcessKiller extends ProcessKiller {
  def kill(p: Process) = {
    val pidField = p.getClass.getDeclaredField("pid")
    pidField.setAccessible(true)
    val pid = pidField.get(p).asInstanceOf[Int]
    import sys.process._
    s"pkill -KILL -P $pid".! == 0
  }
}
