package com.olegych.scastie
package balancer

import api._

import java.nio.file._

import org.scalatest.FunSuite

class SnippetsContainerTest extends FunSuite {

  private def emptyProgress(snippetId: SnippetId): SnippetProgress = {
    SnippetProgress(
        snippetId,
        None,
        None,
        Nil,
        Nil,
        None,
        false,
        false,
        false
      )
  }

  private def testContainer = new SnippetsContainer(Files.createTempDirectory("test"))

  test("create then read") {
    val container = testContainer

    val inputs = Inputs.default
    val snippetId = container.create(inputs, None)
    
    container.appendOutput(emptyProgress(snippetId))

    val obtained = container.readSnippet(snippetId).get.inputs

    assert(obtained == inputs)
  }

  test("update") {
    val container = testContainer

    val inputs1 = Inputs.default.copy(code = "inputs1")
    val snippetId1 = container.create(inputs1, Some("github-user"))
    assert(snippetId1.user.get.update == None)
    
    val inputs2 = Inputs.default.copy(code = "inputs2")
    val snippetId2 = container.update(snippetId1, inputs2)
    assert(snippetId2.user.get.update == Some(1), "we get a new update id")

    container.appendOutput(emptyProgress(snippetId1))
    container.appendOutput(emptyProgress(snippetId2))

    val readInputs1 = container.readSnippet(snippetId1).get.inputs
    val readInputs2 = container.readSnippet(snippetId2).get.inputs

    assert(readInputs1 == inputs1, "we don't mutate previous input")
    assert(readInputs2 == inputs2)
  }

  test("amend") {
    val container = testContainer

    val inputs1 = Inputs.default.copy(code = "inputs1")
    val snippetId1 = container.create(inputs1, Some("github-user"))
    assert(snippetId1.user.get.update == None)
    container.appendOutput(emptyProgress(snippetId1))
    val readInputs1 = container.readSnippet(snippetId1).get.inputs

    assert(inputs1 == readInputs1)
    
    val inputs2 = inputs1.copy(code = "inputs2")
    val snippetId2 = container.amend(snippetId1, inputs2)
    assert(snippetId1 == snippetId2)
    container.appendOutput(emptyProgress(snippetId2))
    val readInputs1bis = container.readSnippet(snippetId1).get.inputs

    assert(readInputs1bis != inputs1, "we mutate previous input")
    assert(readInputs1bis == inputs2)
  }

  test("listSnippets"){
    val container = testContainer
    val user = "github-user"

    val inputs1 = Inputs.default.copy(code = "inputs1")
    val snippetId1 = container.create(inputs1, Some(user))
    container.appendOutput(emptyProgress(snippetId1))

    val inputs2 = Inputs.default.copy(code = "inputs2")
    val snippetId2 = container.create(inputs2, Some(user))
    container.appendOutput(emptyProgress(snippetId2))

    val inputs3 = Inputs.default.copy(code = "inputs3")
    val snippetId3 = container.create(inputs3, Some(user))
    container.appendOutput(emptyProgress(snippetId3))

    val snippets = container.listSnippets(user)

    assert(snippets.size == 3)
    assert(snippets.map(_.summary) == List("inputs1", "inputs2", "inputs3"))
  }
}
