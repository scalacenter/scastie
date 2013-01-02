package com.olegych.scastie

import java.security.{SecurityPermission, Permission}
import java.io.{File, FilePermission}

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
      val read = perm.getActions == ("read")
      val allowedMethods = Seq("accessDeclaredMembers", "suppressAccessChecks", "createClassLoader",
        "accessClassInPackage.sun.reflect", "getStackTrace").contains(perm.getName)
      val file = perm.isInstanceOf[FilePermission]
      val security = perm.isInstanceOf[SecurityPermission]

      deactivate
      val notExistingFile = !new File(perm.getName).exists()

      //g8 replaces \ with \
      val allowedFiles =
        Seq( """.*\.class""", """.*\.jar""", """.*classes.*""", """.*library\.properties""",
          """.*src/main/scala.*""", """.*/?target""")
      val isClass = allowedFiles.exists(perm.getName.replaceAll( """\""" + """\""", "/").matches(_))
      activate

      val readClass = file && isClass && read
      val readMissingFile = file && notExistingFile && read
      lazy val allowedClass = new Throwable().getStackTrace.exists { element =>
        val name = element.getFileName
        //todo apply more robust checks
        List("BytecodeWriters.scala", "Settings.scala", "PathResolver.scala").contains(name)
      }

      val allow = readMissingFile || readClass || (read && !file) || allowedMethods ||
          (security && perm.getName.startsWith("getProperty.")) || allowedClass
      if (!allow) {
        throw new SecurityException(perm.toString)
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
    if (System.getSecurityManager == this) sm.foreach(System.setSecurityManager(_))
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
