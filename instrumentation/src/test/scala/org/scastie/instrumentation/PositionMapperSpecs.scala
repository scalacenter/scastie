package org.scastie
package instrumentation

import scala.meta._

import org.scalatest.funsuite.AnyFunSuite
import RuntimeConstants._

class PositionMapperSpecs extends AnyFunSuite {

  test("identity mapping for identical input") {
    val code0 = s"""|val x = 42
                    |val y = x + 1
                    |""".stripMargin
    val code1 = s"""|val x = 42
                    |val y = x + 1
                    |""".stripMargin

    val positionMapper = PositionMapper(code1)

    assert(positionMapper.mapLine(1) == 1)        // val x = 42
    assert(positionMapper.mapLine(2) == 2)        // val y = x + 1

    assert(positionMapper.mapColumn(1, 5) == 5)   // no offset
    assert(positionMapper.mapColumn(2, 10) == 10) // no offset
  }

  test("mapping with simple instrumentation") {
    val code0 = s"""|println("test1")
                    |val y = 1
                    |println("test2")
                    |""".stripMargin

    val code1 = s"""|import $runtimePackage.*
                    |object $instrumentedObject extends ScastieApp with $instrumentationRecorderT {
                    |scala.Predef.locally {
                    |$$doc.startStatement(0, 16);
                    |val $$t = println("test1"); 
                    |$$doc.binder($runtimeT.render($$t), 0, 16);
                    |$$doc.endStatement();
                    |$$t}
                    |val y = 1
                    |scala.Predef.locally {
                    |$$doc.startStatement(26, 42);
                    |val $$t = println("test2"); 
                    |$$doc.binder($runtimeT.render($$t), 26, 42);
                    |$$doc.endStatement();
                    |$$t}
                    |}
                    |""".stripMargin

    val positionMapper = PositionMapper(code1)

    assert(positionMapper.mapLine(5) == 1)  // val $t = println("test1");
    assert(positionMapper.mapLine(9) == 2)  // val y = 1
    assert(positionMapper.mapLine(12) == 3) // val $t = println("test2");

    assert(positionMapper.mapColumn(9, 5) == 5)   // no offset
    assert(positionMapper.mapColumn(12, 15) == 6) // offset of 9
  }

  test("mapping with experimental imports extracted") {
    val code0 = s"""|import language.experimental.captureChecking
                    |println("test")
                    |val x = 1
                    |""".stripMargin

    val code1 =
      s"""|import $runtimePackage.*
          |import language.experimental.captureChecking
          |object $instrumentedObject extends ScastieApp with $instrumentationRecorderT {                                               
          |
          |scala.Predef.locally {
          |$$doc.startStatement(0, 15);
          |val $$t = println("test"); 
          |$$doc.binder($runtimeT.render($$t), 0, 15);
          |$$doc.endStatement();
          |$$t}
          |val x = 1
          |}
          |""".stripMargin

    val positionMapper = PositionMapper(code1)

    assert(positionMapper.mapLine(2) == 1)  // experimental import
    assert(positionMapper.mapLine(7) == 2)  // val $t = println("test");
    assert(positionMapper.mapLine(11) == 3) // val x = 1

    assert(positionMapper.mapColumn(7, 15) == 6) // offset of 9
    assert(positionMapper.mapColumn(11, 3) == 3) // no offset
  }

  test("mapping with multiline expressions") {
    val code0 = s"""|println:
                    |  "multiline"
                    |val x = 1
                    |""".stripMargin

    val code1 = s"""|import $runtimePackage.*
                    |object $instrumentedObject extends ScastieApp with $instrumentationRecorderT {
                    |scala.Predef.locally {
                    |$$doc.startStatement(0, 25);
                    |val $$t = println:
                    |  "multiline"; 
                    |$$doc.binder($runtimeT.render($$t), 0, 25);
                    |$$doc.endStatement();
                    |$$t}
                    |val x = 1
                    |}
                    |""".stripMargin

    val positionMapper = PositionMapper(code1)

    assert(positionMapper.mapLine(5) == 1)  // val $t = println:
    assert(positionMapper.mapLine(6) == 2)  // "multiline";
    assert(positionMapper.mapLine(10) == 3) // val x = 1

    // Test column mapping
    assert(positionMapper.mapColumn(5, 12) == 3) // offset of 9
    assert(positionMapper.mapColumn(6, 5) == 5)  // no offset
  }

