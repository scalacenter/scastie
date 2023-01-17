package com.olegych.scastie.web.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.olegych.scastie.api.{SnippetId, SnippetUserPart}
import com.olegych.scastie.web.PlayJsonSupport
import org.scalatest.funsuite.AnyFunSuite

class SnippetIdMatcherTests extends AnyFunSuite with ScalatestRouteTest {
  import PlayJsonSupport._

  def testRoute(snippetIdRoute: Route, f1: String => String, f2: String => String, checkEnd: Boolean = true): Unit = {

    val expectedBase =
      SnippetId(
        "GIbgJuUFSKaVzLDGK4kxdw",
        None
      )

    println(f1("/GIbgJuUFSKaVzLDGK4kxdw"))
    Get(f1("/GIbgJuUFSKaVzLDGK4kxdw")) ~> snippetIdRoute ~> check {
      val obtained = responseAs[SnippetId]
      assert(expectedBase == obtained)
    }

    if (checkEnd) {
      Get(f2("/GIbgJuUFSKaVzLDGK4kxdw/")) ~> snippetIdRoute ~> check {
        val obtained = responseAs[SnippetId]
        assert(expectedBase == obtained)
      }
    }

    val expectedUser =
      SnippetId(
        "GIbgJuUFSKaVzLDGK4kxdw",
        Some(SnippetUserPart("MasseGuillaume", 0))
      )

    Get(f1("/MasseGuillaume/GIbgJuUFSKaVzLDGK4kxdw")) ~> snippetIdRoute ~> check {
      val obtained = responseAs[SnippetId]
      assert(expectedUser == obtained)
    }

    if (checkEnd) {
      Get(f2("/MasseGuillaume/GIbgJuUFSKaVzLDGK4kxdw/")) ~> snippetIdRoute ~> check {
        val obtained = responseAs[SnippetId]
        assert(expectedUser == obtained)
      }
    }

    val expectedFull =
      SnippetId(
        "GIbgJuUFSKaVzLDGK4kxdw",
        Some(SnippetUserPart("MasseGuillaume", 2))
      )

    Get(f1("/MasseGuillaume/GIbgJuUFSKaVzLDGK4kxdw/2")) ~> snippetIdRoute ~> check {
      val obtained = responseAs[SnippetId]
      assert(expectedFull == obtained)
    }

    if (checkEnd) {
      Get(f2("/MasseGuillaume/GIbgJuUFSKaVzLDGK4kxdw/2/")) ~> snippetIdRoute ~> check {
        val obtained = responseAs[SnippetId]
        assert(expectedFull == obtained)
      }
    }
  }

  test("snippetId") {
    val snippetIdRoute =
      get(
        snippetId(sid => complete(sid))
      )

    testRoute(
      snippetIdRoute,
      x => x,
      x => x
    )
  }

  test("snippetIdStart") {
    val start = "snippets"

    val snippetIdRoute =
      get(
        snippetIdStart(start)(sid => complete(sid))
      )

    testRoute(
      snippetIdRoute,
      "/" + start + _,
      "/" + start + _
    )
  }

  test("snippetIdEnd") {
    val start = "api"
    val end = "foo"

    val snippetIdRoute =
      get(
        snippetIdEnd(start, end)(sid => complete(sid))
      )

    testRoute(
      snippetIdRoute,
      "/" + start + _ + "/" + end,
      "/" + start + _ + end
    )
  }

  test("snippetIdExtension") {
    val extension = ".js"

    val snippetIdRoute =
      get(
        snippetIdExtension(extension)(sid => complete(sid))
      )

    testRoute(
      snippetIdRoute,
      _ + extension,
      _ + extension,
      checkEnd = false
    )
  }
}
