package com.olegych.scastie

import java.security.Permission
import java.io.FilePermission

/**
  */
object ScriptSecurityManager extends SecurityManager {
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
      //      can't use closures because will get java.lang.ClassCircularityError: ScriptSecurityManager
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
