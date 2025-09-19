package org.scastie
package instrumentation

import scala.meta._

import org.scalatest.funsuite.AnyFunSuite

class LineMapperSpecs extends AnyFunSuite {

  test("identity mapping for identical input") {
    val code0 = """|val x = 42
                   |val y = x + 1
                   |""".stripMargin
    val code1 = """|val x = 42
                   |val y = x + 1
                   |""".stripMargin

    val lineMapper = LineMapper(code1)

    assert(lineMapper(1) == 1) // val x = 42
    assert(lineMapper(2) == 2) // val y = x + 1
  }

  test("mapping with simple instrumentation") {
    val code0 = """|println("test1")
                   |val y = 1
                   |println("test2")
                   |""".stripMargin

    val code1 = """|import _root_.org.scastie.runtime.*
                   |object Playground extends ScastieApp with _root_.org.scastie.runtime.api.InstrumentationRecorder {
                   |scala.Predef.locally {
                   |$doc.startStatement(0, 16);
                   |val $t = println("test1"); 
                   |$doc.binder(_root_.org.scastie.runtime.Runtime.render($t), 0, 16);
                   |$doc.endStatement();
                   |$t}
                   |val y = 1
                   |scala.Predef.locally {
                   |$doc.startStatement(26, 42);
                   |val $t = println("test2"); 
                   |$doc.binder(_root_.org.scastie.runtime.Runtime.render($t), 26, 42);
                   |$doc.endStatement();
                   |$t}
                   |}
                   |""".stripMargin

    val lineMapping = LineMapper(code1)

    assert(lineMapping(5) == 1)  // val $t = println("test1");
    assert(lineMapping(9) == 2)  // val y = 1
    assert(lineMapping(12) == 3) // val $t = println("test2");
  }

  test("mapping with experimental imports extracted") {
    val code0 = """|import language.experimental.captureChecking
                   |println("test")
                   |val x = 1
                   |""".stripMargin

    val code1 =
      """|import _root_.org.scastie.runtime.*
         |import language.experimental.captureChecking
         |object Playground extends ScastieApp with _root_.org.scastie.runtime.api.InstrumentationRecorder {                                               
         |
         |scala.Predef.locally {
         |$doc.startStatement(0, 15);
         |val $t = println("test"); 
         |$doc.binder(_root_.org.scastie.runtime.Runtime.render($t), 0, 15);
         |$doc.endStatement();
         |$t}
         |val x = 1
         |}
         |""".stripMargin

    val lineMapping = LineMapper(code1)

    assert(lineMapping(2) == 1)  // experimental import
    assert(lineMapping(7) == 2)  // val $t = println("test");
    assert(lineMapping(11) == 3) // val x = 1
  }

  test("mapping with multiline expressions") {
    val code0 = """|println:
                   |  "multiline"
                   |val x = 1
                   |""".stripMargin

    val code1 = """|import _root_.org.scastie.runtime.*
                   |object Playground extends ScastieApp with _root_.org.scastie.runtime.api.InstrumentationRecorder {
                   |scala.Predef.locally {
                   |$doc.startStatement(0, 25);
                   |val $t = println:
                   |  "multiline"; 
                   |$doc.binder(_root_.org.scastie.runtime.Runtime.render($t), 0, 25);
                   |$doc.endStatement();
                   |$t}
                   |val x = 1
                   |}
                   |""".stripMargin

    val lineMapping = LineMapper(code1)

    assert(lineMapping(5) == 1)  // val $t = println:
    assert(lineMapping(6) == 2)  // "multiline";
    assert(lineMapping(10) == 3) // val x = 1
  }

  test("mapping with empty lines and comments") {
    val code0 = """|// Comment 1
                   |
                   |println("test")
                   |// Comment 2
                   |val x = 1
                   |""".stripMargin

    val code1 = """|import _root_.org.scastie.runtime.*
                   |object Playground extends ScastieApp with _root_.org.scastie.runtime.api.InstrumentationRecorder {
                   |// Comment 1
                   |
                   |scala.Predef.locally {
                   |$doc.startStatement(15, 31);
                   |val $t = println("test"); 
                   |$doc.binder(_root_.org.scastie.runtime.Runtime.render($t), 15, 31);
                   |$doc.endStatement();
                   |$t}
                   |// Comment 2
                   |val x = 1
                   |}
                   |""".stripMargin

    val lineMapping = LineMapper(code1)

    assert(lineMapping(3) == 1)  // Comment 1
    assert(lineMapping(4) == 2)  // empty line
    assert(lineMapping(7) == 3)  // val $t = println("test");
    assert(lineMapping(11) == 4) // Comment 2
    assert(lineMapping(12) == 5) // val x = 1
  }

