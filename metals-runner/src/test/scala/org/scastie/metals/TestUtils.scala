package org.scastie.metals

import scala.jdk.CollectionConverters._

import cats.effect.implicits.*
import cats.effect.IO
import cats.syntax.all._
import com.evolutiongaming.scache.Cache
import org.scastie.api._
import org.scastie.api.{ScalaDependency, ScalaTarget}
import org.scastie.buildinfo.BuildInfo
import munit.Assertions
import munit.CatsEffectAssertions
import org.eclipse.lsp4j.MarkupContent
import org.http4s._
import JavaConverters._
import cats.data.EitherT
import cats.data.OptionT

object TestUtils extends Assertions with CatsEffectAssertions {
  val cache  = Cache.empty[IO, ScastieMetalsOptions, ScastiePresentationCompiler]
  val server = ScastieMetalsImpl.instance[IO](cache)

  type DependencyForVersion = ScalaTarget => ScalaDependency

  val testTargets =
    List(BuildInfo.latestLTS, BuildInfo.stableLTS, BuildInfo.latestNext).map(ScalaTarget.Scala3.apply) ++
      List(BuildInfo.latest213, BuildInfo.latest212).map(ScalaTarget.Jvm.apply)

  val unsupportedVersions = List(BuildInfo.latest211, BuildInfo.latest210).map(Jvm.apply)

  private def testCode(code: String, isWorksheet: Boolean): ScastieOffsetParams = {
    val offset = code.indexOfSlice("@@")
    ScastieOffsetParams(code.filter(_ != '@'), offset, isWorksheet)
  }

  private def createRequest(
    scalaTarget: ScalaTarget,
    dependencies: Set[DependencyForVersion],
    code: String,
    isWorksheet: Boolean = false
  ): LSPRequestDTO =
    val offsetParamsComplete = testCode(code, isWorksheet)
    val dependencies0        = dependencies.map(_.apply(scalaTarget))
    LSPRequestDTO(ScastieMetalsOptions(dependencies0, scalaTarget, code), offsetParamsComplete)

  def getCompat[A](scalaTarget: ScalaTarget, compat: Map[String, A], default: A): A =
    val binaryScalaVersion = scalaTarget.binaryScalaVersion
    val majorVersion       = binaryScalaVersion.split('.').headOption
    if (compat.keys.exists(_ == binaryScalaVersion)) then compat(binaryScalaVersion)
    else if (majorVersion.forall(v => compat.keys.exists(_ == v))) compat(majorVersion.get)
    else default

  def convertScalaCliConfiguration(code: String, expected: Either[FailureType, ScastieMetalsOptions]): IO[Unit] =
    val config = server.isConfigurationSupported(ScastieMetalsOptions(Set(), ScalaCli(BuildInfo.latest3), code)).value
    assertIO(config, expected)

  def convertScalaCliConfiguration(code: String): IO[ScastieMetalsOptions] =
    val config = server.isConfigurationSupported(ScastieMetalsOptions(Set(), ScalaCli(BuildInfo.latest3), code)).toOption
    assertIOBoolean(config.isDefined).flatMap(_ => config.value.map(_.get))

  def testCompletion(
    testTargets: List[ScalaTarget] = testTargets,
    dependencies: Set[DependencyForVersion] = Set(),
    code: String = "",
    expected: Either[FailureType, Set[String]] = Right(Set()),
    compat: Map[String, Either[FailureType, Set[String]]] = Map(),
    isWorksheet: Boolean = false,
  ): IO[List[Unit]] = testTargets.traverse(scalaTarget =>
    val request = createRequest(scalaTarget, dependencies, code, isWorksheet)
    val comp    = server.complete(request).map(_.items.map(item => s"${item.label} ${item.detail}").toSet).value
    assertIO(comp, getCompat(scalaTarget, compat, expected), Left(NoResult(s"Failed for target $scalaTarget")))
  )

  def testCompletionEdit(
    testTargets: List[ScalaTarget] = testTargets,
    dependencies: Set[DependencyForVersion] = Set(),
    code: String,
    expectedCode: String,
    isWorksheet: Boolean,
  ): IO[List[Unit]] = testTargets.traverse(scalaTarget =>
    val request = createRequest(scalaTarget, dependencies, code, isWorksheet)
    val newText = server.complete(request).map(_.items.head).map { completionDTO =>
      val editRange = completionDTO.instructions.editRange
      val offset = code.linesWithSeparators.take(editRange.startLine - 1).map(_.length).sum + editRange.startChar
      request.offsetParams.content.patch(offset, completionDTO.instructions.text, editRange.endChar - editRange.startChar)
    }
    assertIO(newText.value, expectedCode.asRight[FailureType], Left(NoResult(s"Failed for target $scalaTarget")))
  )

  def testHover(
    testTargets: List[ScalaTarget] = testTargets,
    dependencies: Set[DependencyForVersion] = Set(),
    code: String = "",
    expected: Either[FailureType, MarkupContent] = Right(MarkupContent()),
    compat: Map[String, Either[FailureType, MarkupContent]] = Map()
  ): IO[List[Unit]] = testTargets.traverse(scalaTarget =>
    val request = createRequest(scalaTarget, dependencies, code)
    val comp    = server.hover(request).map(_.getContents().getRight()).value
    assertIO(comp, getCompat(scalaTarget, compat, expected), Left(NoResult(s"Failed for target $scalaTarget")))
  )

  case class TestSignatureHelp(label: String, doc: String)

  def testSignatureHelp(
    testTargets: List[ScalaTarget] = testTargets,
    dependencies: Set[DependencyForVersion] = Set(),
    code: String = "",
    expected: Either[FailureType, Set[TestSignatureHelp]] = Right(Set()),
    compat: Map[String, Either[FailureType, Set[TestSignatureHelp]]] = Map()
  ): IO[List[Unit]] = testTargets.traverse(scalaTarget =>
    val request = createRequest(scalaTarget, dependencies, code)
    val comp = server
      .signatureHelp(request)
      .map(
        _.getSignatures.asScala
          .map(signature =>
            TestSignatureHelp(signature.getLabel, signature.getDocumentation().asScala.map(_.getValue).merge)
          )
          .toSet
      )
      .value
    assertIO(comp, getCompat(scalaTarget, compat, expected), Left(NoResult(s"Failed for target $scalaTarget")))
  )

  def testCompletionInfo(
    testTargets: List[ScalaTarget] = testTargets,
    dependencies: Set[DependencyForVersion] = Set(),
    code: String = "",
    expected: List[Either[FailureType, String]] = List(),
    compat: Map[String, List[Either[FailureType, String]]] = Map()
  ): IO[List[Unit]] = testTargets.traverse(scalaTarget =>
    val request = createRequest(scalaTarget, dependencies, code)
    val comp = server.complete(request).getOrElse(ScalaCompletionList(Set(), false)).flatMap { cmps =>
      cmps.items
        .map { cmp =>
          val infoRequest = CompletionInfoRequest(request._1, cmp)
          server.completionInfo(infoRequest).value
        }
        .toList
        .sequence
    }

    assertIO(comp, getCompat(scalaTarget, compat, expected), Left(NoResult(s"Failed for target $scalaTarget")))
  )

}
