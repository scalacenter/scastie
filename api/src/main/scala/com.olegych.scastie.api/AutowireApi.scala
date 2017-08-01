package com.olegych.scastie.api

import com.olegych.scastie.proto._

import scala.concurrent.Future

trait AutowireApi {
  def run(inputs: Inputs): Future[SnippetId]
  def save(inputs: Inputs): Future[SnippetId]
  def amend(snippetId: SnippetId, inputs: Inputs): Future[Boolean]
  def update(snippetId: SnippetId, inputs: Inputs): Future[Option[SnippetId]]
  def fork(snippetId: SnippetId, inputs: Inputs): Future[Option[SnippetId]]
  def delete(snippetId: SnippetId): Future[Boolean]

  def format(code: FormatRequest): Future[FormatResponse]

  def fetch(snippetId: SnippetId): Future[Option[FetchResult]]
  def fetchOld(oldId: OldId): Future[Option[FetchResult]]
  def fetchUser(): Future[Option[User]]
  def fetchUserSnippets(): Future[List[SnippetSummary]]

  def complete(
      request: EnsimeRequest.Completion
  ): Future[Option[EnsimeResponse.Completion]]

  def typeAt(
      request: EnsimeRequest.TypeAtPoint
  ): Future[Option[EnsimeResponse.TypeAtPoint]]
}
