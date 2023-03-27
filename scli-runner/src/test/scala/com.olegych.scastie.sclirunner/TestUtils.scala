package com.olegych.scastie.sclirunner

import scala.jdk.FutureConverters._
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import com.olegych.scastie.api.Problem

object TestUtils {
  def getResultWithTimeout[T](run: Future[T]) = {
    Try(Await.result(run, Duration(35, TimeUnit.SECONDS)))
  }

  def shouldNotCompile(run: Future[Either[ScliRunner.ScliRun, ScliRunner.ScliRunnerError]]): List[Problem] = {
    val result = getResultWithTimeout(run)
    result match {
      case Success(Right(ScliRunner.CompilationError(problems, _))) => problems
      case _ => throw new AssertionError(s"Expected the code to not compile. Instead, got $result")
    }
  }

  def shouldOutputString(run: Future[Either[ScliRunner.ScliRun, ScliRunner.ScliRunnerError]], str: String): ScliRunner.ScliRun = {
    val result = getResultWithTimeout(run)
    result match {
      case Success(Left(x @ ScliRunner.ScliRun(output, instrumentations, _))) => {
        if (output.exists(_.contains(str))) x
        else throw new AssertionError(s"Expected the output to contain at least $str. Contained only $output")
      }
      case _ => throw new AssertionError(s"Expected the run to have been run. Got $result")
    }
  }

  def shouldRun(run: Future[Either[ScliRunner.ScliRun, ScliRunner.ScliRunnerError]]) = {
    shouldOutputString(run, "")
  }

  def shouldTimeout(run: Future[Either[ScliRunner.ScliRun, ScliRunner.ScliRunnerError]]): Unit = {
    shouldOutputString(run, "Timeout exceeded.")
  }
}
