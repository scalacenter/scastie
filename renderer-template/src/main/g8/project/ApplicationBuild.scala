import java.io.FilePermission
import java.lang.reflect.Method
import java.security.Permission
import sbt._
import classpath.ClasspathUtilities
import sbt.Keys._
import scala.Some

object ApplicationBuild extends Build {
  val rendererWorker = Project(id = "rendererWorker", base = file("."), settings = Defaults.defaultSettings ++ Seq(
    runner in(Compile, run) <<= (taskTemporaryDirectory, scalaInstance) map {
      (nativeTmp, instance) => new Run(instance, false, nativeTmp) {
        override def run(mainClass: String, classpath: Seq[File], options: Seq[String], log: Logger) = {
          log.info("Running " + mainClass + " " + options.mkString(" "))

          def execute = try run0(mainClass, classpath, options, log) catch {
            case e: java.lang.reflect.InvocationTargetException => throw e.getCause
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
            ScriptSM.hardenPermissions {
              main.invoke(null, options.toArray[String])
            }
          } finally {
            currentThread.setContextClassLoader(oldLoader)
          }
        }
      }
    }
  ))
}

object ScriptSM extends SecurityManager {
  System.setProperty("actors.enableForkJoin", false + "")
  val sm = System.getSecurityManager
  var activated = false

  override def checkPermission(perm: Permission) {
    if (activated) {
      val read = perm.getActions == ("read")
      val allowedMethods = Seq("accessDeclaredMembers", "suppressAccessChecks", "createClassLoader",
        "accessClassInPackage.sun.reflect", "getStackTrace").contains(perm.getName)
      val file = perm.isInstanceOf[FilePermission]
      val allowedFiles = Seq(".class", ".jar", "classes", "library.properties")
      //      can't use closures because will get java.lang.ClassCircularityError: ScriptSM
      //      val isClass = allowedFiles.exists(perm.getName.endsWith)
      val isClass = {
        val iterator = allowedFiles.iterator
        var result = false
        while (!result && iterator.hasNext) {
          result |= perm.getName.endsWith(iterator.next())
        }
        result
      }
      val readClass = file && isClass && read
      val allow = readClass || (read && !file) || allowedMethods
      if (!allow) {
        throw new SecurityException(perm.toString)
      }
    } else {
      if (sm != null) {
        sm.checkPermission(perm)
      }
    }

  }

  def deactivate {
    activated = false
    System.setSecurityManager(sm)
  }

  def activate {
    System.setSecurityManager(this)
    activated = true
  }

  def hardenPermissions[T](f: => T): T = this.synchronized {
    try {
      activate
      f
    } finally {
      deactivate
    }
  }
}
