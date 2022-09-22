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

class MetalsServerTest extends CatsEffectSuite {

  private val server = ScastieMetalsImpl.instance[IO]

  private def testCode(code: String): ScastieOffsetParams = {
    val offset = code.indexOfSlice("@@")
    ScastieOffsetParams(code.filter(_ != '@'), offset)
  }

  private def testCompletion(
    scalaTarget: ScalaTarget = ScalaTarget.Scala3("3.2.0"),
    dependencies: Set[ScalaDependency] = Set(),
    code: String = "",
    expected: Set[String] = Set()
  ) =
    val offsetParamsComplete = testCode(code)
    val request = LSPRequestDTO(ScastieMetalsOptions(dependencies, scalaTarget), offsetParamsComplete)
    import DTOCodecs._
    println(request.asJson)
    val comp = server.complete(request).map(_.getItems.asScala.map(_.getLabel).toSet).value
    assertIO(comp, expected.asRight)

  private def testHover(
    scalaTarget: ScalaTarget = ScalaTarget.Scala3("3.2.0"),
    dependencies: Set[ScalaDependency] = Set(),
    code: String = "",
    expected: MarkupContent = MarkupContent()
  ) =
    val offsetParamsComplete = testCode(code)
    val request = LSPRequestDTO(ScastieMetalsOptions(dependencies, scalaTarget), offsetParamsComplete)
    val comp = server.hover(request).map(_.getContents().getRight()).value
    assertIO(comp, expected.asRight)

  private def testSignatureHelp(
    scalaTarget: ScalaTarget = ScalaTarget.Scala3("3.2.0"),
    dependencies: Set[ScalaDependency] = Set(),
    code: String = "",
    expected: Set[String] = Set()
  ) =
    val offsetParamsComplete = testCode(code)
    val request = LSPRequestDTO(ScastieMetalsOptions(dependencies, scalaTarget), offsetParamsComplete)
    val comp = server.signatureHelp(request).map(_.getSignatures.asScala.map(_.getLabel).toSet).value
    assertIO(comp, expected.asRight)



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
      )
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
      )
    )
  }

  test("Completions with dependencies") {
    val scalaTarget: ScalaTarget = ScalaTarget.Scala3("3.2.0")
    testCompletion(
      scalaTarget = scalaTarget,
      dependencies = Set(ScalaDependency("org.typelevel", "cats-core", scalaTarget, "2.8.0")),
      code =
        """import cats.syntax.all._
          |object M {
          |  def test = "5".asRigh@@
          |}
          """.stripMargin,
      expected = Set(
        "asRight[B]: Either[B, A]",
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
      expected = MarkupContent("markdown", "```scala\ndef println(): Unit\n```")
    )
  }

  test("Simple hover 2") {
    testHover(
      code =
        """object M {
          |  def hello = prin@@t()
          |}
          """.stripMargin,
      expected = MarkupContent("markdown", "```scala\ndef print(x: Any): Unit\n```")
    )
  }

  test("Hover with dependencies") {
    val scalaTarget: ScalaTarget = ScalaTarget.Scala3("3.2.0")
    testHover(
      scalaTarget = scalaTarget,
      dependencies = Set(ScalaDependency("org.typelevel", "cats-core", scalaTarget, "2.8.0")),
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
          |```""".stripMargin)
    )
  }

  test("Simple signature help") {
    testSignatureHelp(
      code =
        """object M {
          |  def hello = println(@@
          |}
          """.stripMargin,
      expected = Set(
        "println(): Unit",
        "println(x: Any): Unit",
      )
    )
  }

  test("Simple SignatureHelp 2") {
    testSignatureHelp(
      code =
        """object M {
          |  def hello = print(@@
          |}
          """.stripMargin,
      expected = Set(
        "print(x: Any): Unit",
      )
    )
  }

  test("Signature help with dependencies") {
    val scalaTarget: ScalaTarget = ScalaTarget.Scala3("3.2.0")
    testSignatureHelp(
      scalaTarget = scalaTarget,
      dependencies = Set(ScalaDependency("org.typelevel", "cats-core", scalaTarget, "2.8.0")),
      code =
        """import cats.syntax.all._
          |object M {
          |  def test = "5".asRight.traverse(@@
          |}
          """.stripMargin,
      expected = Set(
        "traverse[F[_$4], AA >: Any, D](f: String => F[D])(using F: cats.Applicative[F]): F[Either[AA, D]]"
      )
    )
  }


}
