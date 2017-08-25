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
      products in Compile += {
        val runIt = (webpack in (client, Compile, fastOptJS)).value
        webpackOutputDir.value
      },
      mappings in (Compile, packageBin) := {
        val webpackOut = webpackOutputDir.value.toPath

        val mappingExcludingNonOptimized =
          (mappings in (Compile, packageBin)).value.filterNot {
            case (f, r) => f.toPath.startsWith(webpackOut)
          }

        val runIt = (webpack in (client, Compile, fullOptJS)).value

        val optimized =
          (webpackOut.toFile.***.get)
            .map(path => (path, webpackOut.relativize(path.toPath).toString))
            .toSeq

        mappingExcludingNonOptimized ++ optimized
      }
    )
  }
}
