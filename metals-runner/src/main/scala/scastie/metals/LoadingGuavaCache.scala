package scastie.metals

import com.google.common.cache.{Cache, CacheBuilder}
import cats.effect.Sync
import cats.syntax.all._

class LoadingGuavaCache[F[_], K, V](val underlying: Cache[K, V])(implicit F: Sync[F]) {
  def get(key: K): F[Option[V]] = F.delay(Option(underlying.getIfPresent(key)))
  def put(key: K)(value: V): F[V] = F.delay(underlying.put(key, value)) *> value.pure[F]
  def getOrLoad(key: K)(f: => F[V]): F[V] = get(key) >>= (_.fold(f >>= put(key))(_.pure[F]))
}

object LoadingGuavaCache {

  def apply[F[_]: Sync, K, V](timeoutSeconds: Int): LoadingGuavaCache[F, K, V] =
    val cache0: Cache[K, V] = CacheBuilder
      .newBuilder()
      .expireAfterAccess(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
      .build()

    new LoadingGuavaCache[F, K, V](cache0)
}
