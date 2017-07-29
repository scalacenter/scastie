// package com.olegych.scastie.api

// import com.olegych.scastie.proto.User

// case class SnippetUserPart(login: String, update: Option[Int])
// case class SnippetId(base64UUID: String, user: Option[SnippetUserPart]) {
//   def isOwnedBy(user2: Option[User]): Boolean = {
//     (user, user2) match {
//       case (Some(SnippetUserPart(snippetLogin, _)),
//             Some(User(userLogin, _, _))) =>
//         snippetLogin == userLogin
//       case _ => false
//     }
//   }

//   override def toString: String = url

//   def url: String = {
//     this match {
//       case SnippetId(uuid, None) => uuid
//       case SnippetId(uuid, Some(SnippetUserPart(login, update))) =>
//         s"$login/$uuid/${update.getOrElse(0)}"
//     }
//   }

//   def scalaJsUrl(end: String): String = {
//     val middle = url
//     s"/${Shared.scalaJsHttpPathPrefix}/$middle/$end"
//   }
// }
