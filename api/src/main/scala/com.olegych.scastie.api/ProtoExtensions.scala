package com.olegych.scastie.api

import com.olegych.scastie.proto._

object PlainScala {
  def unapply(scalaTarget: ScalaTarget): Option[String]  = {
    scalaTarget.value match {
      case ScalaTarget.Value.WrapPlainScala(ScalaTarget.PlainScala(scalaVersion)) =>
        Some(scalaVersion)
      case _ => None
    }
  }
}

object TypelevelScala {
  def unapply(scalaTarget: ScalaTarget): Option[String]  = {
    scalaTarget.value match {
      case ScalaTarget.Value.WrapTypelevelScala(ScalaTarget.TypelevelScala(scalaVersion)) =>
        Some(scalaVersion)
      case _ => None
    }
  }
}

object Dotty {
  def unapply(scalaTarget: ScalaTarget): Option[String]  = {
    scalaTarget.value match {
      case ScalaTarget.Value.WrapDotty(ScalaTarget.Dotty(dottyVersion)) =>
        Some(dottyVersion)
      case _ => None
    }
  }
}

object ScalaJs {
  val targetFilename = "fastopt.js"
  val sourceMapFilename: String = targetFilename + ".map"
  val sourceFilename = "main.scala"
  val sourceUUID = "file:///tmp/LxvjvKARSa2U5ctNis9LIA"
  
  def unapply(scalaTarget: ScalaTarget): Option[(String, String)]  = {
    scalaTarget.value match {
      case ScalaTarget.Value.WrapScalaJs(ScalaTarget.ScalaJs(scalaVersion, scalaJsVersion)) =>
        Some((scalaVersion, scalaJsVersion))
      case _ => None
    }
  }
}