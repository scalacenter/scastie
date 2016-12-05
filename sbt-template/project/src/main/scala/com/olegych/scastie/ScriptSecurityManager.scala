package com.olegych.scastie

import java.security.{SecurityPermission, Permission}
import java.io.{File, FilePermission}
import java.util.PropertyPermission

/**
  */
object ScriptSecurityManager extends SecurityManager {
  System.setProperty("actors.enableForkJoin", false + "")

  private val lock = new Object
  @volatile private var sm: Option[SecurityManager] = None
  @volatile private var activated = false

  def hardenPermissions[T](f: => T): T = lock.synchronized {
    try {
      activate
      f
    } finally {
      deactivate
    }
  }

  override def checkPermission(perm: Permission) {
    if (activated) {
      val read = perm.getActions == "read"
      val readWrite = perm.getActions == "read,write"
      val allowedMethods = Seq(
        "accessDeclaredMembers", "suppressAccessChecks", "createClassLoader",
        "accessClassInPackage.sun.reflect", "getStackTrace", "getClassLoader"
      ).contains(perm.getName)
      val getenv = perm.getName.startsWith("getenv")
      val file = perm.isInstanceOf[FilePermission]
      val property = perm.isInstanceOf[PropertyPermission]
      val security = perm.isInstanceOf[SecurityPermission]

      deactivate
      val notExistingFile = !new File(perm.getName).exists()

      val allowedFiles =
        Seq( """.*\.class""", """.*\.jar""", """.*classes.*""", """.*\.properties""",
          """.*src/main/scala.*""", """.*/?target""")
      val isClass = allowedFiles.exists(perm.getName.replaceAll( """\""" + """\""", "/").matches)
      activate

      val readClass = file && isClass && read
      val readMissingFile = file && notExistingFile && read
      lazy val allowedClass = new Throwable().getStackTrace.exists { element =>
        val name = element.getFileName
        //todo apply more robust checks
        List("BytecodeWriters.scala", "Settings.scala", "PathResolver.scala", "JavaMirrors.scala", "Using.scala", ".*.java")
            .exists(_.r.findFirstMatchIn(name).isDefined)
      }

      val allow = readMissingFile || readClass || (read && !file) || allowedMethods || getenv ||
          (property && readWrite) || (security && perm.getName.startsWith("getProperty.")) || allowedClass
      if (!allow) {
        val exception = new SecurityException(perm.toString)
        exception.printStackTrace()
        throw exception
      }
    } else {
      //don't use closures here to avoid SOE
      if (sm.isDefined && sm.get != this) {
        sm.get.checkPermission(perm)
      }
    }

  }

  private def deactivate {
    activated = false
    if (System.getSecurityManager == this) sm.foreach(System.setSecurityManager)
  }

  private def activate {
    val manager = System.getSecurityManager
    if (manager != this) {
      sm = Option(manager)
      System.setSecurityManager(this)
    }
    activated = true
  }
}
