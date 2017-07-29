package com.olegych.scastie.api

import com.olegych.scastie.proto._

import scala.concurrent.Future

trait AutowireApi {
  def run(inputs: Inputs): Future[SnippetId]
  def format(code: FormatRequest): Future[FormatResponse]

  def complete(
      completionRequest: EnsimeRequest.Completion
  ): Future[Option[EnsimeResponse.Completion]]
  def typeAt(
      typeAtPointRequest: EnsimeRequest.TypeAtPoint
  ): Future[Option[EnsimeResponse.TypeAtPoint]]

  def save(inputs: Inputs): Future[SnippetId]
  def amend(snippetId: SnippetId, inputs: Inputs): Future[Boolean]
  def update(snippetId: SnippetId, inputs: Inputs): Future[Option[SnippetId]]
  def fork(snippetId: SnippetId, inputs: Inputs): Future[Option[SnippetId]]
  def delete(snippetId: SnippetId): Future[Boolean]

  def fetch(snippetId: SnippetId): Future[Option[FetchResult]]
  def fetchOld(id: Int): Future[Option[FetchResult]]
  def fetchUser(): Future[Option[User]]
  def fetchUserSnippets(): Future[List[SnippetSummary]]
}
