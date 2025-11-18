package org.scastie.api

import scala.concurrent.Future

trait RestApi {
  def run(inputs: BaseInputs): Future[SnippetId]
  def save(inputs: BaseInputs): Future[SnippetId]

  def update(editInputs: EditInputs): Future[Option[SnippetId]]
  def fork(editInputs: EditInputs): Future[Option[SnippetId]]

  def delete(snippetId: SnippetId): Future[Boolean]

  def format(request: FormatRequest): Future[FormatResponse]

  def fetch(snippetId: SnippetId): Future[Option[FetchResult]]
  def fetchOld(id: Int): Future[Option[FetchResult]]
  def fetchUserData(): Future[Option[UserData]]
  def fetchUserSnippets(): Future[List[SnippetSummary]]
}
