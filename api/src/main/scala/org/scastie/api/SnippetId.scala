package org.scastie.api 

import io.circe.generic.semiauto._
import io.circe._

object User {
  // low tech solution
  val admins: Set[String] = Set("rochala", "julienrf")

  implicit val userEncoder: Encoder[User] = deriveEncoder[User]
  implicit val userDecoder: Decoder[User] = deriveDecoder[User]
}

case class User(login: String, name: Option[String], avatar_url: String) {
  def isAdmin: Boolean = User.admins.contains(login)
}

case class UserData(
  user: User,
  switchableUsers: List[User] = List.empty
) {
  def isAdmin: Boolean = user.isAdmin
}

object UserData {
  implicit val userDataEncoder: Encoder[UserData] = deriveEncoder[UserData]
  implicit val userDataDecoder: Decoder[UserData] = deriveDecoder[UserData]
}

object SnippetUserPart {
  implicit val snippetUserPartEncoder: Encoder[SnippetUserPart] = deriveEncoder[SnippetUserPart]
  implicit val snippetUserPartDecoder: Decoder[SnippetUserPart] = deriveDecoder[SnippetUserPart]
}

case class SnippetUserPart(login: String, update: Int = 0)

object SnippetId {
  def empty: SnippetId = SnippetId("", None)

  implicit val snippetIdEncoder: Encoder[SnippetId] = deriveEncoder[SnippetId]
  implicit val snippetIdDecoder: Decoder[SnippetId] = deriveDecoder[SnippetId]
}

case class SnippetId(base64UUID: String, user: Option[SnippetUserPart]) {
  def isOwnedBy(user2: Option[User]): Boolean = {
    (user, user2) match {
      case (Some(SnippetUserPart(snippetLogin, _)), Some(User(userLogin, _, _))) =>
        snippetLogin == userLogin
      case _ => false
    }
  }

  override def toString: String = url

  def url: String = {
    this match {
      case SnippetId(uuid, None) => uuid
      case SnippetId(uuid, Some(SnippetUserPart(login, update))) =>
        s"$login/$uuid/$update"
    }
  }

  def scalaJsUrl(end: String): String = {
    val middle = url
    s"/api/${Shared.scalaJsHttpPathPrefix}/$middle/$end"
  }
}
