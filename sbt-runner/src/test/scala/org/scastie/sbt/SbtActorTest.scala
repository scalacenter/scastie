package org.scastie.sbt

import scala.concurrent.duration._

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.testkit.TestActor.AutoPilot
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.BeforeAndAfterAll
import org.scastie.api._
import org.scastie.runtime.api._
import org.scastie.util.SbtTask

class SbtActorTest()
  extends TestKit(ActorSystem("SbtActorTest"))
  with ImplicitSender
  with AnyFunSuiteLike
  with BeforeAndAfterAll {
  setAutoPilot(new AutoPilot {
    def run(sender: ActorRef, msg: Any): AutoPilot = {
      sender ! s"reply to $msg"
      this
    }
  })
  print("\u001b")

  (1 to 2).foreach { i =>
    test(s"[$i] timeout") {
      runCode(s"Thread.sleep(${timeout.toMillis + 1000})", allowFailure = true)(progress => {
        // println(progress)
        progress.isTimeout
      })
    }

    test(s"[$i] after a timeout the sbt instance is ready to be used") {
      runCode("1 + 1")(progress => {
        val gotInstrumentation = progress.instrumentations.nonEmpty

        if (gotInstrumentation) {
          assert(
            progress.instrumentations == List(
              Instrumentation(Position(0, 5), Value("2", "Int"))
            )
          )
        }

        gotInstrumentation
      })
    }
  }

  test("capture runtime errors") {
    runCode("1/0", allowFailure = true) { progress =>
      val gotRuntimeError = progress.runtimeError.nonEmpty

      if (gotRuntimeError) {
        val error = progress.runtimeError.get
        println(error.fullStack)
        assert(error.fullStack.nonEmpty)
        assert(error.line.contains(1))
        assert(error.message contains "java.lang.ArithmeticException: / by zero")
      }
      gotRuntimeError
    }
  }

  test("trap sys.exit") {
    runCode("sys.exit(-2)", allowFailure = false) { progress =>
      progress.buildOutput.exists(_.line.contains("Nonzero exit code: -2"))
    }
  }

  test("capture user output separately from sbt output") {
    val message = "Hello"
    runCode(s"""println("$message")""")(
      assertUserOutput(message)
    )
  }

  test("force program mode when an entry point is present") {
    val message = "Hello"
    runCode(
      s"""object Main { def main(args: Array[String]): Unit = println("$message") }""",
      allowFailure = true
    ) { progress =>
      if (progress.isDone) progress.isForcedProgramMode else false
    }
  }

  test("report parsing error") {
    runCode("\n4444444444444444444\n", allowFailure = true)(assertCompilationInfo { info =>
      assert(info.message == "integer number too large for Int")
      assert(info.line.contains(2))
    })
  }

  test("report compilation error") {
    runCode("err", allowFailure = true)(assertCompilationInfo { info =>
      assert(info.message == "not found: value err")
      assert(info.line.contains(1))
    })
  }

  test("Encoding issues #100") {
    val message = "€"
    runCode(s"""println("$message")""")(
      assertUserOutput(message)
    )
  }

  test("Disable Predef #357") {
    val message = "No Predef!"
    val input = SbtInputs.default.copy(
      sbtConfigExtra = "scalacOptions += \"-Yno-predef\" ",
      code = s"""scala.Predef.println("$message")"""
    )
//    run(input)(_ => true)
//    run(input.copy(sbtConfigExtra = input.sbtConfigExtra + "\nname := \"aaa\" "))(_ => true)
    run(input)(assertUserOutput(message))
  }

  test("Scala.js support") {
    val scalaJs = SbtInputs.default.copy(code = "1 + 1", target = Js.default)
    run(scalaJs)(_.isDone)
  }

  test("Scala.js 3 support") {
    val scalaJs = SbtInputs.default
      .copy(code = "1 + 1", target = Js.default.copy(scalaVersion = org.scastie.buildinfo.BuildInfo.latestLTS))
    run(scalaJs)(_.isDone)
  }

  test("Scala 2.10 support") {
    val scala =
      SbtInputs.default.copy(code = "println(1 + 1)", target = Scala2(org.scastie.buildinfo.BuildInfo.latest210))
    run(scala)(assertUserOutput("2"))
  }

  test("Scala 2.11 support") {
    val scala =
      SbtInputs.default.copy(code = "println(1 + 1)", target = Scala2(org.scastie.buildinfo.BuildInfo.latest211))
    run(scala)(assertUserOutput("2"))
  }

  test("Scala 2.12 support") {
    val scala =
      SbtInputs.default.copy(code = "println(1 + 1)", target = Scala2(org.scastie.buildinfo.BuildInfo.latest212))
    run(scala)(assertUserOutput("2"))
  }

  test("avoid https://github.com/scala/bug/issues/8119") {
    val scala = SbtInputs.default.copy(
      code = "val n = 0; val m = List(1).par.foreach(_ => n); println(1)",
      target = Scala2(org.scastie.buildinfo.BuildInfo.latest212)
    )
    run(scala)(assertUserOutput("1"))
  }

  test("Scala 2.13 support") {
    val scala =
      SbtInputs.default.copy(code = "println(1 + 1)", target = Scala2(org.scastie.buildinfo.BuildInfo.latest213))
    run(scala)(assertUserOutput("2"))
  }

  test("no warnings by default") {
    val scala =
      SbtInputs.default.copy(code = "println(1 + 1)", sbtConfigExtra = """scalacOptions ++= List("-Xfatal-warnings")""")
    run(scala)(assertUserOutput("2"))
  }

  test("no warnings on 2.12") {
    val scala = SbtInputs.default.copy(
      code = "println(1 + 1)",
      sbtConfigExtra = """scalacOptions ++= List("-Xlint", "-Xfatal-warnings")""",
      target = Scala2("2.12.10")
    )
    run(scala)(assertUserOutput("2"))
  }

  test("Scala 3 support") {
    val message = "Hello, Scala 3!"
    val dotty = SbtInputs.default.copy(
      code = s"""|object Main {
                 |  def main(args: Array[String]): Unit = {
                 |    println("$message")
                 |  }
                 |}
                 |""".stripMargin,
      target = Scala3.default,
      isWorksheetMode = false
    )
    run(dotty)(assertUserOutput("Hello, Scala 3!"))
  }

  test("Scala 3 worksheet support") {
    val message = "Hello, Scala 3 worksheet!"
    val dotty = SbtInputs.default.copy(
      code = s"""println("$message")""",
      target = Scala3.default
    )
    run(dotty)(assertUserOutput("Hello, Scala 3 worksheet!"))
  }

  test("Scala 3.0 worksheet support") {
    val message = "Hello, Scala 3.0 worksheet!"
    val dotty = SbtInputs.default.copy(
      code = s"""println("$message")""",
      target = Scala3("3.0.0")
    )
    run(dotty)(assertUserOutput("Hello, Scala 3.0 worksheet!"))
  }

  test("Capture System.err #284") {
    val message = "Failure"
    runCode(s"""System.err.println("$message")""")(
      assertUserOutput(message, ProcessOutputType.StdErr)
    )
  }

  test("#258 instrumentation with variable t") {
    runCode("val t = 1; t")(_.instrumentations.nonEmpty)
  }

  test("last line comment should not fail compilation in worksheet Scala 2") {
    val dotty = SbtInputs.default.copy(
      code = s"""|println("Hello world!")
                 |// test comment""".stripMargin,
      target = Scala2.default
    )
    run(dotty)(assertUserOutput("Hello world!"))
  }

  test("last line comment should not fail compilation in worksheet Scala 3") {
    val dotty = SbtInputs.default.copy(
      code = s"""|println("Hello world!")
                 |// test comment""".stripMargin,
      target = Scala3.default
    )
    run(dotty)(assertUserOutput("Hello world!"))
  }

  test("Scala 3 braceless syntax: print statement") {
    val dotty = SbtInputs.default.copy(
      code = s"""|println:
                 |  "Hello world!"
                 |""".stripMargin
    )
    var outputOk = false
    var instrOk = false
    run(dotty) { progress =>
      if (assertUserOutput("Hello world!")(progress)) outputOk = true
      if (assertInstrumentation(Value("()", "scala.Unit"), Position(0, 25))(progress)) instrOk = true
      outputOk && instrOk
    }
  }

  test("top-level match expression") {
    val dotty = SbtInputs.default.copy(
      code = """|1 match
                |  case a: Int => "Hi"
                |""".stripMargin
    )

    run(dotty) {
      assertInstrumentation(Value("Hi", "java.lang.String"), Position(0, 29))
    }
  }

  test("string interpolation in match pattern with println") {
    val dotty = SbtInputs.default.copy(
      code = """|"hello" match
                |  case s"hel$lo" => println(lo)
                |""".stripMargin
    )
    var outputOk = false
    var instrOk = false
    run(dotty) { progress =>
      if (assertUserOutput("lo")(progress)) outputOk = true
      if (assertInstrumentation(Value("()", "scala.Unit"), Position(0, 45))(progress)) instrOk = true
      outputOk && instrOk
    }
  }

  test("pattern match with custom unapplySeq extractor and println") {
    val dotty = SbtInputs.default.copy(
      code = """|object CharList:
                |  def unapplySeq(s: String): Option[Seq[Char]] = Some(s.toList)
                |
                |"example" match
                |  case CharList(c1, c2, c3, c4, _, _, _) =>
                |    println(s"$c1,$c2,$c3,$c4")
                |""".stripMargin
    )
    var outputOk = false
    var instrOk = false
    run(dotty) { progress =>
      if (assertUserOutput("e,x,a,m")(progress)) outputOk = true
      if (assertInstrumentation(Value("()", "scala.Unit"), Position(82, 173))(progress)) instrOk = true
      outputOk && instrOk
    }
  }

  test("braceless while loop") {
    val dotty = SbtInputs.default.copy(
      code = """|var x = 1
                |
                |while
                |  x < 3
                |do
                |  if (x != 1) { println(x) }
                |  x += 1
                |""".stripMargin
    )
    var outputOk = false
    var instrOk = false
    run(dotty) { progress =>
      if (assertUserOutput("2")(progress)) outputOk = true
      if (assertInstrumentation(Value("()", "scala.Unit"), Position(11, 65))(progress)) instrOk = true
      outputOk && instrOk
    }
  }

  test("report warning for extension method conflict") {
    val dotty = SbtInputs.default.copy(
      code = """|class Json:
                |  def foo[A](bar: AnyRef): Unit = ()
                |
                |extension (j: Json)
                |  def foo[A](bar: String): Unit = ()
                |""".stripMargin
    )
    run(dotty)(assertCompilationInfo { info =>
      assert(info.message.contains("Extension method foo will never be selected from type Json"))
      assert(info.line.contains(5))
    })
  }

  test("nested if-then with println") {
    val dotty = SbtInputs.default.copy(
      code = """|if true then
                |  if true then 
                |    println("yes")
                |""".stripMargin
    )
    var outputOk = false
    var instrOk = false
    run(dotty) { progress =>
      if (assertUserOutput("yes")(progress)) outputOk = true
      if (assertInstrumentation(Value("()", "scala.Unit"), Position(0, 47))(progress)) instrOk = true
      outputOk && instrOk
    }
  }

  test("List.map with case in braceless syntax") {
    val dotty = SbtInputs.default.copy(
      code = """|List(1,2,3).map:
                |  case x => x
                |""".stripMargin
    )
    run(dotty) { progress =>
      assertInstrumentation(
        Value("List(1, 2, 3)", "scala.collection.immutable.List[scala.Int]"),
        Position(0, 30)
      )(progress)
    }
  }

  test("properly instrumented whitespaces") {
    val dotty = SbtInputs.default.copy(
      code = """|1 + 1
                |
                |
                |
                |1 + 5
                |""".stripMargin
    )
    run(dotty) { progress =>
      assertInstrumentation(
        Value("2", "scala.Int"),
        Position(0, 5)
      )(progress) &&
      assertInstrumentation(
        Value("6", "scala.Int"),
        Position(9, 14)
      )(progress)
    }
  }

  test("hide Playground from types") {
    runCode("case class A(i:Int) extends AnyVal; A(1)")(
      _.instrumentations.headOption.exists(_.render == Value("A(1)", "A"))
    )
  }

  test("#304 null pointer") {
    runCode(
      """|trait A {
         |  val a = "1"
         |  val b = a
         |}
         |
         |new A {
         |  override val a = ("2")
         |  println(b)
         |}""".stripMargin,
      allowFailure = true
    )(_.isDone)
  }

  test("sbt worksheet correct error position") {
    val input = SbtInputs.default.copy(
      code = s"""
                |
                |printl
                |
                |""".stripMargin,
      target = Scala3.default,
      isWorksheetMode = true
    )

    run(input, allowFailure = true)(assertCompilationInfo { info =>
      assert(info.message == "Not found: printl - did you mean print? or perhaps printf or println?")
      assert(info.line == Some(3)) // error lines are 1-based
    })

  }

  test("sbt non-worksheet correct error position") {
    val input = SbtInputs.default.copy(
      code = s"""
                |
                |printl
                |
                |""".stripMargin,
      target = Scala3.default,
      isWorksheetMode = false
    )

    run(input, allowFailure = true)(assertCompilationInfo { info =>
      assert(info.message == "Illegal start of toplevel definition")
      assert(info.line == Some(3)) // error lines are 1 based
    })
  }

  def assertCompilationInfo(
    infoAssert: Problem => Any
  )(progress: SnippetProgress): Boolean = {

    val gotCompilationError = progress.compilationInfos.nonEmpty

    if (gotCompilationError) {
      val info = progress.compilationInfos.head
      infoAssert(info)
    }

    gotCompilationError
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private val timeout = 40.seconds

  // SbtProcess uses Stash and it's not compatible with TestActorRef
  // https://stackoverflow.com/questions/18335127/testing-akka-actors-that-mixin-stash-with-testactorref
  private val sbtActor = system.actorOf(
    Props(
      new SbtProcess(
        runTimeout = timeout,
        reloadTimeout = 20.seconds,
        isProduction = false,
        javaOptions = Seq("-Xms51m", "-Xmx550m")
      )
    )
  )

  private var currentId = 0

  private def snippetId = {
    val t = currentId
    currentId += 1
    SnippetId(t.toString, None)
  }

  private var firstRun = true

  private def run(inputs: SbtInputs, allowFailure: Boolean = false)(fish: SnippetProgress => Boolean): Unit = {
    val ip = "my-ip"
    val progressActor = TestProbe()

    sbtActor ! SbtTask(snippetId, inputs, ip, None, progressActor.ref)

    val totalTimeout =
      if (firstRun) timeout + 10.second
      else timeout

    progressActor.fishForMessage(totalTimeout + 100.seconds) { case progress: SnippetProgress =>
      val fishResult = fish(progress)
      //        println(progress -> fishResult)
      if ((progress.isFailure && !allowFailure) || (progress.isDone && !fishResult))
        throw new Exception(s"Fail to meet expectation at ${progress}")
      else fishResult
    }
    firstRun = false
  }

  private def runCode(code: String, target: SbtScalaTarget = Scala2.default, allowFailure: Boolean = false)(
    fish: SnippetProgress => Boolean
  ): Unit = {
    run(SbtInputs.default.copy(code = code, target = target), allowFailure)(fish)
  }

  private def assertInstrumentation(
    expected: Value,
    position: Position
  )(progress: SnippetProgress): Boolean = {
    progress.instrumentations.exists(instr => instr.render == expected && instr.position == position)
  }

  private def assertUserOutput(
    message: String,
    outputType: ProcessOutputType = ProcessOutputType.StdOut
  )(progress: SnippetProgress): Boolean = {
    val gotHelloMessage = progress.userOutput.exists(out => out.line == message && out.tpe == outputType)
//    if (!gotHelloMessage) assert(progress.userOutput.isEmpty)
    gotHelloMessage
  }

}
