package com.olegych.scastie.util

object ProcessUtils {
  def getPid(process: Process): Int = {
    val pidField = process.getClass.getDeclaredField("pid")
    pidField.setAccessible(true)
    pidField.get(process).asInstanceOf[Int]
  }

  def pkill(process: Process): Unit = {
    val pid = getPid(process)

    import sys.process._
    val ret = s"kill $pid".!
    ()
  }
}
