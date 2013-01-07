import giter8.Plugin._
import sbt._
import sbt.Keys._

object RendererTemplateBuild extends Build {
  def g8Scoped[T](key: TaskKey[T]) = key in G8Keys.g8 in Compile
  def g8Scoped[T](key: SettingKey[T]) = key in G8Keys.g8 in Compile
  val rendererTemplate = {
    Project("rendererTemplate", file("."),
      settings = Defaults.defaultSettings ++ giter8Settings ++ Seq(
        g8Scoped(G8Keys.properties) <<=
            (g8Scoped(unmanagedSourceDirectories), g8Scoped(sources)) map { (base, srcs) =>
              val files = srcs.collect {
                case file if file.getName != "build.sbt" => file.getName.replaceAllLiterally("$", "\\$")
              }
              Map(("name", "helloname")).updated("verbatim", files.mkString(" "))
            }
        , shellPrompt := (_ => ">\n") //to be able to detect prompt
        , traceLevel := 1000
      ))
  }
}

