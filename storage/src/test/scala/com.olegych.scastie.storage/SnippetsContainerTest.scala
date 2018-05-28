package com.olegych.scastie.storage

import java.nio.file._
import java.util.concurrent.Executors

import com.olegych.scastie.api._
import org.scalatest.FunSuite

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class SnippetsContainerTest extends FunSuite {

  private def testContainer =
    new SnippetsContainer(
      Files.createTempDirectory("test"),
      Files.createTempDirectory("old-test")
    )(Executors.newSingleThreadExecutor())

  private implicit class FAwait[T](f: Future[T]) {
    def await = Await.result(f, Duration.Inf)
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

    assert(result.isDefined)
    assert(result.get.inputs == inputs)
  }

  test("fork") {
    val container = testContainer
    val inputs =
      Inputs.default.copy(code = "source", isShowingInUserProfile = true)
    val snippetId = container.save(inputs, user = None).await

    val forkedInputs =
      Inputs.default.copy(code = "forked", isShowingInUserProfile = true)
    val forkedSnippetId =
      container.fork(snippetId, forkedInputs, user = None).await.get

    val forkedBis = container.readSnippet(forkedSnippetId).await.get

    assert(forkedSnippetId != snippetId)
    assert(forkedBis.inputs.forked.get == snippetId)
  }

  test("update") {
    val container = testContainer
    val user = UserLogin("github-user")
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

    assert(readInputs1 == inputs1, "we don't mutate previous input")
    assert(readInputs2 == inputs2)

    val snippets = container.listSnippets(user).await
    assert(snippets.size == 2)
  }

  test("amend") {
    val container = testContainer

    val inputs1 = Inputs.default.copy(code = "inputs1")
    val snippetId1 =
      container.save(inputs1, Some(UserLogin("github-user"))).await

    val inputs2 = inputs1.copy(code = "inputs2")
    val amendSuccess = container.amend(snippetId1, inputs2).await
    assert(amendSuccess)

    val readInputs1bis = container.readSnippet(snippetId1).await.get.inputs

    assert(readInputs1bis != inputs1, "we mutate previous input")
    assert(readInputs1bis.copy(isShowingInUserProfile = false) == inputs2)
  }

  test("listSnippets") {
    val container = testContainer
    val user = UserLogin("github-user")

    val inputs1 = Inputs.default.copy(code = "inputs1")
    container.save(inputs1, Some(user)).await

    val inputs2 = Inputs.default.copy(code = "inputs2")
    container.save(inputs2, Some(user)).await

    val inputs3 = Inputs.default.copy(code = "inputs3")
    container.save(inputs3, Some(user)).await

    val snippets = container.listSnippets(user).await

    assert(snippets.size == 3)
    assert(
      snippets.map(_.summary).toSet == Set("inputs1", "inputs2", "inputs3")
    )
  }

  test("delete") {
    val container = testContainer
    val user = UserLogin("github-user")

    val inputs1 = Inputs.default.copy(code = "inputs1")
    val snippetId1 = container.save(inputs1, Some(user)).await

    val inputs1U = Inputs.default.copy(code = "inputs1 updated")
    container.update(snippetId1, inputs1U).await.get

    val inputs2 = Inputs.default.copy(code = "inputs2")
    val snippetId2 = container.save(inputs2, Some(user)).await

    val inputs2U = Inputs.default.copy(code = "inputs2 updated")
    val snippetId2U = container.update(snippetId2, inputs2U).await.get

    assert(container.listSnippets(user).await.size == 4)

    container.delete(snippetId2U).await

    assert(container.listSnippets(user).await.size == 3)

    container.delete(snippetId2).await

    assert(container.listSnippets(user).await.size == 2)
  }
}
