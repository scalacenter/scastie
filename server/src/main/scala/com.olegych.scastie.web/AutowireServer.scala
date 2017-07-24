package com.olegych.scastie
package web

import upickle.default.{Reader, Writer, read => uread, write => uwrite}

object AutowireServer extends autowire.Server[String, Reader, Writer] {
  def read[Result: Reader](p: String): Result = uread[Result](p)
  def write[Result: Writer](r: Result): String = uwrite(r)
}