package org.scastie.storage

import org.scastie.api._
import org.scastie.storage.filesystem.FilesystemContainer
import org.scastie.storage.mongodb.MongoDBContainer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.Executors
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random
import org.scastie.storage.postgres.PostgresContainer

class ContainerTest extends AnyFunSuite with BeforeAndAfterAll with OptionValues {
  val postgres = sys.props.get("SnippetsContainerTest.postgres").flatMap(_.toBooleanOption).contains(true)
  val mongo = sys.props.get("SnippetsContainerTest.mongo").flatMap(_.toBooleanOption).contains(true)

  val root = Files.createTempDirectory("test")
  val oldRoot = Files.createTempDirectory("old-test")

  val testContainer: SnippetsContainer = (postgres, mongo) match {
    case (true, true) =>
      println("ContainerTest cannot use both postgres and mongo at the same time (defaulting to postgres)")
      new PostgresContainer(defaultConfig = true)
    case (false, true) =>
      println("ContainerTest using mongo")
      new MongoDBContainer(defaultConfig = true)
    case (true, false) =>
      println("ContainerTest using postgres")
      new PostgresContainer(defaultConfig = true)
    case (false, false) =>
      println("ContainerTest using filesystem")
      new FilesystemContainer(root, oldRoot)(
        ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())
      )
  }

  override protected def afterAll(): Unit = {
    deleteRecursively(root)
    deleteRecursively(oldRoot)
    if (postgres || mongo) testContainer.close()
  }

  private implicit class FAwait[T](f: Future[T]) {
    def await = Await.result(f, Duration.Inf)
  }

  def deleteRecursively(base: Path): Unit = {
    Files.walkFileTree(
      base,
      new SimpleFileVisitor[Path] {
        override def postVisitDirectory(dir: Path, ex: IOException): FileVisitResult = {
          Files.delete(dir)
          FileVisitResult.CONTINUE
        }
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }
      }
    )
  }
  Seq(SbtInputs.default.withSavedConfig, ScalaCliInputs.default).map { inputType =>
    val typeName = inputType.getClass.getSimpleName.stripSuffix("$")

    test(s"[$typeName] create snippet with logged in user") {
      val bob = "bob"
      val snippetId =
        testContainer.create(inputType, user = Some(UserLogin(bob)))
      assert(snippetId.await.user.get.login == bob)
    }

    test(s"[$typeName] create snippet with anonymous user") {
      val container = testContainer
      val snippetId = container.create(inputType, user = None)
      assert(snippetId.await.user.isEmpty)
    }

    test(s"[$typeName] create then read") {
      val inputs = inputType
      val snippetId = testContainer.create(inputs, user = None).await
      val result = testContainer.readSnippet(snippetId).await

      assert(result.value.inputs == inputs)
    }

    test(s"[$typeName] fork") {
      val inputs =
        inputType.copyBaseInput(code = "source", isShowingInUserProfile = true)
      val snippetId = testContainer.save(inputs, user = None).await

      val forkedInputs =
        inputType.copyBaseInput(code = "forked", isShowingInUserProfile = true)
      val forkedSnippetId =
        testContainer.fork(snippetId, forkedInputs, user = None).await

      val forkedBis = testContainer.readSnippet(forkedSnippetId).await.get

      assert(forkedSnippetId != snippetId)
      assert(forkedBis.inputs.forked.get == snippetId)
    }

    test(s"[$typeName] update") {
      val user = UserLogin("github-user-update" + Random.nextInt())
      val inputs1 =
        inputType.copyBaseInput(code = "inputs1", isShowingInUserProfile = true)
      val snippetId1 = testContainer.save(inputs1, Some(user)).await
      assert(snippetId1.user.get.update == 0)

      val inputs2 =
        inputType.copyBaseInput(code = "inputs2", isShowingInUserProfile = true)
      val snippetId2 = testContainer.update(snippetId1, inputs2).await.get
      assert(snippetId2.user.get.update == 1, "we get a new update id")

      val readInputs1 = testContainer.readSnippet(snippetId1).await.get.inputs
      val readInputs2 = testContainer.readSnippet(snippetId2).await.get.inputs

      assert(readInputs1 == inputs1.copyBaseInput(isShowingInUserProfile = false), "we don't mutate previous input")
      assert(readInputs2 == inputs2.copyBaseInput(forked = Some(snippetId1)), "we update forked")

      val snippets = testContainer.listSnippets(user).await
      assert(snippets.size == 1 && snippets.head.snippetId == snippetId2, "we hide old version")
    }

    test(s"[$typeName] listSnippets") {
      val user = UserLogin("github-user-list" + Random.nextInt())
      val user2 = UserLogin("github-user-list2" + Random.nextInt())

      val inputs1 = inputType.copyBaseInput(code = "inputs1")
      testContainer.save(inputs1, Some(user)).await

      val inputs2 = inputType.copyBaseInput(code = "inputs2")
      testContainer.save(inputs2, Some(user)).await

      val inputs3 = inputType.copyBaseInput(code = "inputs3")
      testContainer.save(inputs3, Some(user)).await

      val user2inputs = inputType.copyBaseInput(code = "inputs3")
      testContainer.save(user2inputs, Some(user2)).await

      val inputs4 =
        inputType.copyBaseInput(code = "inputs4", isShowingInUserProfile = false)
      testContainer.create(inputs4, Some(user)).await

      val snippets = testContainer.listSnippets(user).await
      assert(
        snippets.map(_.summary).toSet == Set("inputs3", "inputs2", "inputs1")
      )
    }

    test(s"[$typeName] delete") {
      val user = UserLogin("github-user-delete" + Random.nextInt())

      val inputs1 = inputType.copyBaseInput(code = "inputs1")
      val snippetId1 = testContainer.save(inputs1, Some(user)).await

      val inputs1U = inputType.copyBaseInput(code = "inputs1 updated")
      testContainer.update(snippetId1, inputs1U).await.get

      val inputs2 = inputType.copyBaseInput(code = "inputs2")
      val snippetId2 = testContainer.save(inputs2, Some(user)).await

      val inputs2U = inputType.copyBaseInput(code = "inputs2 updated")
      val snippetId2U = testContainer.update(snippetId2, inputs2U).await.get

      assert(testContainer.listSnippets(user).await.size == 2)

      testContainer.deleteAll(snippetId2U).await

      assert(testContainer.readSnippet(snippetId2U).await == None)
      assert(testContainer.readSnippet(snippetId2).await == None)

      assert(testContainer.listSnippets(user).await.size == 1)
    }

    test(s"[$typeName] appendOutput") {
      val snippetId = testContainer.create(inputType, user = None).await
      val progress = SnippetProgress.default.copy(snippetId = Some(snippetId))
      testContainer.appendOutput(progress).await
      val result = testContainer.readSnippet(snippetId).await

      assert(result.value.progresses.headOption.value == progress, "we properly append output")
    }

    test(s"[$typeName] deleteAllSnippets") {
      val user = UserLogin("github-user-delete" + Random.nextInt())

      val inputs1 = inputType.copyBaseInput(code = "inputs1")
      val snippetId1 = testContainer.save(inputs1, Some(user)).await

      val inputs2 = inputType.copyBaseInput(code = "inputs2")
      val snippetId2 = testContainer.save(inputs2, Some(user)).await

      val inputs2U = inputType.copyBaseInput(code = "inputs2 updated")
      val snippetId2U = testContainer.update(snippetId2, inputs2U).await.get

      assert(testContainer.listSnippets(user).await.size == 2)

      testContainer.removeUserSnippets(user).await

      assert(testContainer.readSnippet(snippetId1).await == None)
      assert(testContainer.readSnippet(snippetId2U).await == None)
      assert(testContainer.readSnippet(snippetId2).await == None)

      assert(testContainer.listSnippets(user).await.size == 0)
    }

    test(s"[$typeName] readLatestSnippet returns latest version") {
      val user = UserLogin("github-user-latest" + Random.nextInt())

      val inputs1 = inputType.copyBaseInput(code = "version 1")
      val snippetId1 = testContainer.save(inputs1, Some(user)).await

      val inputs2 = inputType.copyBaseInput(code = "version 2")
      val snippetId2 = testContainer.update(snippetId1, inputs2).await.get

      val inputs3 = inputType.copyBaseInput(code = "version 3")
      val snippetId3 = testContainer.update(snippetId2, inputs3).await.get

      val latest = testContainer.readLatestSnippet(snippetId1).await.value

      assert(latest.inputs.code == "version 3", "should return latest version")
      assert(snippetId3.user.get.update == 2, "latest should be update 2")
    }

    test(s"[$typeName] readLatestSnippet with base snippet ID") {
      val user = UserLogin("github-user-latest-base" + Random.nextInt())

      val inputs1 = inputType.copyBaseInput(code = "first")
      val snippetId1 = testContainer.save(inputs1, Some(user)).await

      val inputs2 = inputType.copyBaseInput(code = "second")
      val snippetId2 = testContainer.update(snippetId1, inputs2).await.get

      val latestFromFirst = testContainer.readLatestSnippet(snippetId1).await.value
      val latestFromSecond = testContainer.readLatestSnippet(snippetId2).await.value

      assert(latestFromFirst.inputs.code == "second")
      assert(latestFromSecond.inputs.code == "second")
    }

    test(s"[$typeName] readLatestSnippet with anonymous user") {
      val inputs = inputType.copyBaseInput(code = "anonymous snippet")
      val snippetId = testContainer.save(inputs, None).await

      val latest = testContainer.readLatestSnippet(snippetId).await.value

      assert(latest.inputs.code == "anonymous snippet")
      assert(snippetId.user.isEmpty)
    }

    test(s"[$typeName] readLatestSnippet with non-existent snippet") {
      val user = UserLogin("github-user-nonexistent" + Random.nextInt())
      val nonExistentId = SnippetId("nonexistent", Some(SnippetUserPart(user.login, 0)))

      val result = testContainer.readLatestSnippet(nonExistentId).await

      assert(result.isEmpty, "should return None for non-existent snippet")
    }
  }
}
