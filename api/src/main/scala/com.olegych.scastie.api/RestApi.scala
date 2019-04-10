package com.olegych.scastie.api

import scala.concurrent.Future

trait RestApi {
  def run(inputs: Inputs): Future[SnippetId]
  def save(inputs: Inputs): Future[SnippetId]

  def update(editInputs: EditInputs): Future[Option[SnippetId]]
  def fork(editInputs: EditInputs): Future[Option[SnippetId]]

  def delete(snippetId: SnippetId): Future[Boolean]

  def format(request: FormatRequest): Future[FormatResponse]

  def fetch(snippetId: SnippetId): Future[Option[FetchResult]]
  def fetchOld(id: Int): Future[Option[FetchResult]]
  def fetchUser(): Future[Option[User]]
  def fetchUserSnippets(): Future[List[SnippetSummary]]
}
