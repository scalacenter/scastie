package scastie.metals

import cats.effect.{ExitCode, IO, IOApp}

object MainIO extends IOApp:
  def run(args: List[String]) = Server.stream[IO].compile.drain.as(ExitCode.Success)
