import sbt._
import java.io._

import com.olegych.scastie.api._

class GenerateProjects(base: File) {
  val root: File = base / "projects"

  def write: Unit = {

  }

  def projects: List[GeneratedProject] = {
    Nil

    // lazy val runtimeScala210JS = runtimeScala210.js
    // lazy val runtimeScala210JVM = runtimeScala210.jvm
    // lazy val runtimeScala211JS = runtimeScala211.js
    // lazy val runtimeScala211JVM = runtimeScala211.jvm
    // lazy val runtimeScalaJS = runtimeScalaCurrent.js
    // lazy val runtimeScalaJVM = runtimeScalaCurrent.jvm
    // lazy val runtimeScala213JS = runtimeScala213.js
    // lazy val runtimeScala213JVM = runtimeScala213.jvm
  }
}

class GeneratedProject {
  def runCmd: String = {
    ""
  }
}