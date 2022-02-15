package com.olegych.scastie.client

import com.olegych.scastie.api._

object Page {
  def fromSnippetId(snippetId: SnippetId): ResourcePage = {
    snippetId match {
      case SnippetId(uuid, None) =>
        AnonymousResource(uuid)

      case SnippetId(uuid, Some(SnippetUserPart(login, 0))) =>
        UserResource(login, uuid)

      case SnippetId(uuid, Some(SnippetUserPart(login, update))) =>
        UserResourceUpdated(login, uuid, update)
    }
  }
}

sealed trait Page
case object Home extends Page
case object Embedded extends Page

case class TargetTypePage(targetType: ScalaTargetType, code: Option[String]) extends Page
case class TryLibraryPage(dependency: ScalaDependency, project: Project, code: Option[String]) extends Page
case class OldSnippetIdPage(id: Int) extends Page
case class InputsPage(inputs: Inputs) extends Page

sealed trait ResourcePage extends Page

case class AnonymousResource(
    uuid: String
) extends ResourcePage

case class UserResource(
    login: String,
    uuid: String
) extends ResourcePage

case class UserResourceUpdated(
    login: String,
    uuid: String,
    update: Int
) extends ResourcePage

case class EmbeddedAnonymousResource(
    uuid: String
) extends ResourcePage

case class EmbeddedUserResource(
    login: String,
    uuid: String
) extends ResourcePage

case class EmbeddedUserResourceUpdated(
    login: String,
    uuid: String,
    update: Int
) extends ResourcePage
