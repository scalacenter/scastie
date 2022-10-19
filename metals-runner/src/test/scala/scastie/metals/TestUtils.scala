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
import munit.CatsEffectAssertions
import munit.CatsEffectSuitePlatform
import munit.Assertions
import com.olegych.scastie.api._
import JavaConverters._
import cats.implicits._

object TestUtils extends Assertions with CatsEffectAssertions {
  private val server = ScastieMetalsImpl.instance[IO]

  type DependencyForVersion = ScalaTarget => ScalaDependency

  val testTargets = List(BuildInfo.latest3, BuildInfo.stable3).map(ScalaTarget.Scala3.apply) ++
    List(BuildInfo.latest213, BuildInfo.latest212).map(ScalaTarget.Jvm.apply)

  val unsupportedVersions = List(BuildInfo.latest211, BuildInfo.latest210).map(ScalaTarget.Jvm.apply)

  private def testCode(code: String): ScastieOffsetParams = {
    val offset = code.indexOfSlice("@@")
    ScastieOffsetParams(code.filter(_ != '@'), offset)
  }

  private def createRequest(
    scalaTarget: ScalaTarget,
    dependencies: Set[DependencyForVersion],
    code: String
  ): LSPRequestDTO =
    val offsetParamsComplete = testCode(code)
    val dependencies0 = dependencies.map(_.apply(scalaTarget))
    LSPRequestDTO(ScastieMetalsOptions(dependencies0, scalaTarget), offsetParamsComplete)

  def getCompat[A](scalaTarget: ScalaTarget, compat: Map[String, A], default: A): A =
    val binaryScalaVersion = scalaTarget.binaryScalaVersion
    val majorVersion = binaryScalaVersion.split('.').headOption
    if (compat.keys.exists(_ == binaryScalaVersion)) then
      compat(binaryScalaVersion)
    else if (majorVersion.forall(v => compat.keys.exists(_ == v)))
      compat(majorVersion.get)
    else
      default

  def testCompletion(
    testTargets: List[ScalaTarget] = testTargets,
    dependencies: Set[DependencyForVersion] = Set(),
    code: String = "",
    expected: Either[String, Set[String]] = Right(Set()),
    compat: Map[String, Either[String, Set[String]]] = Map()
  ): IO[List[Unit]] =
    testTargets.traverse (scalaTarget =>
      val request = createRequest(scalaTarget, dependencies, code)
      val comp = server.complete(request).map(_.getItems.asScala.map(_.getLabel).toSet).value
      assertIO(comp, getCompat(scalaTarget, compat, expected), s"Failed for target $scalaTarget")
    )

  def testHover(
    testTargets: List[ScalaTarget] = testTargets,
    dependencies: Set[DependencyForVersion] = Set(),
    code: String = "",
    expected: Either[String, MarkupContent] = Right(MarkupContent()),
    compat: Map[String, Either[String, MarkupContent]] = Map()
  ): IO[List[Unit]] =
    testTargets.traverse (scalaTarget =>
      val request = createRequest(scalaTarget, dependencies, code)
      val comp = server.hover(request).map(_.getContents().getRight()).value
      assertIO(comp, getCompat(scalaTarget, compat, expected), s"Failed for target $scalaTarget")
    )

  def testSignatureHelp(
    testTargets: List[ScalaTarget] = testTargets,
    dependencies: Set[DependencyForVersion] = Set(),
    code: String = "",
    expected: Either[String, Set[String]] = Right(Set()),
    compat: Map[String, Either[String, Set[String]]] = Map()
  ): IO[List[Unit]] =
    testTargets.traverse (scalaTarget =>
      val request = createRequest(scalaTarget, dependencies, code)
      val comp = server.signatureHelp(request).map(_.getSignatures.asScala.map(_.getLabel).toSet).value
      assertIO(comp, getCompat(scalaTarget, compat, expected), s"Failed for target $scalaTarget")
    )

  def testCompletionInfo(
    testTargets: List[ScalaTarget] = testTargets,
    dependencies: Set[DependencyForVersion] = Set(),
    code: String = "",
    expected: List[Either[String, String]] = List(),
    compat: Map[String, List[Either[String, String]]] = Map()
  ): IO[List[Unit]] =
    testTargets.traverse (scalaTarget =>
      val request = createRequest(scalaTarget, dependencies, code)
      val comp = server.complete(request).fold(_ => Set(), _.toSimpleScalaList).flatMap { cmps =>
        cmps.map { cmp =>
          val infoRequest = CompletionInfoRequest(request._1, cmp)
          server.completionInfo(infoRequest).value
        }.toList.sequence
      }

      assertIO(comp, getCompat(scalaTarget, compat, expected), s"Failed for target $scalaTarget")

    )

}
