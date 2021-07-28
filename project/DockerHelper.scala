import sbt._
import sbt.Keys._
import SbtShared._
import com.typesafe.sbt.SbtNativePackager
import com.typesafe.sbt.SbtNativePackager.Universal
import com.typesafe.sbt.packager.universal.UniversalPlugin
import com.typesafe.sbt.packager.Keys.{bashScriptExtraDefines, executableScriptName, stage}
import com.typesafe.sbt.packager.archetypes.scripts.BashStartScriptPlugin
import sbtdocker.DockerPlugin
import sbtdocker.DockerPlugin.autoImport.{ImageName, docker, imageNames}
import sbtdocker.immutable.Dockerfile
import sbtdocker.Instructions.Run

object DockerHelper extends AutoPlugin {
  override def requires = SbtNativePackager && UniversalPlugin && DockerPlugin && BashStartScriptPlugin
  override def trigger = allRequirements
  object autoImport {
    val dockerImageName = settingKey[String]("docker image name")
  }
  import autoImport._

  private val dockerOrg = "scalacenter"
  private val appDir = "/app"
  private val username = "scastie"
  private val uid = 433
  private val chown = s"$uid:$uid"
  private val userHome = s"/home/$username"

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    docker / imageNames := Seq(
      ImageName(
        namespace = Some(dockerOrg),
        repository = dockerImageName.value,
        tag = Some(gitHashNow)
      )
    ),
    // https://www.scala-sbt.org/sbt-native-packager/archetypes/cheatsheet.html#extra-defines
    bashScriptExtraDefines += s"""addJava "-Dlogback.configurationFile=$appDir/conf/logback.xml"""",
    Universal / mappings += (
      (ThisBuild / baseDirectory).value / "deployment" / "logback.xml" -> "conf/logback.xml"
    ),
  )

  private def baseDockerfile(fromImg: String, stageDir: File): Dockerfile =
    Dockerfile.empty
      .from(fromImg)
      .runRaw(s"""\\
        groupadd -g $uid $username && \\
        useradd -md $userHome -g $username -u $uid -s /bin/sh $username""")
      .env(
        "LANG" -> "en_US.UTF-8",
        "HOME" -> userHome
      )
      .copy(stageDir, appDir, chown)

  private def entrypoint = Def.setting {
    s"$appDir/bin/${executableScriptName.value}"
  }

  def serverDockerfile(): Def.Initialize[Task[Dockerfile]] = Def.task {
    baseDockerfile("adoptopenjdk:8u292-b10-jre-hotspot", stage.value)
      .user(username)
      .workDir(appDir)
      .env("DATA_DIR" -> s"$appDir/data")
      .volume(s"$appDir/data")
      .entryPoint(entrypoint.value)
  }

  def runnerDockerfile(sbtScastie: Project): Def.Initialize[Task[Dockerfile]] = Def.task {
    val sbtTargetDir = target.value
    val ivyHome = ivyPaths.value.ivyHome.get.toPath
    val org = organization.value
    val sbtScastieModuleName = (sbtScastie / moduleName).value

    val generatedProjects = new GenerateProjects(sbtTargetDir.toPath)
    generatedProjects.generateSbtProjects()

    val ivyLocalTemp = sbtTargetDir / "ivy"
    sbt.IO.delete(ivyLocalTemp)

    /*
      sbt-scastie / scala_2.10 / sbt_0.13 / 0.25.0
      api_2.11    / 0.25.0

          0             1           2         3
     */
    CopyRecursively(
      source = ivyHome.resolve(s"local/$org"),
      destination = ivyLocalTemp.toPath,
      directoryFilter = { (dir, depth) =>
        lazy val isSbtScastiePath = dir.getName(0).toString == sbtScastieModuleName
        lazy val dirName = dir.getFileName.toString

        if (depth == 1) {
          dirName == versionNow || dirName == versionRuntime || isSbtScastiePath
        } else if (depth == 3 && isSbtScastiePath) {
          dirName == versionNow || dirName == versionRuntime
        } else {
          true
        }
      }
    )

    val dest = s"$userHome/projects"

    baseDockerfile("adoptopenjdk:8u292-b10-jdk-hotspot", stage.value)
      .runRaw(s"""\\
        mkdir $appDir/sbt && \\
        curl -Lo /tmp/sbt-${distSbtVersion}.tgz \\
          https://github.com/sbt/sbt/releases/download/v${distSbtVersion}/sbt-${distSbtVersion}.tgz && \\
        tar -xzvf /tmp/sbt-$distSbtVersion.tgz -C $appDir/sbt && \\
        ln -s $appDir/sbt/sbt/bin/sbt /usr/local/bin/sbt && \\
        mkdir $userHome/.sbt && chown $chown $userHome/.sbt
        """)
      .user(username)
      .workDir(userHome)
      .copy(generatedProjects.projectTarget.toFile, dest, chown)
      .copy(ivyLocalTemp, s"$userHome/.ivy2/local/$org", chown)
      // comment the `addInstructions` below to speedup sbtRunner/ docker task when testing deploy* tasks
      .addInstructions(
        generatedProjects.projects.map(p => Run(p.runCmd(dest)))
      )
      .entryPoint(entrypoint.value, "-J-Xmx512M", "-J-Xms512M")
  }
}
