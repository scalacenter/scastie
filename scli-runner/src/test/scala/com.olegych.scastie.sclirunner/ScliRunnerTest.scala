package com.olegych.scastie.sclirunner

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterAll
import com.olegych.scastie.util.ScastieFileUtil
import java.nio.file.Paths
import com.olegych.scastie.api.SnippetId
import com.olegych.scastie.api.Inputs
import scala.concurrent.Future
import com.olegych.scastie.api.Value

class ScliRunnerTest extends AnyFunSuite with BeforeAndAfterAll {
  
  var scliRunner: Option[ScliRunner] = None

  override protected def beforeAll(): Unit = {
    scliRunner = Some(new ScliRunner)
  }
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

  test("directives are updated") {
    TestUtils.shouldRun(
      run("directive-1")
    )
    TestUtils.shouldRun(
      run("directive-2")
    )
  }

  test("instrumentation is correct") {
    val r = TestUtils.shouldRun(
      run("instrumentation-test")
    )
    
    assert(r.instrumentation.isDefined)
    val content = r.instrumentation.get
    assert(content.exists(
      p => p.position.start == 193 && p.position.end == 197
          && { p.render match {
            case Value("44", "Int") => true
            case _ => false
          } }
    ))
    assert(content.exists(
      p => p.position.start == 70 && p.position.end == 75
          && { p.render match {
            case Value("126", "Int") => true
            case _ => false
          } }
    ))
  }

  test("do not instrument if not need") {
    val r = TestUtils.shouldRun(
      run("normal")
    )
    assert(r.output.mkString.contains("hello!"))
  }

  override protected def afterAll(): Unit = {
    scliRunner.map(_.end)
    scliRunner = None
  }

  def run(file: String, isWorksheet: Boolean = true, onOutput: String => Any = str => ()): Future[Either[ScliRunner.ScliRun, ScliRunner.ScliRunnerError]] = {
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
