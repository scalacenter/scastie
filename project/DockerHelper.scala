import sbtdocker.DockerPlugin.autoImport._

import java.nio.file.Path

object DockerHelper {
  def apply(baseDirectory: Path,
            sbtTargetDir: Path,
            sbtScastie: String,
            ivyHome: Path,
            organization: String,
            artifact: Path,
            sbtVersion: String): Dockerfile = {

    val artifactTargetPath = s"/app/${artifact.getFileName()}"
    val generatedProjects = new GenerateProjects(sbtTargetDir)
    generatedProjects.generateSbtProjects()

    val logbackConfDestination = "/home/scastie/logback.xml"

    val ivyLocalTemp = sbtTargetDir.resolve("ivy")

    sbt.IO.delete(ivyLocalTemp.toFile)

    /*
      sbt-scastie / scala_2.10 / sbt_0.13 / 0.25.0
      api_2.11    / 0.25.0

          0             1           2         3
     */

    CopyRecursively(
      source = ivyHome.resolve(s"local/$organization"),
      destination = ivyLocalTemp,
      directoryFilter = { (dir, depth) =>
        lazy val isSbtScastiePath = dir.getName(0).toString == sbtScastie
        lazy val dirName = dir.getFileName.toString

        if (depth == 1) {
          dirName == SbtShared.versionNow || dirName == SbtShared.versionRuntime || isSbtScastiePath
        } else if (depth == 3 && isSbtScastiePath) {
          dirName == SbtShared.versionNow || dirName == SbtShared.versionRuntime
        } else {
          true
        }
      }
    )

    val containerUsername = "sbtRunnerContainer"

    val sbtGlobal = sbtTargetDir.resolve(".sbt")
    sbtGlobal.toFile.mkdirs()

    new Dockerfile {
      from("openjdk:17-alpine")

      // Install ca-certificates for wget https
      runRaw("apk update")
      runRaw("apk --update upgrade")
      runRaw("apk add ca-certificates")
      runRaw("update-ca-certificates")
      runRaw("apk add openssl")
      runRaw("apk add nss")
      runRaw("apk add bash")

      runRaw("mkdir -p /app/sbt")

      runRaw(
        s"wget https://github.com/sbt/sbt/releases/download/v${sbtVersion}/sbt-${sbtVersion}.tgz -O /tmp/sbt-${sbtVersion}.tgz"
      )
      runRaw(s"tar -xzvf /tmp/sbt-$sbtVersion.tgz -C /app/sbt")

      runRaw("ln -s /app/sbt/sbt/bin/sbt /usr/local/bin/sbt")

      val userHome = s"/home/$containerUsername"

      runRaw(s"addgroup -g 433 $containerUsername")
      runRaw(
        s"adduser -h $userHome -G $containerUsername -D -u 433 -s /bin/sh $containerUsername"
      )

      def chown(dir: String) = {
        user("root")
        runRaw(s"chown -R $containerUsername:$containerUsername $userHome/$dir")
        user(containerUsername)
      }

      add(sbtGlobal.toFile, s"$userHome/.sbt")
      chown(".sbt")

      user(containerUsername)
      workDir(userHome)
      env("LANG", "en_US.UTF-8")
      env("HOME", userHome)

      val dest = s"$userHome/projects"
      add(generatedProjects.projectTarget.toFile, dest)
      chown("projects")

      add(ivyLocalTemp.toFile, s"$userHome/.ivy2/local/$organization")
      chown(".ivy2")

      generatedProjects.projects.foreach(
        generatedProject => runRaw(generatedProject.runCmd(dest))
      )

      add(artifact.toFile, artifactTargetPath)

      add(
        baseDirectory.resolve("deployment/logback.xml").toFile,
        logbackConfDestination
      )

      entryPoint(
        "java",
        "-Xmx512M",
        "-Xms512M",
        s"-Dlogback.configurationFile=$logbackConfDestination",
        "-jar",
        artifactTargetPath
      )
    }
  }
}
