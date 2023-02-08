package com.olegych.scastie.web

import com.olegych.scastie.api._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}

package object routes {
  def snippetIdStart(matcherStart: String)(f: SnippetId => Route): Route =
    snippetIdBase(
      matcherStart / _,
      matcherStart / _
    )(f)

  def snippetId(f: SnippetId => Route): Route =
    snippetIdBase(
      p => p,
      p => p
    )(f)

  def snippetIdEnd(matcherStart: String, matcherEnd: String)(f: SnippetId => Route): Route =
    snippetIdBase(
      matcherStart / _ / matcherEnd,
      matcherStart / _ / matcherEnd
    )(f)

  def snippetIdExtension(extension: String)(f: SnippetId => Route): Route =
    snippetIdBase(
      _ ~ extension,
      _ ~ extension
    )(f)

  private val uuidMatcher = PathMatcher("[A-Za-z0-9]{22}".r)

  private def snippetIdBase(
      fp1: PathMatcher[Tuple1[String]] => PathMatcher[Tuple1[String]],
      fp2: PathMatcher[(String, String, Option[Int])] => PathMatcher[
        (String, String, Option[Int])
      ]
  )(f: SnippetId => Route): Route = {

    concat(
      path(fp1(uuidMatcher) ~ Slash.?)(uuid => f(SnippetId(uuid, None))),
      path(fp2(Segment / uuidMatcher ~ (Slash ~ IntNumber).?) ~ Slash.?)(
        (user, uuid, update) => f(SnippetId(uuid, Some(SnippetUserPart(user, update.getOrElse(0)))))
      )
    )
  }
}
