import sbt._
import sbt.Keys._
import com.olegych.scastie.SecuredRun

object ApplicationBuild extends Build {
  val rendererWorker = Project(id = "rendererWorker", base = file("."),
    settings = Defaults.defaultSettings ++ Seq(
      runner in(Compile, run) <<= (taskTemporaryDirectory, scalaInstance) map { (nativeTmp, instance) =>
        new SecuredRun(instance, false, nativeTmp)
      }
    ))
}