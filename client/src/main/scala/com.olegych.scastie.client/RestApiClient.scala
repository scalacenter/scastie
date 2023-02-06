package com.olegych.scastie.client

import com.olegych.scastie.api._
import org.scalajs.dom
import org.scalajs.dom.XMLHttpRequest
import play.api.libs.json._

import scala.concurrent.Future
import scala.util.Try

import scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalajs.js.Thenable.Implicits._
import scalajs.js

class RestApiClient(serverUrl: Option[String]) extends RestApi {

  val apiBase: String = serverUrl.getOrElse("")

  def tryParse[T: Reads](response: XMLHttpRequest): Option[T] =
    tryParse(response.responseText)

  def tryParse[T: Reads](response: dom.Response): Future[Option[T]] =
    response.text().map(tryParse(_))

  def tryParse[T: Reads](text: String): Option[T] = {
    Option.when(text.nonEmpty)(text).flatMap(t =>
      Try(Json.parse(t)).toOption.flatMap(Json.fromJson[T](_).asOpt)
    )
  }

  def get[T: Reads](url: String): Future[Option[T]] = {
    val header = new dom.Headers(js.Dictionary("Accept" -> "application/json"))
    dom
      .fetch(apiBase + "/api" + url, js.Dynamic.literal(headers = header, method = dom.HttpMethod.GET).asInstanceOf[dom.RequestInit])
      .flatMap(tryParse[T](_))
  }

  class Post[O: Reads]() {
    def using[I: Writes](url: String, data: I, async: Boolean = true): Future[Option[O]] = {
      val header = new dom.Headers(js.Dictionary("Accept" -> "application/json", "Content-Type" -> "application/json"))
      dom
        .fetch(
          apiBase + "/api" + url,
          js.Dynamic
            .literal(headers = header, method = dom.HttpMethod.POST, body = Json.prettyPrint(Json.toJson(data)))
            .asInstanceOf[dom.RequestInit]
        )
        .flatMap(tryParse[O](_))
    }
  }

  def post[O: Reads]: Post[O] = new Post[O]

  def run(inputs: Inputs): Future[SnippetId] =
    post[SnippetId].using("/run", inputs).map(_.get)

  def format(request: FormatRequest): Future[FormatResponse] =
    post[FormatResponse].using("/format", request).map(_.get)

  def save(inputs: Inputs): Future[SnippetId] =
    post[SnippetId].using("/save", inputs).map(_.get)

  def saveBlocking(inputs: Inputs): Option[SnippetId] = {
    val req = new dom.XMLHttpRequest()
    req.open(
      "POST",
      apiBase + "/api/save",
      async = false
    )
    req.setRequestHeader("Content-Type", "application/json")
    req.setRequestHeader("Accept", "application/json")

    var snippetId: Option[SnippetId] = None

    req.onreadystatechange = { (e: dom.Event) =>
      if (req.readyState == 4 && req.status == 200) {
        snippetId = tryParse[SnippetId](req)
      }
    }

    req.send(Json.prettyPrint(Json.toJson(inputs)))

    snippetId
  }

  def update(editInputs: EditInputs): Future[Option[SnippetId]] =
    post[SnippetId].using("/update", editInputs)

  def fork(editInputs: EditInputs): Future[Option[SnippetId]] =
    post[SnippetId].using("/fork", editInputs)

  def delete(snippetId: SnippetId): Future[Boolean] =
    post[Boolean].using("/delete", snippetId).map(_.getOrElse(false))

  def fetch(snippetId: SnippetId): Future[Option[FetchResult]] =
    get[FetchResult]("/snippets/" + snippetId.url)

  def fetchOld(id: Int): Future[Option[FetchResult]] =
    get[FetchResult](s"/old-snippets/$id")

  def fetchUser(): Future[Option[User]] =
    get[User]("/user/settings")

  @deprecated("Scheduled for removal", "2023-04-30")
  def getPrivacyPolicyStatus(): Future[Boolean] =
    post[Boolean].using("/user/privacyPolicyStatus", "").map(_.getOrElse(true))

  @deprecated("Scheduled for removal", "2023-04-30")
  def acceptPrivacyPolicy(): Future[Boolean] =
    post[Boolean].using("/user/acceptPrivacyPolicy", "", async=false).map(_.getOrElse(false))

  @deprecated("Scheduled for removal", "2023-04-30")
  def removeAllUserSnippets(): Future[Boolean] =
    post[Boolean].using("/user/removeAllUserSnippets", "", async=false).map(_.getOrElse(false))

  @deprecated("Scheduled for removal", "2023-04-30")
  def removeUserFromPolicyStatus(): Future[Boolean] =
    post[Boolean].using("/user/removeUserFromPolicyStatus", "", async=false).map(_.getOrElse(false))

  def fetchUserSnippets(): Future[List[SnippetSummary]] =
    get[List[SnippetSummary]]("/user/snippets").map(_.getOrElse(Nil))
}
