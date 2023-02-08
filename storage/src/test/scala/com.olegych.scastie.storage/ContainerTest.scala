package com.olegych.scastie.storage

import com.olegych.scastie.api._
import com.olegych.scastie.storage.filesystem.FilesystemContainer
import com.olegych.scastie.storage.mongodb.MongoDBContainer
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

class ContainerTest extends AnyFunSuite with BeforeAndAfterAll with OptionValues {
  val mongo = sys.props.get("SnippetsContainerTest.mongo").flatMap(_.toBooleanOption).contains(true)
  println(s"ContainerTest using mongodb: $mongo")
  val root = Files.createTempDirectory("test")
  val oldRoot = Files.createTempDirectory("old-test")

  private val testContainer: SnippetsContainer with UsersContainer = {
    if (mongo)
      new MongoDBContainer(defaultConfig = true)
    else {
      new FilesystemContainer(root, oldRoot)(
       ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())
      )
    }
  }

  override protected def afterAll(): Unit = {
    deleteRecursively(root)
    deleteRecursively(oldRoot)
    if (mongo) testContainer.close()
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
    val bob = "bob"
    val snippetId =
      testContainer.create(Inputs.default, user = Some(UserLogin(bob)))
    assert(snippetId.await.user.get.login == bob)
  }

  test("create snippet with anonymous user") {
    val container = testContainer
    val snippetId = container.create(Inputs.default, user = None)
    assert(snippetId.await.user.isEmpty)
  }

  test("create then read") {
    val inputs = Inputs.default
    val snippetId = testContainer.create(inputs, user = None).await
    val result = testContainer.readSnippet(snippetId).await

    assert(result.value.inputs == inputs.withSavedConfig)
  }

  test("fork") {
    val inputs =
      Inputs.default.copy(code = "source", isShowingInUserProfile = true)
    val snippetId = testContainer.save(inputs, user = None).await

    val forkedInputs =
      Inputs.default.copy(code = "forked", isShowingInUserProfile = true)
    val forkedSnippetId =
      testContainer.fork(snippetId, forkedInputs, user = None).await

    val forkedBis = testContainer.readSnippet(forkedSnippetId).await.get

    assert(forkedSnippetId != snippetId)
    assert(forkedBis.inputs.forked.get == snippetId)
  }

  test("update") {
    val user = UserLogin("github-user-update" + Random.nextInt())
    val inputs1 =
      Inputs.default.copy(code = "inputs1").copy(isShowingInUserProfile = true)
    val snippetId1 = testContainer.save(inputs1, Some(user)).await
    assert(snippetId1.user.get.update == 0)

    val inputs2 =
      Inputs.default.copy(code = "inputs2").copy(isShowingInUserProfile = true)
    val snippetId2 = testContainer.update(snippetId1, inputs2).await.get
    assert(snippetId2.user.get.update == 1, "we get a new update id")

    val readInputs1 = testContainer.readSnippet(snippetId1).await.get.inputs
    val readInputs2 = testContainer.readSnippet(snippetId2).await.get.inputs

    assert(readInputs1 == inputs1.copy(isShowingInUserProfile = false).withSavedConfig, "we don't mutate previous input")
    assert(readInputs2 == inputs2.copy(forked = Some(snippetId1)).withSavedConfig, "we update forked")

    val snippets = testContainer.listSnippets(user).await
    assert(snippets.size == 1 && snippets.head.snippetId == snippetId2, "we hide old version")
  }

  test("listSnippets") {
    val user = UserLogin("github-user-list" + Random.nextInt())
    val user2 = UserLogin("github-user-list2" + Random.nextInt())

    val inputs1 = Inputs.default.copy(code = "inputs1")
    testContainer.save(inputs1, Some(user)).await

    val inputs2 = Inputs.default.copy(code = "inputs2")
    testContainer.save(inputs2, Some(user)).await

    val inputs3 = Inputs.default.copy(code = "inputs3")
    testContainer.save(inputs3, Some(user)).await

    val user2inputs = Inputs.default.copy(code = "inputs3")
    testContainer.save(user2inputs, Some(user2)).await

    val inputs4 =
      Inputs.default.copy(code = "inputs4", isShowingInUserProfile = false)
    testContainer.create(inputs4, Some(user)).await

    val snippets = testContainer.listSnippets(user).await
    assert(
      snippets.map(_.summary).toSet == Set("inputs3", "inputs2", "inputs1")
    )
  }

  test("delete") {
    val user = UserLogin("github-user-delete" + Random.nextInt())

    val inputs1 = Inputs.default.copy(code = "inputs1")
    val snippetId1 = testContainer.save(inputs1, Some(user)).await

    val inputs1U = Inputs.default.copy(code = "inputs1 updated")
    testContainer.update(snippetId1, inputs1U).await.get

    val inputs2 = Inputs.default.copy(code = "inputs2")
    val snippetId2 = testContainer.save(inputs2, Some(user)).await

    val inputs2U = Inputs.default.copy(code = "inputs2 updated")
    val snippetId2U = testContainer.update(snippetId2, inputs2U).await.get

    assert(testContainer.listSnippets(user).await.size == 2)

    testContainer.deleteAll(snippetId2U).await

    assert(testContainer.readSnippet(snippetId2U).await == None)
    assert(testContainer.readSnippet(snippetId2).await == None)

    assert(testContainer.listSnippets(user).await.size == 1)
  }

  test("appendOutput") {
    val inputs = Inputs.default
    val snippetId = testContainer.create(inputs, user = None).await
    val progress = SnippetProgress.default.copy(snippetId = Some(snippetId))
    testContainer.appendOutput(progress)
    val result = testContainer.readSnippet(snippetId).await

    assert(result.value.progresses.headOption.value == progress, "we properly append output")
  }

  test("deleteAllSnippets") {
    val user = UserLogin("github-user-delete" + Random.nextInt())

    val inputs1 = Inputs.default.copy(code = "inputs1")
    val snippetId1 = testContainer.save(inputs1, Some(user)).await

    val inputs2 = Inputs.default.copy(code = "inputs2")
    val snippetId2 = testContainer.save(inputs2, Some(user)).await

    val inputs2U = Inputs.default.copy(code = "inputs2 updated")
    val snippetId2U = testContainer.update(snippetId2, inputs2U).await.get

    assert(testContainer.listSnippets(user).await.size == 2)

    testContainer.removeUserSnippets(user).await

    assert(testContainer.readSnippet(snippetId1).await == None)
    assert(testContainer.readSnippet(snippetId2U).await == None)
    assert(testContainer.readSnippet(snippetId2).await == None)

    assert(testContainer.listSnippets(user).await.size == 0)
  }

  def ensureUserCleanup(username: String, test: String => Any) = {
    try {
      test("bob")
    } finally {
      testContainer.deleteUser(UserLogin(username)).await
    }

  }

  test("add new user") {
    ensureUserCleanup("bob", { username =>
      val snippetId = testContainer.addNewUser(UserLogin(username)).await
      assert(snippetId)
    })
  }

  test("get user privacy policy acceptance") {
    ensureUserCleanup("bob", { username =>
      val snippetId = testContainer.addNewUser(UserLogin(username)).await
      val response = testContainer.getPrivacyPolicyResponse(UserLogin(username)).await
      assert(testContainer.deleteUser(UserLogin(username)).await == true)
    })
  }

  test("set user privacy policy acceptance") {
    ensureUserCleanup("bob", { username =>
      val snippetId = testContainer.addNewUser(UserLogin(username)).await
      val updatePrivacyPolicy = testContainer.setPrivacyPolicyResponse(UserLogin(username), false).await
      val response = testContainer.getPrivacyPolicyResponse(UserLogin(username)).await
      assert(response == false)
    })
  }

  test("remove user from privacy policy list") {
    val username = "bob"
    val snippetId = testContainer.addNewUser(UserLogin(username)).await
    val removeUser = testContainer.deleteUser(UserLogin(username)).await
    assert(removeUser == true)
  }
}
