package org.scastie.metals

import scala.jdk.CollectionConverters._

import cats.effect._
import cats.syntax.all._
import org.scastie.api._
import org.scastie.buildinfo.BuildInfo
import munit.CatsEffectSuite
import org.eclipse.lsp4j.MarkupContent
import org.http4s._
import TestUtils._

class MetalsServerTest extends CatsEffectSuite {
  private val catsVersion = "2.8.0"

  test("Simple completions") {
    testCompletion(
      code = """object M {
               |  def hello = printl@@
               |}
          """.stripMargin,
      expected = Set(
        "println"
      ).asRight
    )
  }

  test("Simple completions 2") {
    testCompletion(
      code = """object M {
               |  def hello = print@@
               |}
          """.stripMargin,
      expected = Set(
        "print",
        "printf",
        "println"
      ).asRight
    )
  }

  test("Completions with dependencies") {
    testCompletion(
      dependencies = Set(ScalaDependency("org.typelevel", "cats-core", _, catsVersion)),
      code = """import cats.syntax.all._
               |object M {
               |  def test = "5".asRigh@@
               |}
          """.stripMargin,
      expected = Set(
        "asRight"
      ).asRight,
      compat = Map(
        "2" -> Set("asRight").asRight
      )
    )
  }

  test("Completions with dependencies cross version") {
    testCompletion(
      testTargets = List(Scala3.default),
      dependencies = Set(_ => ScalaDependency("org.typelevel", "cats-core", Jvm.default, catsVersion)),
      code = """import cats.syntax.all._
               |object M {
               |  def test = "5".asRigh@@
               |}
          """.stripMargin,
      expected = Set(
        "asRight"
      ).asRight
    )
  }

  test("Completions item info") {
    testCompletionInfo(
      code = """object M {
               |  printl@@
               |}
          """.stripMargin,
      expected = List(
        Right("Prints a newline character on the default output."),
        Right("""Prints out an object to the default output, followed by a newline character.
                |
                |
                |**Parameters**
                |- `x`: the object to print.""".stripMargin)
      )
    )
  }

  test("Completions item info with dependencies") {
    testCompletionInfo(
      testTargets = List(Scala3("3.2.0")),
      dependencies = Set(ScalaDependency("org.typelevel", "cats-core", _, catsVersion)),
      code = """import cats.syntax.all._
               |object M {
               |  def test = "5".asRigh@@
               |}
          """.stripMargin,
      expected = List(
        Right("Wrap a value in `Right`.")
      )
    )
  }

  test("Completion infos with dependencies cross version") {
    testCompletionInfo(
      testTargets = List(Scala3.default),
      dependencies = Set(_ => ScalaDependency("org.typelevel", "cats-core", Jvm.default, catsVersion)),
      code = """import cats.syntax.all._
               |object M {
               |  def test = "5".asRigh@@
               |}
          """.stripMargin,
      expected = List(
        "Wrap a value in `Right`.".asRight
      )
    )
  }

  test("Simple hover") {
    testHover(
      code = """object M {
               |  def hello = prin@@tln()
               |}
          """.stripMargin,
      expected = MarkupContent(
        "markdown",
        "```scala\ndef println(): Unit\n```\nPrints a newline character on the default output."
      ).asRight
    )
  }

  test("Simple hover 2") {
    testHover(
      code = """object M {
               |  def hello = prin@@t()
               |}
          """.stripMargin,
      expected = MarkupContent(
        "markdown",
        "```scala\ndef print(x: Any): Unit\n```\nPrints an object to `out` using its `toString` method.\n\n\n**Parameters**\n- `x`: the object to print; may be null."
      ).asRight
    )
  }

  test("Hover with dependencies") {
    testHover(
      dependencies = Set(ScalaDependency("org.typelevel", "cats-core", _, catsVersion)),
      code = """import cats.syntax.all._
               |object M {
               |  def test = "5".asRig@@ht
               |}
          """.stripMargin,
      expected = MarkupContent(
        "markdown",
        """**Expression type**:
          |```scala
          |Either[Nothing, String]
          |```
          |**Symbol signature**:
          |```scala
          |def asRight[B]: Either[B, String]
          |```
          |Wrap a value in `Right`.""".stripMargin
      ).asRight,
      compat = Map(
        "2" -> MarkupContent(
          "markdown",
          """**Expression type**:
            |```scala
            |Either[Nothing,String]
            |```
            |**Symbol signature**:
            |```scala
            |def asRight[B]: Either[B,String]
            |```
            |Wrap a value in `Right`.""".stripMargin
        ).asRight
      )
    )
  }

  test("Hover with dependencies cross version") {
    testHover(
      testTargets = List(Scala3.default),
      dependencies = Set(_ => ScalaDependency("org.typelevel", "cats-core", Jvm.default, catsVersion)),
      code = """import cats.syntax.all._
               |object M {
               |  def test = "5".asRigh@@t
               |}
          """.stripMargin,
      expected = MarkupContent(
        "markdown",
        """**Expression type**:
          |```scala
          |Either[Nothing, String]
          |```
          |**Symbol signature**:
          |```scala
          |def asRight[B]: Either[B, String]
          |```
          |Wrap a value in `Right`.""".stripMargin
      ).asRight
    )
  }

