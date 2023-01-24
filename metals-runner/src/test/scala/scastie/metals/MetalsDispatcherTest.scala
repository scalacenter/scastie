package scastie.metals

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters._
import scala.util.Success

import cats.data.EitherT
import cats.effect.implicits.*
import cats.effect.kernel.Outcome
import cats.effect.IO
import cats.effect.IO.asyncForIO
import cats.implicits._
import cats.syntax.all._
import com.evolutiongaming.scache.{Cache, ExpiringCache}
import com.olegych.scastie.api._
import com.olegych.scastie.buildinfo.BuildInfo
import munit.Assertions
import munit.CatsEffectAssertions
import munit.CatsEffectSuite

class MetalsDispatcherTest extends CatsEffectSuite with Assertions with CatsEffectAssertions {
  private val dispatcherF =
    (cache0: Cache[IO, ScastieMetalsOptions, ScastiePresentationCompiler]) => new MetalsDispatcher[IO](cache0)

  private val cache = Cache.expiring[IO, ScastieMetalsOptions, ScastiePresentationCompiler](
    ExpiringCache.Config(expireAfterRead = 30.seconds),
    None
  )

  override val munitTimeout = 60.seconds

  test("single thread metals access") {
    cache.use { cache =>
      val dispatcher = dispatcherF(cache)
      val options    = ScastieMetalsOptions(Set.empty, ScalaTarget.Jvm(BuildInfo.latest3))
      assertIO(dispatcher.getCompiler(options).isRight, true)
    }
  }

  test("cache should properly shutdown presentation compiler") {
    val cache = Cache.expiring[IO, ScastieMetalsOptions, ScastiePresentationCompiler](
      ExpiringCache.Config(expireAfterRead = 2.seconds),
      None
    )

    cache.use { cache =>
      {
        val dispatcher = dispatcherF(cache)
        val options    = ScastieMetalsOptions(Set.empty, ScalaTarget.Jvm(BuildInfo.latest3))
        val task = for {
          pc     <- dispatcher.getCompiler(options)
          _      <- EitherT.right(IO.sleep(4.seconds))
          result <- EitherT.right(pc.complete(ScastieOffsetParams("print", 3, true)))
        } yield { result.getItems.asScala.toList }
        interceptIO[java.util.concurrent.CancellationException](task.value)
      }
    }
  }

  test("parallel metals access same version") {
    cache.use { cache =>
      val dispatcher = dispatcherF(cache)
      val options    = ScastieMetalsOptions(Set.empty, ScalaTarget.Jvm(BuildInfo.latest3))
      val task       = dispatcher.getCompiler(options).value.parReplicateA(10000)
      assertIO(task.map(results => results.nonEmpty && results.forall(_.isRight)), true)
    }
  }

  test("parallel metals access random scala version") {
    val scalaVersions = ScalaVersions.allVersions(ScalaTargetType.Scala3).map(ScalaTarget.Scala3.apply) ++
      Seq("2.13.9", "2.13.8", "2.12.17").map(ScalaTarget.Jvm.apply)

    val scalaOptions = scalaVersions.map(v => ScastieMetalsOptions(Set.empty, v))

    cache.use { cache =>
      val dispatcher = dispatcherF(cache)
      val task = List
        .fill(10000)(scala.util.Random.nextInt(scalaVersions.size - 1))
        .map { i =>
          dispatcher.getCompiler(scalaOptions(i)).value
        }
        .sequence
      assertIO(task.map(results => results.nonEmpty && results.forall(_.isRight)), true)

    }
  }

  test("parallel metals access with dependencies") {
    val targets = List(
      ScalaTarget.Jvm.default,
      ScalaTarget.Jvm(BuildInfo.latest212),
      ScalaTarget.Scala3.default,
      ScalaTarget.Js.default
    )
    val dependencies = List(
      ScalaDependency("org.typelevel", "cats-core", _, "2.9.0"),
      ScalaDependency("org.scalaz", "scalaz-core", _, "7.3.6"),
      ScalaDependency("dev.zio", "zio", _, "2.0.4"),
      ScalaDependency("org.http4s", "http4s-core", _, "0.23.16"),
      ScalaDependency("io.circe", "circe-core", _, "0.14.3"),
      ScalaDependency("co.fs2", "fs2-core", _, "3.4.0"),
      ScalaDependency("io.monix", "monix", _, "3.4.1")
    )

    val testCases = dependencies.flatMap(dep => targets.map(target => ScastieMetalsOptions(Set(dep(target)), target)))
    cache.use { cache =>
      val dispatcher = dispatcherF(cache)
      val task = List
        .fill(10000)(scala.util.Random.nextInt(testCases.size - 1))
        .map { i =>
          dispatcher.getCompiler(testCases(i)).value
        }
        .sequence
      assertIO(task.map(results => results.nonEmpty && results.forall(_.isRight)), true)
    }
  }
}
