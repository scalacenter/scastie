package com.olegych.scastie.client

import com.olegych.scastie.api._

import play.api.libs.json._

import org.scalajs.dom
import dom.ext.Ajax

import scala.concurrent.Future
import scalajs.concurrent.JSExecutionContext.Implicits.queue

object ApiClient extends RestApi {
  def get[T: Reads](url: String): Future[T] = {
    Ajax
      .get("/api" + url)
      .map(ret => Json.fromJson[T](Json.parse(ret.responseText)).asOpt.get)
  }

  class Post[O: Reads]() {
    def using[I: Writes](url: String, data: I): Future[O] = {
      Ajax
        .post(
          url = "/api" + url,
          data = Json.prettyPrint(Json.toJson(data)),
          headers = Map("Content-Type" -> "application/json"),
        )
        .map(ret => Json.fromJson[O](Json.parse(ret.responseText)).asOpt.get)
    }
  }

  def post[O: Reads](): Post[O] = new Post[O]

  def run(inputs: Inputs): Future[SnippetId] =
    post[SnippetId].using("/run", inputs)

  def format(request: FormatRequest): Future[FormatResponse] =
    post[FormatResponse].using("/format", request)

  def autocomplete(
      request: AutoCompletionRequest
  ): Future[Option[AutoCompletionResponse]] =
    post[AutoCompletionResponse]
      .using("/autocomplete", request)
      .map(x => Some(x))

  def typeAt(request: TypeAtPointRequest): Future[Option[TypeAtPointResponse]] =
    post[TypeAtPointResponse].using("/typeAt", request).map(x => Some(x))

  def updateEnsimeConfig(request: UpdateEnsimeConfigRequest): Future[Option[EnsimeConfigUpdated]] =
    post[EnsimeConfigUpdated].using("/updateEnsimeConfig", request).map(x => Some(x))

  def save(inputs: Inputs): Future[SnippetId] =
    post[SnippetId].using("/save", inputs)

  def amend(editInputs: EditInputs): Future[Boolean] =
    post[Boolean].using("/amend", editInputs)

  def update(editInputs: EditInputs): Future[Option[SnippetId]] =
    post[SnippetId].using("/update", editInputs).map(x => Some(x))

  def fork(editInputs: EditInputs): Future[Option[SnippetId]] =
    post[SnippetId].using("/fork", editInputs).map(x => Some(x))

  def delete(snippetId: SnippetId): Future[Boolean] =
    post[Boolean].using("/delete", snippetId)

  def fetch(snippetId: SnippetId): Future[Option[FetchResult]] =
    get[FetchResult]("/snippets/" + snippetId.url).map(x => Some(x))

  def fetchOld(id: Int): Future[Option[FetchResult]] =
    get[FetchResult](s"/old-snippets/$id").map(x => Some(x))

  def fetchUser(): Future[Option[User]] =
    get[User]("/user/settings").map(x => Some(x))

  def fetchUserSnippets(): Future[List[SnippetSummary]] =
    get[List[SnippetSummary]]("/user/snippets")
}
