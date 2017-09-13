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
      products in Compile += Def.task {
        webpackOutputDir.value
      }.dependsOn(webpack in (client, Compile, fullOptJS)).value
    )
  }
}
