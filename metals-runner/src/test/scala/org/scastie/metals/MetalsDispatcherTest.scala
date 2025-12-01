package org.scastie.metals

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
import org.scastie.api._
import org.scastie.buildinfo.BuildInfo
import munit.Assertions
import munit.CatsEffectAssertions
import munit.CatsEffectSuite

class MetalsDispatcherTest extends CatsEffectSuite with Assertions with CatsEffectAssertions {

  private val cache = Cache.expiring[IO, (String, ScastieMetalsOptions), ScastiePresentationCompiler](
    ExpiringCache.Config(expireAfterRead = 30.seconds),
    None
  )

  override val munitTimeout = 120.seconds

  test("single thread metals access") {
    cache.use { cache =>
      val dispatcher = MetalsDispatcher(cache)
      val options    = ScastieMetalsOptions(Set.empty, Scala3(BuildInfo.latestLTS))
      val userUuid   = "test-user-1"
      assertIO(dispatcher.getCompiler(userUuid, options).isRight, true)
    }
  }

  test("parallel metals access for same cache entry") {
    cache.use { cache =>
      val dispatcher = MetalsDispatcher(cache)
      val options    = ScastieMetalsOptions(Set.empty, Scala3(BuildInfo.latestLTS))
      val userUuid   = "test-user-1"
      val tasks = List
        .fill(10)(dispatcher.getCompiler(userUuid, options).semiflatMap(_.complete(ScastieOffsetParams("prin", 4, true))).value)
        .parSequence
      assertIO(tasks.map(_.forall(_.isRight)), true)
    }
  }

  test("cache should properly shutdown presentation compiler") {
    val cache = Cache.expiring[IO, (String, ScastieMetalsOptions), ScastiePresentationCompiler](
      ExpiringCache.Config(expireAfterRead = 2.seconds),
      None
    )

    cache.use { cache =>
      {
        val dispatcher = MetalsDispatcher(cache)
        val options    = ScastieMetalsOptions(Set.empty, Scala3(BuildInfo.latestLTS))
        val userUuid   = "5e460689-36b8-41db-b021-7dd273ecab88"
        val task = for {
          pc     <- dispatcher.getCompiler(userUuid, options)
          _      <- EitherT.right(IO.sleep(4.seconds))
          result <- EitherT.right(pc.complete(ScastieOffsetParams("print", 3, true)))
        } yield { result.items }
        interceptIO[java.util.concurrent.CancellationException](task.value)
      }
    }
  }

  test("caching: same user with same config reuses PC") {
    cache.use { cache =>
      val dispatcher = MetalsDispatcher(cache)
      val options    = ScastieMetalsOptions(Set.empty, Scala3(BuildInfo.latestLTS))
      val userUuid   = "5e460689-36b8-41db-b021-7dd273ecab88"

      for {
        pc1 <- dispatcher.getCompiler(userUuid, options).value
        pc2 <- dispatcher.getCompiler(userUuid, options).value
        _ <- assertIO(IO.pure(pc1.isRight && pc2.isRight), true)
      } yield ()
    }
  }

  test("caching: same user with different config creates separate cache entries") {
    cache.use { cache =>
      val dispatcher = MetalsDispatcher(cache)
      val options1   = ScastieMetalsOptions(Set.empty, Scala3(BuildInfo.latestLTS))
      val options2   = ScastieMetalsOptions(Set.empty, Scala2.default)
      val userUuid   = "5e460689-36b8-41db-b021-7dd273ecab88"

      for {
        pc1 <- dispatcher.getCompiler(userUuid, options1).value
        pc2 <- dispatcher.getCompiler(userUuid, options2).value
        keys <- cache.keys
        _ <- assertIO(IO.pure(pc1.isRight && pc2.isRight), true)
        userKeys = keys.filter(_._1 == userUuid)
        _ <- assertIO(IO.pure(userKeys.size), 2)
      } yield ()
    }
  }

  test("caching: different users with same config have separate PCs") {
    cache.use { cache =>
      val dispatcher = MetalsDispatcher(cache)
      val options    = ScastieMetalsOptions(Set.empty, Scala3(BuildInfo.latestLTS))
      val user1      = "5e460689-36b8-41db-b021-7dd273ecab88"
      val user2      = "7f6a1dc2-d936-4853-9345-161f3617c649"

      for {
        pc1 <- dispatcher.getCompiler(user1, options).value
        pc2 <- dispatcher.getCompiler(user2, options).value
        keys <- cache.keys
        _ <- assertIO(IO.pure(pc1.isRight && pc2.isRight), true)
        _ <- assertIO(IO.pure(keys.size), 2)
      } yield ()
    }
  }

  test("parallel metals access same version") {
    cache.use { cache =>
      val dispatcher = MetalsDispatcher(cache)
      val options    = ScastieMetalsOptions(Set.empty, Scala3(BuildInfo.latestLTS))
      val userUuid   = "test-user-parallel"
      val task       = dispatcher.getCompiler(userUuid, options).value.parReplicateA(10000)
      assertIO(task.map(results => results.nonEmpty && results.forall(_.isRight)), true)
    }
  }

  test("parallel metals access random scala version") {
    val scalaVersions = ScalaVersions.allVersions(ScalaTargetType.Scala3).map(Scala3.apply) ++
      Seq("2.13.9", "2.13.8", "2.12.17").map(Scala2.apply)

    val scalaOptions = scalaVersions.map(v => ScastieMetalsOptions(Set.empty, v))

    cache.use { cache =>
      val dispatcher = MetalsDispatcher(cache)
      val task = List
        .fill(10000)(scala.util.Random.nextInt(scalaVersions.size - 1))
        .map { i =>
          val userUuid = s"test-user-$i"
          dispatcher.getCompiler(userUuid, scalaOptions(i)).value
        }
        .sequence
      assertIO(task.map(results => results.nonEmpty && results.forall(_.isRight)), true)

    }
  }

  test("parallel metals access with dependencies".flaky) {
    val targets = List(
      Scala2.default,
      Scala2(BuildInfo.latest212),
      Scala3.default,
      Js.default
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
      val dispatcher = MetalsDispatcher(cache)
      val task = List
        .fill(10000)(scala.util.Random.nextInt(testCases.size - 1))
        .map { i =>
          val userUuid = s"test-user-$i"
          dispatcher.getCompiler(userUuid, testCases(i)).value
        }
        .sequence
      assertIO(task.map(results => results.nonEmpty && results.forall(_.isRight)), true)
    }
  }
}
