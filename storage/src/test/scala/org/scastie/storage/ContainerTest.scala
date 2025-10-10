package org.scastie.storage

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.util.concurrent.Executors
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

import org.scastie.api._
import org.scastie.storage.filesystem.FilesystemContainer
import org.scastie.storage.mongodb.MongoDBContainer

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues

class ContainerTest extends AnyFunSuite with BeforeAndAfterAll with OptionValues {
  val mongo = sys.props.get("SnippetsContainerTest.mongo").flatMap(_.toBooleanOption).contains(true)
  println(s"ContainerTest using mongodb: $mongo")
  val root = Files.createTempDirectory("test")
  val oldRoot = Files.createTempDirectory("old-test")

  private val testContainer: SnippetsContainer with UsersContainer = {
    if (mongo) new MongoDBContainer(defaultConfig = true)
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

  Seq(SbtInputs.default.withSavedConfig, ScalaCliInputs.default).map { inputType =>
    val typeName = inputType.getClass.getSimpleName.stripSuffix("$")

    test(s"[$typeName] create snippet with logged in user") {
      val bob = "bob"
      val snippetId = testContainer.create(inputType, user = Some(UserLogin(bob)))
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
      val inputs = inputType.copyBaseInput(code = "source", isShowingInUserProfile = true)
      val snippetId = testContainer.save(inputs, user = None).await

      val forkedInputs = inputType.copyBaseInput(code = "forked", isShowingInUserProfile = true)
      val forkedSnippetId = testContainer.fork(snippetId, forkedInputs, user = None).await

      val forkedBis = testContainer.readSnippet(forkedSnippetId).await.get

      assert(forkedSnippetId != snippetId)
      assert(forkedBis.inputs.forked.get == snippetId)
    }

    test(s"[$typeName] update") {
      val user = UserLogin("github-user-update" + Random.nextInt())
      val inputs1 = inputType.copyBaseInput(code = "inputs1", isShowingInUserProfile = true)
      val snippetId1 = testContainer.save(inputs1, Some(user)).await
      assert(snippetId1.user.get.update == 0)

      val inputs2 = inputType.copyBaseInput(code = "inputs2", isShowingInUserProfile = true)
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

      val inputs4 = inputType.copyBaseInput(code = "inputs4", isShowingInUserProfile = false)
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

    def ensureUserCleanup(username: String, test: String => Any) = {
      try {
        test(username)
      } finally {
        testContainer.deleteUser(UserLogin(username)).await
      }

    }

    test(s"[$typeName] add new user") {
      ensureUserCleanup(
        "bob",
        { username =>
          val snippetId = testContainer.addNewUser(UserLogin(username)).await
          assert(snippetId)
        }
      )
    }

    test(s"[$typeName] get user privacy policy acceptance") {
      ensureUserCleanup(
        "bob",
        { username =>
          val snippetId = testContainer.addNewUser(UserLogin(username)).await
          val response = testContainer.getPrivacyPolicyResponse(UserLogin(username)).await
          assert(testContainer.deleteUser(UserLogin(username)).await == true)
        }
      )
    }

    test(s"[$typeName] set user privacy policy acceptance") {
      ensureUserCleanup(
        "bob",
        { username =>
          val snippetId = testContainer.addNewUser(UserLogin(username)).await
          val updatePrivacyPolicy = testContainer.setPrivacyPolicyResponse(UserLogin(username), false).await
          val response = testContainer.getPrivacyPolicyResponse(UserLogin(username)).await
          assert(response == false)
        }
      )
    }

    test(s"[$typeName] remove user from privacy policy list") {
      val username = "bob"
      val snippetId = testContainer.addNewUser(UserLogin(username)).await
      val removeUser = testContainer.deleteUser(UserLogin(username)).await
      assert(removeUser == true)
    }
  }
}
