package com.olegych.scastie

import sbt._
import classpath.ClasspathUtilities
import java.lang.reflect.{Method, InvocationTargetException}

/**
  */
class SecuredRun(instance: ScalaInstance, trapExit: Boolean, nativeTmp: File)
    extends Run(instance, false, nativeTmp) {
  override def run(mainClass: String, classpath: Seq[File], options: Seq[String], log: Logger) = {
    log.info("Running " + mainClass + " " + options.mkString(" "))

    def execute = try run0(mainClass, classpath, options, log) catch {
      case e: InvocationTargetException => throw e.getCause
    }
    def directExecute = try {
      execute; None
    } catch {
      case e: Exception => log.trace(e); Some(e.toString)
    }
    directExecute
  }

  private def run0(mainClassName: String, classpath: Seq[File], options: Seq[String], log: Logger) {
    log.debug("  Classpath:\n\t" + classpath.mkString("\n\t"))
    val loader = ClasspathUtilities.makeLoader(classpath, instance.loader, instance, nativeTmp)
    val main = getMainMethod(mainClassName, loader)
    invokeMain(loader, main, options)
  }

  private def invokeMain(loader: ClassLoader, main: Method, options: Seq[String]) {
    val currentThread = Thread.currentThread
    val oldLoader = Thread.currentThread.getContextClassLoader
    currentThread.setContextClassLoader(loader)
    try {
      ScriptSecurityManager.hardenPermissions {
        main.invoke(null, options.toArray[String])
      }
    } finally {
      currentThread.setContextClassLoader(oldLoader)
    }
  }
}
