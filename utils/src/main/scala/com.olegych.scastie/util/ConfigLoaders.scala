package com.olegych.scastie.util

import com.typesafe.sslconfig.util.ConfigLoader._
import com.typesafe.sslconfig.util.{ConfigLoader, EnrichedConfig}

import java.nio.file.{Path, Paths}
import scala.language.implicitConversions

object ConfigLoaders {
  implicit def toConfigLoader[A](f: EnrichedConfig => A): ConfigLoader[A] = playConfigLoader.map(f)

  implicit val pathLoader: ConfigLoader[Path] = stringLoader.map(Paths.get(_))
}