  test("Simple signature help") {
    testSignatureHelp(
      code = """object M {
               |  def hello = println(@@)
               |}
          """.stripMargin,
      expected = Set(
        TestSignatureHelp(
          "println(x: Any): Unit",
          """Prints out an object to the default output, followed by a newline character.
            |
            |
            |**Parameters**
            |- `x`: the object to print.""".stripMargin
        ),
        TestSignatureHelp("println(): Unit", "Prints a newline character on the default output.")
      ).asRight
    )
  }

  test("Simple SignatureHelp 2") {
    testSignatureHelp(
      code = """object M {
               |  def hello = print(@@)
               |}
          """.stripMargin,
      expected = Set(
        TestSignatureHelp(
          "print(x: Any): Unit",
          """Prints an object to `out` using its `toString` method.
            |
            |
            |**Parameters**
            |- `x`: the object to print; may be null.""".stripMargin
        )
      ).asRight
    )
  }

  test("Signature help with dependencies") {
    testSignatureHelp(
      dependencies = Set(ScalaDependency("org.typelevel", "cats-core", _, catsVersion)),
      code = """import cats.syntax.all._
               |object M {
               |  def test = List().collectSomeFold(@@)
               |}
          """.stripMargin,
      expected = Set(
        TestSignatureHelp(
          "collectSomeFold[M](f: Any => Option[M])(using F: Foldable[List], M: Monoid[M]): M",
          """Tear down a subset of this structure using a `A => Option[M]`.
            |
            |```
            |scala> import cats.syntax.all._
            |scala> val xs = List(1, 2, 3, 4)
            |scala> def f(n: Int): Option[Int] = if (n % 2 == 0) Some(n) else None
            |scala> xs.collectFoldSome(f)
            |res0: Int = 6
            |```""".stripMargin
        )
      ).asRight,
      compat = Map(
        "2" -> Set(
          TestSignatureHelp(
            "collectSomeFold[M](f: A => Option[M])(implicit F: Foldable[List], M: Monoid[M]): M",
            """Tear down a subset of this structure using a `A => Option[M]`.
              |
              |```
              |scala> import cats.syntax.all._
              |scala> val xs = List(1, 2, 3, 4)
              |scala> def f(n: Int): Option[Int] = if (n % 2 == 0) Some(n) else None
              |scala> xs.collectFoldSome(f)
              |res0: Int = 6
              |```""".stripMargin
          )
        ).asRight
      )
    )
  }

  test("No completions for unsupported scala versions") {
    testCompletion(
      testTargets = unsupportedVersions,
      code = """object M {
               |  def hello = printl@@
               |}
          """.stripMargin,
      compat = unsupportedVersions
        .map(v =>
          v.binaryScalaVersion -> PresentationCompilerFailure(
            s"Interactive features are not supported for Scala ${v.binaryScalaVersion}."
          ).asLeft
        )
        .toMap
    )
  }

  test("No hovers for unsupported scala versions") {
    testHover(
      testTargets = unsupportedVersions,
      code = """object M {
               |  def hello = prin@@tln()
               |}
          """.stripMargin,
      compat = unsupportedVersions
        .map(v =>
          v.binaryScalaVersion -> PresentationCompilerFailure(
            s"Interactive features are not supported for Scala ${v.binaryScalaVersion}."
          ).asLeft
        )
        .toMap
    )
  }

  test("No signatureHelps for unsupported scala versions") {
    testSignatureHelp(
      testTargets = unsupportedVersions,
      code = """object M {
               |  def hello = println(@@)
               |}
          """.stripMargin,
      compat = unsupportedVersions
        .map(v =>
          v.binaryScalaVersion -> PresentationCompilerFailure(
            s"Interactive features are not supported for Scala ${v.binaryScalaVersion}."
          ).asLeft
        )
        .toMap
    )
  }

  test("No completions for illegal snippet") {
    testCompletion(
      code = """object M {
               |  test hello = printl@@
               |}
          """.stripMargin,
      expected = Set().asRight
    )
  }

  test("No hovers for illegal snippet") {
    testHover(
      code = """object M {
               |  prin@@
               |}
          """.stripMargin,
      expected = NoResult(s"There is no hover for given position").asLeft
    )
  }

  test("No signatureHelps for illegal snippet") {
    testSignatureHelp(
      code = """object M {
               |  printl(@@)
               |}
          """.stripMargin,
      expected = Set().asRight
    )
  }

  test("Scala-CLI: Completion with dependency given with `import dep` directives") {
    testCompletion(
      testTargets = List(ScalaCli.default),
      code = """//> using dep "com.lihaoyi::os-lib:0.9.1"
            |object M {
            |   os.pw@@
            |}
          """.stripMargin,
      expected = Set("pwd: Path").asRight
    )
  }

  test("Scala-CLI: Completion with dependency given with `import lib` directives") {
    testCompletion(
      testTargets = List(ScalaCli.default),
      code = """//> using lib "com.lihaoyi::os-lib:0.9.1"
            |object M {
            |   os.pw@@
            |}
          """.stripMargin,
      expected = Set("pwd: Path").asRight
    )
  }

  test("Scala-CLI: Hover on a dependency function works") {
    testCompletionInfo(
      testTargets = List(ScalaCli.default),
      code = """//> using lib "com.lihaoyi::os-lib:0.9.1"
            |object M {
            |   os.pw@@d
            |}
          """.stripMargin,
      expected = List(
        "The current working directory for this process.".asRight
      )
    )
  }
}
