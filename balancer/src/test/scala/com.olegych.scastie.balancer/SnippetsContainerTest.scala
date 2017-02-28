package com.olegych.scastie
package balancer

import api._

import java.nio.file._

import org.scalatest.FunSuite

class SnippetsContainerTest extends FunSuite {

  private def testContainer = new SnippetsContainer(Files.createTempDirectory("test"))

  test("create snippet with logged in user") {
    val container = testContainer
    val bob = "bob"
    val snippetId = container.create(Inputs.default, user = Some(UserLogin(bob)))
    assert(snippetId.user.get.login == bob)
  }

  test("create snippet with annonymous user") {
    val container = testContainer
    val snippetId = container.create(Inputs.default, user = None)
    assert(snippetId.user.isEmpty)
  }

  test("create then read") {
    val container = testContainer
    val inputs = Inputs.default
    val snippetId = container.create(inputs, user = None)
    val result = container.readSnippet(snippetId)

    assert(result.isDefined)
    assert(result.get.inputs == inputs)
  }

  test("fork"){
    val container = testContainer
    val inputs = Inputs.default.copy(code = "source", showInUserProfile = true)
    val snippetId = container.save(inputs, user = None)
    val result = container.readSnippet(snippetId)

    val forkedInputs = Inputs.default.copy(code = "forked", showInUserProfile = true)
    val forkedSnippetId = container.fork(snippetId, forkedInputs, user = None).get

    val forkedBis = container.readSnippet(forkedSnippetId).get

    assert(forkedSnippetId != snippetId)
    assert(forkedBis.inputs.forked.get == snippetId)
  }

  test("update"){
    val container = testContainer
    val user = UserLogin("github-user")
    val inputs1 = Inputs.default.copy(code = "inputs1").copy(showInUserProfile = true)
    val snippetId1 = container.save(inputs1, Some(user))
    assert(snippetId1.user.get.update == None)
    
    val inputs2 = Inputs.default.copy(code = "inputs2").copy(showInUserProfile = true)
    val snippetId2 = container.update(snippetId1, inputs2).get
    assert(snippetId2.user.get.update == Some(1), "we get a new update id")

    val readInputs1 = container.readSnippet(snippetId1).get.inputs
    val readInputs2 = container.readSnippet(snippetId2).get.inputs

    assert(readInputs1 == inputs1, "we don't mutate previous input")
    assert(readInputs2 == inputs2)

    val snippets = container.listSnippets(user)
    assert(snippets.size == 2)
  }

  test("amend") {
    val container = testContainer

    val inputs1 = Inputs.default.copy(code = "inputs1")
    val snippetId1 = container.save(inputs1, Some(UserLogin("github-user")))
    
    val inputs2 = inputs1.copy(code = "inputs2")
    val amendSuccess = container.amend(snippetId1, inputs2)
    assert(amendSuccess)
    
    val readInputs1bis = container.readSnippet(snippetId1).get.inputs

    assert(readInputs1bis != inputs1, "we mutate previous input")
    assert(readInputs1bis == inputs2)
  }

  test("listSnippets"){
    val container = testContainer
    val user = UserLogin("github-user")

    val inputs1 = Inputs.default.copy(code = "inputs1")
    val snippetId1 = container.save(inputs1, Some(user))

    val inputs2 = Inputs.default.copy(code = "inputs2")
    val snippetId2 = container.save(inputs2, Some(user))

    val inputs3 = Inputs.default.copy(code = "inputs3")
    val snippetId3 = container.save(inputs3, Some(user))

    val snippets = container.listSnippets(user)

    assert(snippets.size == 3)
    assert(snippets.map(_.summary) == List("inputs1", "inputs2", "inputs3"))
  }

  test("delete"){
    val container = testContainer
    val user = UserLogin("github-user")

    val inputs1 = Inputs.default.copy(code = "inputs1")
    val snippetId1 = container.save(inputs1, Some(user))
    
    val inputs1U = Inputs.default.copy(code = "inputs1 updated")
    val snippetId1U = container.update(snippetId1, inputs1U).get

    val inputs2 = Inputs.default.copy(code = "inputs2")
    val snippetId2 = container.save(inputs2, Some(user))
    
    val inputs2U = Inputs.default.copy(code = "inputs2 updated")
    val snippetId2U = container.update(snippetId2, inputs2U).get

    assert(container.listSnippets(user).size == 4)
    
    container.delete(snippetId2U)

    assert(container.listSnippets(user).size == 3)

    container.delete(snippetId2)

    assert(container.listSnippets(user).size == 2)
  }
}
