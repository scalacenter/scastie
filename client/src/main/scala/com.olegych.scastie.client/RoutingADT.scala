package com.olegych.scastie
package client

import api.{SnippetId, SnippetUserPart}

object Page {
  def fromSnippetId(snippetId: SnippetId): ResourcePage = {
    snippetId match {
      case SnippetId(uuid, None) => AnonymousResource(uuid)
      case SnippetId(uuid, Some(SnippetUserPart(login, None))) => UserResource(login, uuid)
      case SnippetId(uuid, Some(SnippetUserPart(login, Some(update)))) => UserResourceUpdated(login, uuid, update)
    }
  }
}

sealed trait Page
case object Home extends Page
case object Embeded extends Page

sealed trait ResourcePage extends Page

case class AnonymousResource(uuid: String) extends ResourcePage
case class UserResource(login: String, uuid: String) extends ResourcePage
case class UserResourceUpdated(login: String, uuid: String, update: Int) extends ResourcePage

case class EmbeddedAnonymousResource(uuid: String) extends ResourcePage
case class EmbeddedUserResource(login: String, uuid: String) extends ResourcePage
case class EmbeddedUserResourceUpdated(login: String, uuid: String, update: Int) extends ResourcePage
