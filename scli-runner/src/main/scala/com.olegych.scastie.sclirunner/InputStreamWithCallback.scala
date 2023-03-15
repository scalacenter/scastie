package com.olegych.scastie.sclirunner

import java.io.FilterInputStream
import java.io.InputStream

class InputStreamWithCallback(callback: String => Any, in: InputStream) extends FilterInputStream(in) {
  override def read(b: Array[Byte], off: Int, len: Int): Int = {
    val bytesRead = super.read(b, off, len)
    callback(new String(b))
    bytesRead
  }
}