  test("mapping with mixed imports") {
    val code0 = """|import scala.util.Random
                   |import language.experimental.captureChecking
                   |val r = Random.nextInt()
                   |""".stripMargin

    val code1 = """|import _root_.org.scastie.runtime.*
                   |import language.experimental.captureChecking
                   |object Playground extends ScastieApp with _root_.org.scastie.runtime.api.InstrumentationRecorder {
                   |import scala.util.Random   
                   |                                       
                   |val r = Random.nextInt(); 
                   |}
                   |""".stripMargin

    val lineMapping = LineMapper(code1)

    assert(lineMapping(4) == 1) // import scala.util.Random
    assert(lineMapping(6) == 3) // val r = Random.nextInt();
  }

  test("empty original code") {
    val code0 = ""
    val code1 = """|import _root_.org.scastie.runtime.*
                   |object Playground extends ScastieApp with _root_.org.scastie.runtime.api.InstrumentationRecorder {
                   |}
                   |""".stripMargin

    val lineMapping = LineMapper(code1)

    assert(lineMapping(1) == 1)
    assert(lineMapping(2) == 1)
    assert(lineMapping(3) == 1)
  }

  test("single line code") {
    val code0 = "println(42)"

    val code1 = """|import _root_.org.scastie.runtime.*
                   |object Playground extends ScastieApp with _root_.org.scastie.runtime.api.InstrumentationRecorder {
                   |scala.Predef.locally {
                   |$doc.startStatement(0, 11);
                   |val $t = println(42); 
                   |$doc.binder(_root_.org.scastie.runtime.Runtime.render($t), 0, 11);
                   |$doc.endStatement();
                   |$t}
                   |}
                   |""".stripMargin

    val lineMapping = LineMapper(code1)

    assert(lineMapping(5) == 1) // val $t = println(42);
  }

  test("line numbers beyond input") {
    val code0 = "val x = 1"
    val code1 = "val x = 1"

    val lineMapping = LineMapper(code1)

    assert(lineMapping(10) == 10)
    assert(lineMapping(100) == 100)
  }

  test("complex real-world example") {
    val code0 = """|import scala.concurrent.Future
                   |import language.experimental.captureChecking
                   |
                   |// Setup
                   |val data = List(1, 2, 3)
                   |
                   |// Processing
                   |data.foreach { x =>
                   |  println(s"Processing: $x")
                   |}
                   |
                   |val result = data.map(_ * 2)
                   |println(result)
                   |""".stripMargin

    val code1 = """|import _root_.org.scastie.runtime.*
                   |import language.experimental.captureChecking
                   |object Playground extends ScastieApp with _root_.org.scastie.runtime.api.InstrumentationRecorder {
                   |import scala.concurrent.Future
                   |
                   |                                                 
                   |// Setup
                   |val data = List(1, 2, 3)
                   |
                   |// Processing
                   |scala.Predef.locally {
                   |$doc.startStatement(25, 75);
                   |val $t = data.foreach { x =>
                   |  println(s"Processing: $x")
                   |}; 
                   |$doc.binder(_root_.org.scastie.runtime.Runtime.render($t), 25, 75);
                   |$doc.endStatement();
                   |$t}
                   |
                   |val result = data.map(_ * 2)
                   |scala.Predef.locally {
                   |$doc.startStatement(106, 120);
                   |val $t = println(result); 
                   |$doc.binder(_root_.org.scastie.runtime.Runtime.render($t), 106, 120);
                   |$doc.endStatement();
                   |$t}
                   |}
                   |""".stripMargin

    val lineMapping = LineMapper(code1)

    assert(lineMapping(4) == 1)   // import scala.concurrent.Future
    assert(lineMapping(7) == 4)   // Setup comment
    assert(lineMapping(8) == 5)   // val data = List(1, 2, 3)
    assert(lineMapping(10) == 7)  // Processing comment
    assert(lineMapping(13) == 8)  // val $t = data.foreach...
    assert(lineMapping(20) == 12) // val result = data.map(_ * 2)
    assert(lineMapping(23) == 13) // val $t = println(result);
  }

  test("mapping with multiple identical expressions") {
    val code0 = """|println("test")
                   |val y = 2
                   |println("test")
                   |val z = 3
                   |""".stripMargin

    val code1 = """|import _root_.org.scastie.runtime.*
                   |object Playground extends ScastieApp with _root_.org.scastie.runtime.api.InstrumentationRecorder {
                   |scala.Predef.locally {
                   |$doc.startStatement(0, 15);
                   |val $t = println("test"); 
                   |$doc.binder(_root_.org.scastie.runtime.Runtime.render($t), 0, 15);
                   |$doc.endStatement();
                   |$t}
                   |val y = 2
                   |scala.Predef.locally {
                   |$doc.startStatement(25, 40);
                   |val $t = println("test"); 
                   |$doc.binder(_root_.org.scastie.runtime.Runtime.render($t), 25, 40);
                   |$doc.endStatement();
                   |$t}
                   |val z = 3
                   |}
                   |""".stripMargin

    val lineMapping = LineMapper(code1)

    assert(lineMapping(5) == 1)  // val $t = println("test"); #1
    assert(lineMapping(12) == 3) // val $t = println("test"); #2
    assert(lineMapping(9) == 2)  // val y = 2
    assert(lineMapping(16) == 4) // val z = 3
  }
}
