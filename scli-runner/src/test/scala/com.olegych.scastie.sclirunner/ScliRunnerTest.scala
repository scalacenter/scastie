package com.olegych.scastie.sclirunner

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterAll
import com.olegych.scastie.util.ScastieFileUtil
import java.nio.file.Paths
import com.olegych.scastie.api.SnippetId
import com.olegych.scastie.api.Inputs
import scala.concurrent.Future

class ScliRunnerTest extends AnyFunSuite with BeforeAndAfterAll {
  
  var scliRunner: Option[ScliRunner] = None

  override protected def beforeAll(): Unit = {
    scliRunner = Some(new ScliRunner)
  }

  // Make some test api
  // On changing directives
  // Directives are taken into account
  test("forward compile errors") {
    TestUtils.shouldNotCompile(
      run("non-compilable")
    )
  }

  test("should timeout on >30 seconds scripts") {
    TestUtils.shouldTimeout(
      run("too-long")
    )
  }

  override protected def afterAll(): Unit = {
    scliRunner.map(_.end)
    scliRunner = None
  }

  def run(file: String, isWorksheet: Boolean = true, onOutput: String => Any = str => ()): Future[BspClient.BspClientRun] = {
    val f = ScastieFileUtil.slurp(Paths.get("scli-runner", "src", "test", "resources", s"$file.scala"))

    if (scliRunner.isEmpty) throw new IllegalStateException("scli-runner is not defined")
    if (f.isEmpty) throw new IllegalArgumentException(s"Test file $file does not exist.") 

    scliRunner.get.runTask(
      ScliRunner.ScliTask(
        SnippetId("1", None),
        Inputs.default.copy(_isWorksheetMode = isWorksheet, code = f.get),
        "1.1.1.1",
        None
      )
    , onOutput)
  }
}
