import sbt._
import Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin.AutoImport._
import org.scalajs.sbtplugin.cross.CrossType

import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport._

import spray.revolver.RevolverPlugin.autoImport._

object ScalaJSHelper {

  def packageScalaJS(client: Project) = {
    def webpackOutputDir = Def.task {
      webpackDir.value / "out"
    }

    def webpackDir = Def.task {
      ((crossTarget in (client, Compile, npmUpdate)).value)
    }

    Seq(
      watchSources ++= (watchSources in client).value,
      mappings in (Compile, packageBin) := {
        val webpackOut = webpackOutputDir.value.toPath

        val runIt = (webpack in (client, Compile, fullOptJS)).value

        val optimized =
          (webpackOut.toFile.***.get)
            .map(path => (path, webpackOut.relativize(path.toPath).toString))
            .toSeq

        (mappings in (Compile, packageBin)).value ++
          optimized
      }
    )
  }
}
