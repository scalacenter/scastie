import sbt._
import Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin.AutoImport._
import org.scalajs.sbtplugin.cross.CrossType

object ScalaJSHelper {
  def packageScalaJS(client: Project) = Seq(
    watchSources ++= (watchSources in client).value,
    // Pick fastOpt when developing and fullOpt when publishing
    resourceGenerators in Compile += Def.task {
      val target = (classDirectory in Compile).value / "public"
      val jsdeps = (packageJSDependencies in (client, Compile)).value
      val (js, map) = andSourceMap((fastOptJS in (client, Compile)).value.data)
      IO.copy(
          Seq(
            js -> target / js.getName,
            map -> target / map.getName,
            jsdeps -> target / jsdeps.getName
          ))
        .toSeq
    }.taskValue,
    mappings in (Compile, packageBin) := {
      val mappingExcludingNonOptimized =
        (mappings in (Compile, packageBin)).value.filterNot {
          case (f, r) =>
            f.getName.endsWith("-fastopt.js") ||
              f.getName.endsWith("js.map")
        }

      val optimized = {
        val (js, map) =
          andSourceMap((fullOptJS in (client, Compile)).value.data)
        Seq(
          js -> js.getName,
          map -> map.getName
        )
      }

      mappingExcludingNonOptimized ++ optimized
    }
  )

  private def andSourceMap(aFile: java.io.File) =
    aFile -> file(aFile.getAbsolutePath + ".map")
}
