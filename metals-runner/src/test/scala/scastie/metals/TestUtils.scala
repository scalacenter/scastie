package scastie.metals

import scala.jdk.CollectionConverters._

import cats.effect.implicits.*
import cats.effect.IO
import cats.syntax.all._
import com.evolutiongaming.scache.Cache
import com.olegych.scastie.api._
import com.olegych.scastie.api.{ScalaDependency, ScalaTarget}
import com.olegych.scastie.buildinfo.BuildInfo
import munit.Assertions
import munit.CatsEffectAssertions
import org.eclipse.lsp4j.MarkupContent
import org.http4s._
import JavaConverters._

object TestUtils extends Assertions with CatsEffectAssertions {
  val cache  = Cache.empty[IO, ScastieMetalsOptions, ScastiePresentationCompiler]
  val server = ScastieMetalsImpl.instance[IO](cache)

  type DependencyForVersion = ScalaTarget => ScalaDependency

  val testTargets = List(BuildInfo.latest3, BuildInfo.stable3).map(ScalaTarget.Scala3.apply) ++
    List(BuildInfo.latest213, BuildInfo.latest212).map(ScalaTarget.Jvm.apply)

  val unsupportedVersions = List(BuildInfo.latest211, BuildInfo.latest210).map(ScalaTarget.Jvm.apply)

  private def testCode(code: String): ScastieOffsetParams = {
    val offset = code.indexOfSlice("@@")
    ScastieOffsetParams(code.filter(_ != '@'), offset, false)
  }

  private def createRequest(
    scalaTarget: ScalaTarget,
    dependencies: Set[DependencyForVersion],
    code: String
  ): LSPRequestDTO =
    val offsetParamsComplete = testCode(code)
    val dependencies0        = dependencies.map(_.apply(scalaTarget))
    LSPRequestDTO(ScastieMetalsOptions(dependencies0, scalaTarget), offsetParamsComplete)

  def getCompat[A](scalaTarget: ScalaTarget, compat: Map[String, A], default: A): A =
    val binaryScalaVersion = scalaTarget.binaryScalaVersion
    val majorVersion       = binaryScalaVersion.split('.').headOption
    if (compat.keys.exists(_ == binaryScalaVersion)) then compat(binaryScalaVersion)
    else if (majorVersion.forall(v => compat.keys.exists(_ == v))) compat(majorVersion.get)
    else default

  def testCompletion(
    testTargets: List[ScalaTarget] = testTargets,
    dependencies: Set[DependencyForVersion] = Set(),
    code: String = "",
    expected: Either[FailureType, Set[String]] = Right(Set()),
    compat: Map[String, Either[FailureType, Set[String]]] = Map()
  ): IO[List[Unit]] = testTargets.traverse(scalaTarget =>
    val request = createRequest(scalaTarget, dependencies, code)
    val comp    = server.complete(request).map(_.getItems.asScala.map(_.getLabel).toSet).value
    assertIO(comp, getCompat(scalaTarget, compat, expected), Left(NoResult(s"Failed for target $scalaTarget")))
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
    val comp = server.complete(request).fold(_ => Set(), _.toSimpleScalaList).flatMap { cmps =>
      cmps
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