  test("mapping with empty lines and comments") {
    val code0 = s"""|// Comment 1
                    |
                    |println("test")
                    |// Comment 2
                    |val x = 1
                    |""".stripMargin

    val code1 = s"""|import $runtimePackage.*
                    |object $instrumentedObject extends ScastieApp with $instrumentationRecorderT {
                    |// Comment 1
                    |
                    |scala.Predef.locally {
                    |$$doc.startStatement(15, 31);
                    |val $$t = println("test"); 
                    |$$doc.binder($runtimeT.render($$t), 15, 31);
                    |$$doc.endStatement();
                    |$$t}
                    |// Comment 2
                    |val x = 1
                    |}
                    |""".stripMargin

    val positionMapper = PositionMapper(code1)

    assert(positionMapper.mapLine(3) == 1)  // Comment 1
    assert(positionMapper.mapLine(4) == 2)  // empty line
    assert(positionMapper.mapLine(7) == 3)  // val $t = println("test");
    assert(positionMapper.mapLine(11) == 4) // Comment 2
    assert(positionMapper.mapLine(12) == 5) // val x = 1

    assert(positionMapper.mapColumn(7, 20) == 11) // offset of 9
  }

  test("mapping with mixed imports") {
    val code0 = s"""|import scala.util.Random
                    |import language.experimental.captureChecking
                    |val r = Random.nextInt()
                    |""".stripMargin

    val code1 = s"""|import $runtimePackage.*
                    |import language.experimental.captureChecking
                    |object $instrumentedObject extends ScastieApp with $instrumentationRecorderT {
                    |import scala.util.Random   
                    |                                       
                    |val r = Random.nextInt(); 
                    |}
                    |""".stripMargin

    val positionMapper = PositionMapper(code1)

    assert(positionMapper.mapLine(4) == 1) // import scala.util.Random
    assert(positionMapper.mapLine(6) == 3) // val r = Random.nextInt();

    assert(positionMapper.mapColumn(6, 10) == 10) // no offset
  }

  test("empty original code") {
    val code0 = ""
    val code1 = s"""|import $runtimePackage.*
                    |object $instrumentedObject extends ScastieApp with $instrumentationRecorderT {
                    |}
                    |""".stripMargin

    val positionMapper = PositionMapper(code1)

    assert(positionMapper.mapLine(1) == 1)
    assert(positionMapper.mapLine(2) == 1)
    assert(positionMapper.mapLine(3) == 1)

    assert(positionMapper.mapColumn(1, 5) == 5)
  }

  test("single line code") {
    val code0 = "println(42)"

    val code1 = s"""|import $runtimePackage.*
                    |object $instrumentedObject extends ScastieApp with $instrumentationRecorderT {
                    |scala.Predef.locally {
                    |$$doc.startStatement(0, 11);
                    |val $$t = println(42); 
                    |$$doc.binder($runtimeT.render($$t), 0, 11);
                    |$$doc.endStatement();
                    |$$t}
                    |}
                    |""".stripMargin

    val positionMapper = PositionMapper(code1)

    assert(positionMapper.mapLine(5) == 1) // val $t = println(42);

    assert(positionMapper.mapColumn(5, 15) == 6) // offset of 9
  }

  test("line numbers beyond input") {
    val code0 = "val x = 1"
    val code1 = "val x = 1"

    val positionMapper = PositionMapper(code1)

    assert(positionMapper.mapLine(10) == 10)
    assert(positionMapper.mapLine(100) == 100)

    // Test column mapping for lines beyond input
    assert(positionMapper.mapColumn(10, 5) == 5)
    assert(positionMapper.mapColumn(100, 20) == 20)
  }

