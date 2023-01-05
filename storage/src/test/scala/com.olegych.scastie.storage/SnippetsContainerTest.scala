package com.olegych.scastie.storage

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.util.concurrent.Executors

import com.olegych.scastie.api._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.OptionValues

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

class SnippetsContainerTest extends AnyFunSuite with BeforeAndAfterAll with OptionValues {
  val mongo = sys.props.get("SnippetsContainerTest.mongo").flatMap(_.toBooleanOption).contains(true)
  println(s"SnippetsContainerTest using mongodb: $mongo")
  val root = Files.createTempDirectory("test")
  val oldRoot = Files.createTempDirectory("old-test")

  private lazy val mongoContainer = new MongoDBSnippetsContainer(scala.concurrent.ExecutionContext.Implicits.global)
  private val testContainer: SnippetsContainer = {
    if (mongo)
      mongoContainer
    else {
      new FilesSnippetsContainer(root, oldRoot)(
        Executors.newSingleThreadExecutor()
      )
    }
  }

  override protected def afterAll(): Unit = {
    deleteRecursively(root)
    deleteRecursively(oldRoot)
    if (mongo) mongoContainer.close()
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

  test("create snippet with logged in user") {
    val container = testContainer
    val bob = "bob"
    val snippetId =
      container.create(Inputs.default, user = Some(UserLogin(bob)))
    assert(snippetId.await.user.get.login == bob)
  }

  test("create snippet with anonymous user") {
    val container = testContainer
    val snippetId = container.create(Inputs.default, user = None)
    assert(snippetId.await.user.isEmpty)
  }

  test("create then read") {
    val container = testContainer
    val inputs = Inputs.default
    val snippetId = container.create(inputs, user = None).await
    val result = container.readSnippet(snippetId).await

    assert(result.value.inputs == inputs.withSavedConfig)
  }

  test("fork") {
    val container = testContainer
    val inputs =
      Inputs.default.copy(code = "source", isShowingInUserProfile = true)
    val snippetId = container.save(inputs, user = None).await

    val forkedInputs =
      Inputs.default.copy(code = "forked", isShowingInUserProfile = true)
    val forkedSnippetId =
      container.fork(snippetId, forkedInputs, user = None).await

    val forkedBis = container.readSnippet(forkedSnippetId).await.get

    assert(forkedSnippetId != snippetId)
    assert(forkedBis.inputs.forked.get == snippetId)
  }

  test("update") {
    val container = testContainer
    val user = UserLogin("github-user-update" + Random.nextInt())
    val inputs1 =
      Inputs.default.copy(code = "inputs1").copy(isShowingInUserProfile = true)
    val snippetId1 = container.save(inputs1, Some(user)).await
    assert(snippetId1.user.get.update == 0)

    val inputs2 =
      Inputs.default.copy(code = "inputs2").copy(isShowingInUserProfile = true)
    val snippetId2 = container.update(snippetId1, inputs2).await.get
    assert(snippetId2.user.get.update == 1, "we get a new update id")

    val readInputs1 = container.readSnippet(snippetId1).await.get.inputs
    val readInputs2 = container.readSnippet(snippetId2).await.get.inputs

    assert(readInputs1 == inputs1.copy(isShowingInUserProfile = false).withSavedConfig, "we don't mutate previous input")
    assert(readInputs2 == inputs2.copy(forked = Some(snippetId1)).withSavedConfig, "we update forked")

    val snippets = container.listSnippets(user).await
    assert(snippets.size == 1 && snippets.head.snippetId == snippetId2, "we hide old version")
  }

  test("listSnippets") {
    val container = testContainer
    val user = UserLogin("github-user-list" + Random.nextInt())
    val user2 = UserLogin("github-user-list2" + Random.nextInt())

    val inputs1 = Inputs.default.copy(code = "inputs1")
    container.save(inputs1, Some(user)).await
    Thread.sleep(100)

    val inputs2 = Inputs.default.copy(code = "inputs2")
    container.save(inputs2, Some(user)).await
    Thread.sleep(100)

    val inputs3 = Inputs.default.copy(code = "inputs3")
    container.save(inputs3, Some(user)).await
    Thread.sleep(100)

    val user2inputs = Inputs.default.copy(code = "inputs3")
    container.save(user2inputs, Some(user2)).await
    Thread.sleep(100)

    val inputs4 =
      Inputs.default.copy(code = "inputs4", isShowingInUserProfile = false)
    container.create(inputs4, Some(user)).await

    val snippets = container.listSnippets(user).await
    assert(
      snippets.map(_.summary) == List("inputs3", "inputs2", "inputs1")
    )
  }

  test("delete") {
    val container = testContainer
    val user = UserLogin("github-user-delete" + Random.nextInt())

    val inputs1 = Inputs.default.copy(code = "inputs1")
    val snippetId1 = container.save(inputs1, Some(user)).await

    val inputs1U = Inputs.default.copy(code = "inputs1 updated")
    container.update(snippetId1, inputs1U).await.get

    val inputs2 = Inputs.default.copy(code = "inputs2")
    val snippetId2 = container.save(inputs2, Some(user)).await

    val inputs2U = Inputs.default.copy(code = "inputs2 updated")
    val snippetId2U = container.update(snippetId2, inputs2U).await.get

    assert(container.listSnippets(user).await.size == 2)

    container.deleteAll(snippetId2U).await

    assert(container.readSnippet(snippetId2U).await == None)
    assert(container.readSnippet(snippetId2).await == None)

    assert(container.listSnippets(user).await.size == 1)
  }

  test("appendOutput") {
    val container = testContainer
    val inputs = Inputs.default
    val snippetId = container.create(inputs, user = None).await
    val progress = SnippetProgress.default.copy(snippetId = Some(snippetId))
    container.appendOutput(progress)
    val result = container.readSnippet(snippetId).await

    assert(result.value.progresses.headOption.value == progress, "we properly append output")
  }
}
