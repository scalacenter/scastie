import com.typesafe.sbt.SbtNativePackager.Universal
import org.scalajs.sbtplugin.ScalaJSPlugin.AutoImport._
import sbt.Keys._
import sbt._
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport._
import spray.revolver.RevolverPlugin.autoImport._

object ScalaJsHelper {

  def packageScalaJS(client: Project): Seq[Setting[_]] = {
    Seq(
      watchSources ++= (watchSources in client).value,
      products in Compile += ((crossTarget in (client, Compile, npmUpdate)).value / "out"),
      reStart := reStart.dependsOn(webpack in (client, Compile, fastOptJS)).evaluated,
      packageBin in Universal := (packageBin in Universal).dependsOn(webpack in (client, Compile, fullOptJS)).value,
    )
  }
}