  test("complex real-world example") {
    val code0 = s"""|import scala.concurrent.Future
                    |import language.experimental.captureChecking
                    |
                    |// Setup
                    |val data = List(1, 2, 3)
                    |
                    |// Processing
                    |data.foreach { x =>
                    |  println(s"Processing: $$x")
                    |}
                    |
                    |val result = data.map(_ * 2)
                    |println(result)
                    |""".stripMargin

    val code1 = s"""|import $runtimePackage.*
                    |import language.experimental.captureChecking
                    |object $instrumentedObject extends ScastieApp with $instrumentationRecorderT {
                    |import scala.concurrent.Future
                    |
                    |                                                 
                    |// Setup
                    |val data = List(1, 2, 3)
                    |
                    |// Processing
                    |scala.Predef.locally {
                    |$$doc.startStatement(25, 75);
                    |val $$t = data.foreach { x =>
                    |  println(s"Processing: $$x")
                    |}; 
                    |$$doc.binder($runtimeT.render($$t), 25, 75);
                    |$$doc.endStatement();
                    |$$t}
                    |
                    |val result = data.map(_ * 2)
                    |scala.Predef.locally {
                    |$$doc.startStatement(106, 120);
                    |val $$t = println(result); 
                    |$$doc.binder($runtimeT.render($$t), 106, 120);
                    |$$doc.endStatement();
                    |$$t}
                    |}
                    |""".stripMargin

    val positionMapper = PositionMapper(code1)

    assert(positionMapper.mapLine(4) == 1)   // import scala.concurrent.Future
    assert(positionMapper.mapLine(7) == 4)   // Setup comment
    assert(positionMapper.mapLine(8) == 5)   // val data = List(1, 2, 3)
    assert(positionMapper.mapLine(10) == 7)  // Processing comment
    assert(positionMapper.mapLine(13) == 8)  // val $t = data.foreach...
    assert(positionMapper.mapLine(20) == 12) // val result = data.map(_ * 2)
    assert(positionMapper.mapLine(23) == 13) // val $t = println(result);

    assert(positionMapper.mapColumn(13, 15) == 6)  // offset of 9
    assert(positionMapper.mapColumn(23, 20) == 11) // offset of 9
  }

  test("mapping with multiple identical expressions") {
    val code0 = s"""|println("test")
                    |val y = 2
                    |println("test")
                    |val z = 3
                    |""".stripMargin

    val code1 = s"""|import $runtimePackage.*
                    |object $instrumentedObject extends ScastieApp with $instrumentationRecorderT {
                    |scala.Predef.locally {
                    |$$doc.startStatement(0, 15);
                    |val $$t = println("test"); 
                    |$$doc.binder($runtimeT.render($$t), 0, 15);
                    |$$doc.endStatement();
                    |$$t}
                    |val y = 2
                    |scala.Predef.locally {
                    |$$doc.startStatement(25, 40);
                    |val $$t = println("test"); 
                    |$$doc.binder($runtimeT.render($$t), 25, 40);
                    |$$doc.endStatement();
                    |$$t}
                    |val z = 3
                    |}
                    |""".stripMargin

    val positionMapper = PositionMapper(code1)

    assert(positionMapper.mapLine(5) == 1)  // val $t = println("test"); #1
    assert(positionMapper.mapLine(12) == 3) // val $t = println("test"); #2
    assert(positionMapper.mapLine(9) == 2)  // val y = 2
    assert(positionMapper.mapLine(16) == 4) // val z = 3

    assert(positionMapper.mapColumn(5, 18) == 9)  // offset of 9
    assert(positionMapper.mapColumn(12, 18) == 9) // offset of 9
  }

  test("mapping for scala-cli") {
    val code0 = s"""|//> using scala 3.7.3
                    |// Lorem ipsum
                    |1/0
                    |""".stripMargin

    val code1 = s"""|//> using scala 3.7.3
                    |//> using dep org.scastie::runtime-scala:1.0.0-SNAPSHOT
                    |import $runtimePackage._
                    |object $instrumentedObject extends ScastieApp with $instrumentationRecorderT {
                    |/**                */
                    |// Lorem ipsum
                    |scala.Predef.locally {
                    |$$doc.startStatement(39, 42);
                    |val $$t = 1/0;
                    |$$doc.binder($runtimeT.render($$t), 39, 42);
                    |$$doc.endStatement();
                    |$$t}
                    |}
                    |""".stripMargin

    val positionMapper = PositionMapper(code1, true)

    assert(positionMapper.mapLine(9) == 3) // val $t = 1/0;

    assert(positionMapper.mapColumn(9, 12) == 3) // offset of 9
  }
}
