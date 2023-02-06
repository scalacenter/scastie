package com.olegych.scastie.storage

import com.olegych.scastie.api._
import com.olegych.scastie.instrumentation.Instrument
import com.olegych.scastie.util.Base64UUID

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters

import java.nio.file.{Files, Path, Paths}
import scala.concurrent.{ExecutionContext, Future}


trait SnippetsContainer {
  protected implicit val ec: ExecutionContext

  def appendOutput(progress: SnippetProgress): Future[Unit]
  def deleteAll(snippetId: SnippetId): Future[Boolean] = {
    def deleteUpdate(update: Int): Future[Boolean] = {
      val updateSnippetId = snippetId.copy(user = snippetId.user.map(_.copy(update = update)))
      for {
        read <- readSnippet(updateSnippetId)
        result <- read match {
          case Some(read) =>
            for {
              result <- delete(updateSnippetId)
              resultNext <- deleteUpdate(update + 1)
            } yield result || resultNext
          case None => Future.successful(false)
        }
      } yield result
    }
    deleteUpdate(0)
  }
  protected def delete(snippetId: SnippetId): Future[Boolean]
  def removeUserSnippets(user: UserLogin): Future[Boolean] = {
    listSnippets(user).flatMap(snippets => {
      Future.sequence(
        snippets
          .map(snippet => deleteAll(snippet.snippetId)))
          .map(_.fold(true)(_ && _)
      )
    })
  }
  def listSnippets(user: UserLogin): Future[List[SnippetSummary]]
  def readOldSnippet(id: Int): Future[Option[FetchResult]]
  def readScalaJs(snippetId: SnippetId): Future[Option[FetchResultScalaJs]]
  def readScalaJsSourceMap(
      snippetId: SnippetId
  ): Future[Option[FetchResultScalaJsSourceMap]]
  def readSnippet(snippetId: SnippetId): Future[Option[FetchResult]]
  protected def insert(snippetId: SnippetId, inputs: Inputs): Future[Unit]
  protected def hideFromUserProfile(snippetId: SnippetId): Future[Unit]

  private def insert0(snippetId: SnippetId, inputs: Inputs): Future[SnippetId] =
    insert(snippetId, inputs).map(_ => snippetId)

  final def create(inputs: Inputs, user: Option[UserLogin]): Future[SnippetId] = {
    insert0(newSnippetId(user), inputs)
  }

  final def save(inputs: Inputs, user: Option[UserLogin]): Future[SnippetId] =
    create(inputs.copy(isShowingInUserProfile = true), user)

  final def update(snippetId: SnippetId, inputs: Inputs): Future[Option[SnippetId]] = {
    updateSnippetId(snippetId).flatMap {
      case Some(nextSnippetId) =>
        for {
          r <- insert0(nextSnippetId, inputs.copy(forked = Some(snippetId), isShowingInUserProfile = true))
          _ <- hideFromUserProfile(snippetId)
        } yield Some(r)
      case _ => Future.successful(None)
    }
  }

  final def fork(snippetId: SnippetId, inputs: Inputs, user: Option[UserLogin]): Future[SnippetId] =
    create(inputs.copy(forked = Some(snippetId), isShowingInUserProfile = true), user)

  final def readScalaSource(
      snippetId: SnippetId
  ): Future[Option[FetchResultScalaSource]] =
    readSnippet(snippetId).map(
      _.flatMap(
        snippet =>
          Instrument(snippet.inputs.code, snippet.inputs.target) match {
            case Right(instrumented) =>
              Some(FetchResultScalaSource(instrumented))
            case _ => None
        }
      )
    )

  final def downloadSnippet(snippetId: SnippetId): Future[Option[Path]] =
    readSnippet(snippetId).map(_.map(asZip(snippetId)))

  protected final def newSnippetId(user: Option[UserLogin]): SnippetId = {
    val uuid = Base64UUID.create
    SnippetId(uuid, user.map(u => SnippetUserPart(u.login)))
  }

  protected final def updateSnippetId(snippetId: SnippetId): Future[Option[SnippetId]] = {
    snippetId.user match {
      case Some(SnippetUserPart(login, lastUpdateId)) =>
        val nextSnippetId = SnippetId(
          snippetId.base64UUID,
          Some(
            SnippetUserPart(
              login,
              lastUpdateId + 1
            )
          )
        )
        readSnippet(nextSnippetId).flatMap {
          case Some(_) => updateSnippetId(nextSnippetId)
          case None    => Future.successful(Some(nextSnippetId))
        }
      case None => Future.successful(None)
    }
  }

  private val snippetZip = Files.createTempDirectory(null)

  private def asZip(snippetId: SnippetId)(snippet: FetchResult): Path = {
    import snippet.inputs

    val projectDir = snippetZip.resolve(snippetId.url)

    if (!Files.exists(projectDir)) {
      Files.createDirectories(projectDir)

      val buildFile = projectDir.resolve("build.sbt")
      Files.write(buildFile, inputs.sbtConfig.linesIterator.filterNot(_.contains("org.scastie")).mkString("\n").getBytes())

      val projectFile = projectDir.resolve("project/plugins.sbt")
      Files.createDirectories(projectFile.getParent)
      Files.write(projectFile, inputs.sbtPluginsConfig.linesIterator.filterNot(_.contains("org.scastie")).mkString("\n").getBytes())

      val codeFile = projectDir.resolve(s"src/main/scala/main.${if (inputs.isWorksheetMode) "sc" else "scala"}")
      Files.createDirectories(codeFile.getParent)
      Files.write(codeFile, inputs.code.getBytes)
      val buildPropsFile = projectDir.resolve("project/build.properties")
      Files.write(buildPropsFile, s"sbt.version=${com.olegych.scastie.buildinfo.BuildInfo.sbtVersion}".getBytes)
    }

    val zippedProjectDir = Paths.get(s"$projectDir.zip")
    if (!Files.exists(zippedProjectDir)) {
      new ZipFile(zippedProjectDir.toFile)
        .addFolder(projectDir.toFile, new ZipParameters())
    }

    zippedProjectDir
  }

  def close(): Unit = ()
}
