package org.scastie.runner

import java.lang.reflect.InvocationTargetException

object Runner {
  def main(args: Array[String]): Unit = {
    assert(args.nonEmpty)
    val mainClass = args.head
    val colored = args.tail.head.toBoolean
    val args0     = args.drop(2)

    val loader = Thread.currentThread().getContextClassLoader
    val cls    = loader.loadClass(mainClass)
    val method = cls.getMethod("main", classOf[Array[String]])
    try method.invoke(null, args0)
    catch {
      case e: InvocationTargetException if e.getCause != null =>
        val printer = StackTracePrinter(
          loader = loader,
          callerClass = Some(getClass.getName),
          cutInvoke = true,
          colored = colored
        )
        printer.printException(e.getCause)
        System.exit(1)
    }
  }
}
