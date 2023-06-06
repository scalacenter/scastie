package scastie.endpoints

import sttp.tapir._
import com.olegych.scastie.api._

object SnippetIdUtils {
  type EmbeddedSnippetId = SnippetId
  type NormalSnippetId = SnippetId

  // Migrate to opaque types in the future
  type MaybeEmbeddedSnippet = Either[NormalSnippetId, EmbeddedSnippetId]

  sealed trait FrontPageSnippet { def content: String }
  case class EmbeddedSnippet(content: String) extends FrontPageSnippet
  case class UniversalSnippet(content: String) extends FrontPageSnippet

  implicit class MaybeEmbeddedSnippedExtensions(maybeEmbeddedSnippet: MaybeEmbeddedSnippet) {
    def extractSnippetId: SnippetId = maybeEmbeddedSnippet match {
      case Left(snippetId) => snippetId
      case Right(snippetId) => snippetId
    }

    def user: String = extractSnippetId.user.fold("")(_.login)
    def snippetId: String = extractSnippetId.base64UUID
    def rev: String = extractSnippetId.user.fold("")(_.update.toString)
  }

  def toSnippetId(onlySnippetId: String): SnippetId =
    SnippetId(onlySnippetId, None)
  def toSnippetId(userSnippet: (String, String)): SnippetId =
    SnippetId(userSnippet._2, Some(SnippetUserPart(userSnippet._1)))
  def toSnippetId(userSnippetWithRev: (String, String, Int)): SnippetId =
    SnippetId(userSnippetWithRev._2, Some(SnippetUserPart(userSnippetWithRev._1, userSnippetWithRev._3)))

  def toMaybeSnippetId(onlySnippetId: String): DecodeResult[MaybeEmbeddedSnippet] =
    toMaybeSnippetId(List(onlySnippetId))
  def toMaybeSnippetId(userSnippet: (String, String)): DecodeResult[MaybeEmbeddedSnippet] =
    toMaybeSnippetId(List(userSnippet._1, userSnippet._2))
  def toMaybeSnippetId(userSnippetWithRev: (String, String, String)): DecodeResult[MaybeEmbeddedSnippet] =
    toMaybeSnippetId(List(userSnippetWithRev._1, userSnippetWithRev._2, userSnippetWithRev._3))

  private def toMaybeSnippetId(paths: List[String]): DecodeResult[MaybeEmbeddedSnippet] =
    paths match {
      case init :+ last if last.endsWith(".js") => toSnippetId(init :+ last.stripSuffix(".js")).map(Right(_))
      case other => toSnippetId(other).map(Left(_))
    }

  private def toSnippetId(paths: List[String]): DecodeResult[SnippetId] =
    DecodeResult.fromOption(paths match {
      case user :: base64UUID :: rev :: Nil => rev.toIntOption.map(rev => SnippetId(base64UUID, Some(SnippetUserPart(user, rev))))
      case user :: base64UUID :: Nil => Some(SnippetId(base64UUID, Some(SnippetUserPart(user))))
      case base64UUID :: Nil => Some(SnippetId(base64UUID, None))
      case _ => None
    })

}
