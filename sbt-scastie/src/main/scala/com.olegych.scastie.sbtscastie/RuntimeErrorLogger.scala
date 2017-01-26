import sbt._
import Keys._

import sbtapi._

import java.io.{PrintWriter, OutputStream, StringWriter}
import upickle.default.{write => uwrite}

object RuntimeErrorLogger {
  private object NoOp {
    def apply(): NoOp = {     
      def out(in: String): Unit = println(uwrite(SbtOutput(in.trim)))

      new NoOp(new OutputStream{
        override def close(): Unit = ()
        override def flush(): Unit = ()
        override def write(b: Array[Byte]): Unit = 
          out(new String(b))
        override def write(b: Array[Byte], off: Int, len: Int): Unit =
          out(new String(b, off, len))
        override def write(b: Int): Unit = ()
      })
    }
  }
  private class NoOp(os: OutputStream) extends PrintWriter(os)

  val settings: Seq[sbt.Def.Setting[_]] = Seq(
    extraLoggers := {
      val clientLogger = FullLogger {
        new Logger {
          def log(level: Level.Value, message: => String): Unit = ()
          def success(message: => String): Unit = () // << this is never called
          def trace(t: => Throwable): Unit = {
            def search(e: Throwable) = {
              e.getStackTrace
               .find(trace => trace.getFileName == "main.scala" && trace.getLineNumber != -1)
               .map(v ⇒ (e, Some(v.getLineNumber)))
            }
            def loop(e: Throwable): Option[(Throwable, Option[Int])] = {
              val s = search(e)
              if(s.isEmpty)
                if(e.getCause != null) loop(e.getCause)
                else Some((e, None))
              else s
            }

            // Nonzero exit code: 1
            val sbtTrap =
              t.isInstanceOf[RuntimeException] &&
              t.getMessage == "Nonzero exit code: 1" &&
              !t.getStackTrace.exists(e => e.getClassName == "sbt.Run" && e.getMethodName == "invokeMain")

            if(!sbtTrap) {
              loop(t).map{ case (err, line) ⇒
                val errors = new StringWriter()
                t.printStackTrace(new PrintWriter(errors))
                val fullStack = errors.toString()

                println(uwrite(RuntimeError(err.toString, line, fullStack)))
              }
            }
          }
        }
      }
      // val currentFunction = extraLoggers.value
      (key: ScopedKey[_]) => Seq(clientLogger)
    },
    showSuccess := false,
    logManager := LogManager.defaults(
      extraLoggers.value,
      ConsoleOut.printWriterOut(NoOp())
    )
  )
}
