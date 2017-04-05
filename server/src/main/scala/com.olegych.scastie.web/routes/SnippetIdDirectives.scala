package com.olegych.scastie
package web
package routes

import api._

import akka.http.scaladsl._
import server.Directives._
import server.{Route, PathMatcher}

object SnippetIdDirectives {
  def snippetId(pathStart: String)(f: SnippetId => Route): Route =
    snippetIdBase(pathStart, p => p, p => p)(f)

  def snippetIdEnd(pathStart: String,
                   pathEnd: String)(f: SnippetId => Route): Route =
    snippetIdBase(pathStart, _ / pathEnd, _ / pathEnd)(f)

  private def snippetIdBase(
      pathStart: String,
      fp1: PathMatcher[Tuple1[String]] => PathMatcher[Tuple1[String]],
      fp2: PathMatcher[(String, String, Option[Int])] => PathMatcher[
        (String, String, Option[Int])
      ]
  )(f: SnippetId => Route): Route = {
    concat(
      path(fp1(pathStart / Segment))(uuid ⇒ f(SnippetId(uuid, None))),
      path(fp2(pathStart / Segment / Segment / IntNumber.?))(
        (user, uuid, update) ⇒
          f(SnippetId(uuid, Some(SnippetUserPart(user, update))))
      )
    )
  }
}
