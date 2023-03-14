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

  def shouldNotCompile(run: Future[BspClient.BspClientRun]): List[Problem] = {
    val result = getResultWithTimeout(run)
    result match {
      case Failure(ScliRunner.CompilationError(problems)) => problems
      case _ => throw new AssertionError(s"Expected the code to not compile. Instead, got $result")
    }
  }

  def shouldOutputString(run: Future[BspClient.BspClientRun], str: String): List[String] = {
    val result = getResultWithTimeout(run)
    result match {
      case Success(BspClient.BspClientRun(output)) => {
        if (output.exists(_.contains(str))) output
        else throw new AssertionError(s"Expected the output to contain at least $str. Contained only $output")
      }
      case _ => throw new AssertionError(s"Expected the run to have been run. Got $result")
    }
  }

  def shouldTimeout(run: Future[BspClient.BspClientRun]): Unit = {
    val result = getResultWithTimeout(run)
    result match {
      case Failure(BspClient.FailedRunError("Timeout exceed.")) => ()
      case _ => throw new AssertionError(s"Expected the run to timeout. Instead, got $result")
    }
  }
}
