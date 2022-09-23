package scastie.metals

import org.http4s._
import org.http4s.implicits._
import munit.CatsEffectSuite
import fs2.io.file.Files
import fs2.io.file.Path
import io.circe._
import io.circe.syntax._
import cats.effect._
import cats.syntax.all._
import org.eclipse.lsp4j.CompletionList
import scala.jdk.CollectionConverters._
import com.olegych.scastie.api.{ScalaTarget, ScalaDependency}
import org.eclipse.lsp4j.MarkupContent
import com.olegych.scastie.api.ScalaVersions
import com.olegych.scastie.buildinfo.BuildInfo
import TestUtils._


class MetalsServerTest extends CatsEffectSuite {
  private val catsVersion = "2.8.0"

  test("Simple completions") {
    testCompletion(
      code =
        """object M {
          |  def hello = printl@@
          |}
          """.stripMargin,
      expected = Set(
        "println(): Unit",
        "println(x: Any): Unit",
      ).asRight
    )
  }

  test("Simple completions 2") {
    testCompletion(
      code =
        """object M {
          |  def hello = print@@
          |}
          """.stripMargin,
      expected = Set(
        "println(): Unit",
        "println(x: Any): Unit",
        "print(x: Any): Unit",
        "printf(text: String, xs: Any*): Unit"
      ).asRight
    )
  }


  test("Completions with dependencies") {
    testCompletion(
      dependencies = Set(ScalaDependency("org.typelevel", "cats-core", _, catsVersion)),
      code =
        """import cats.syntax.all._
          |object M {
          |  def test = "5".asRigh@@
          |}
          """.stripMargin,
      expected = Set(
        "asRight[B]: Either[B, A]",
      ).asRight,
      compat = Map(
       "2" -> Set("asRight[B]: Either[B,String]").asRight
     )
    )
  }

  test("Simple hover") {
    testHover(
      code =
        """object M {
          |  def hello = prin@@tln()
          |}
          """.stripMargin,
      expected = MarkupContent("markdown", "```scala\ndef println(): Unit\n```").asRight
    )
  }

  test("Simple hover 2") {
    testHover(
      code =
        """object M {
          |  def hello = prin@@t()
          |}
          """.stripMargin,
      expected = MarkupContent("markdown", "```scala\ndef print(x: Any): Unit\n```").asRight
    )
  }

  test("Hover with dependencies") {
    testHover(
      dependencies = Set(ScalaDependency("org.typelevel", "cats-core", _, catsVersion)),
      code =
        """import cats.syntax.all._
          |object M {
          |  def test = "5".asRig@@ht
          |}
          """.stripMargin,
      expected = MarkupContent("markdown",
        """**Expression type**:
          |```scala
          |Either[Nothing, String]
          |```
          |**Symbol signature**:
          |```scala
          |def asRight[B]: Either[B, String]
          |```""".stripMargin).asRight,
      compat = Map(
        "2" -> MarkupContent("markdown",
        """**Expression type**:
          |```scala
          |Either[Nothing,String]
          |```
          |**Symbol signature**:
          |```scala
          |def asRight[B]: Either[B,String]
          |```""".stripMargin).asRight
        )
    )
  }

  test("Simple signature help") {
    testSignatureHelp(
      code =
        """object M {
          |  def hello = println(@@)
          |}
          """.stripMargin,
      expected = Set(
        "println(): Unit",
        "println(x: Any): Unit",
      ).asRight
    )
  }

  test("Simple SignatureHelp 2") {
    testSignatureHelp(
      code =
        """object M {
          |  def hello = print(@@)
          |}
          """.stripMargin,
      expected = Set(
        "print(x: Any): Unit",
      ).asRight
    )
  }

  test("Signature help with dependencies") {
    testSignatureHelp(
      dependencies = Set(ScalaDependency("org.typelevel", "cats-core", _, catsVersion)),
      code =
        """import cats.syntax.all._
          |object M {
          |  def test = "5".asRight.traverse(@@)
          |}
          """.stripMargin,
      expected = Set(
        "traverse[F[_$4], AA >: Any, D](f: String => F[D])(using F: cats.Applicative[F]): F[Either[AA, D]]"
      ).asRight,
      compat = Map(
        "2" -> Set("traverse[F[_], AA >: B, D](f: String => F[D])(implicit F: Applicative[F]): F[Either[AA,D]]").asRight
      )
    )
  }

  test("No completions for unsupported scala versions") {
    testCompletion(
      testTargets = unsupportedVersions,
      code =
        """object M {
          |  def hello = printl@@
          |}
          """.stripMargin,
        compat =  unsupportedVersions.map(v =>
            v.binaryScalaVersion -> s"Interactive features are not supported for Scala ${v.binaryScalaVersion}.".asLeft
        ).toMap
    )
  }

  test("No hovers for unsupported scala versions") {
    testHover(
      testTargets = unsupportedVersions,
      code =
        """object M {
          |  def hello = prin@@tln()
          |}
          """.stripMargin,
        compat =  unsupportedVersions.map(v =>
            v.binaryScalaVersion -> s"Interactive features are not supported for Scala ${v.binaryScalaVersion}.".asLeft
        ).toMap
    )
  }

  test("No signatureHelps for unsupported scala versions") {
    testSignatureHelp(
      testTargets = unsupportedVersions,
      code =
        """object M {
          |  def hello = println(@@)
          |}
          """.stripMargin,
        compat =  unsupportedVersions.map(v =>
            v.binaryScalaVersion -> s"Interactive features are not supported for Scala ${v.binaryScalaVersion}.".asLeft
        ).toMap
    )
  }

}
