package com.olegych.scastie.sclirunner

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterAll

class ScliRunnerTest extends AnyFunSuite with BeforeAndAfterAll {
  
  var scliRunner: Option[ScliRunner] = None

  override protected def beforeAll(): Unit = {
    scliRunner = Some(new ScliRunner)
  }

  

  override protected def afterAll(): Unit = {
    // scliRunner.map(_.end)
    scliRunner = None
  }
}
