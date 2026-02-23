package org.scastie.instrumentation

import org.scastie.api._
import org.scastie.runtime.api._
import RuntimeConstants._

import scala.collection.immutable.Seq
import scala.meta._
import scala.meta.inputs.Position
import scala.meta.parsers.Parsed
import scala.util.control.NonFatal
import org.scastie.buildinfo.BuildInfo
import org.scastie.api.ScalaTargetType.Scala2
import org.scastie.api.ScalaTargetType.JS

sealed trait InstrumentationFailure

object InstrumentationFailure {
  case object HasMainMethod extends InstrumentationFailure
  case object UnsupportedDialect extends InstrumentationFailure
  case class ParsingError(error: Parsed.Error) extends InstrumentationFailure
  case class InternalError(exception: Throwable) extends InstrumentationFailure
}

case class InstrumentationSuccess(
    instrumentedCode: String,
    positionMapper: Option[PositionMapper] = None
)

object Instrument {
  def getParsingLineOffset(isWorksheet: Boolean): Int = if (isWorksheet) -1 else 0
  def getExceptionLineOffset(isWorksheet: Boolean): Int = if (isWorksheet) -2 else 0
  def getMessageLineOffset(isWorksheet: Boolean): Int = if (isWorksheet) -2 else 0

  import InstrumentationFailure._

  val entryPointName = "Main"

  private val elemArrayT =
    "_root_.scala.scalajs.js.Array[_root_.org.scalajs.dom.HTMLElement]"

  private def extractExperimentalImports(code: String): (String, String) = {
    val experimentalRegex = """^\s*import\s+language\.experimental\.[^\n]+""".r

    val experimentalImports = experimentalRegex.findAllIn(code).toList
    val codeWithoutExpImports = experimentalRegex.replaceAllIn(code, m => "/*" + " " * (m.matched.length - 4) + "*/")

    val experimental = experimentalImports.mkString("\n") + (if (experimentalImports.nonEmpty) "\n" else "")
    
    (experimental, codeWithoutExpImports)
  }

  private def posToApi(position: Position, offset: Int) = {
    val (x, y) = position match {
      case Position.None => (0, 0)
      case Position.Range(_, start, end) =>
        (start - offset, end - offset)
    }
    s"$positionT($x, $y)"
  }

  def instrumentOne(term: Term, tpeTree: Option[Type], offset: Int, isScalaJs: Boolean): Patch = {

    val treeQuote =
      tpeTree match {
        case None      => s"val $$t = $term"
        case Some(tpe) => s"val $$t: $tpe = $term"
      }

    val startPos = term.pos.start - offset
    val endPos   = term.pos.end - offset

    val renderCall =
      if (!isScalaJs) s"$runtimeT.render($$t)"
      else s"$runtimeT.render($$t, attach)"

    val replacement =
      s"""|scala.Predef.locally {
          |$$doc.startStatement($startPos, $endPos);
          |$treeQuote;
          |$$doc.binder($renderCall, $startPos, $endPos);
          |$$doc.endStatement();
          |$$t}""".stripMargin

    Patch(term.tokens.head, term.tokens.last, replacement)
  }

  private def instrument(source: Source, offset: Int, isScalaJs: Boolean): (String, String) = {
    val instrumentedCodePatches = source.stats.collect {
      case c: Defn.Object if c.name.value == instrumentedObject =>
        c.templ.body.stats
          .collect {
            case term: Term if !term.isInstanceOf[Term.EndMarker] =>
              instrumentOne(term, None, offset, isScalaJs)
            }
    }.flatten

    val instrumentedCode = Patch(source.tokens, instrumentedCodePatches)

    val entryPoint =
      if (!isScalaJs) {
        s"""|object $entryPointName {
            |  def suppressUnusedWarnsScastie = Html
            |  val playground = $instrumentedObject
            |  def main(args: Array[String]): Unit = {
            |    playground.main(Array())
            |    scala.Predef.println("\\n" + $runtimeT.writeStatements(playground.$$doc.getResults()))
            |  }
            |}
            |""".stripMargin
      } else {
        s"""|@$jsExportTopLevelT("ScastiePlaygroundMain") class ScastiePlaygroundMain {
            |  def suppressUnusedWarnsScastie = Html
            |  val playground = $runtimeErrorT.wrap($instrumentedObject)
            |  @$jsExportT def result = $runtimeT.writeStatements(playground.map { playground => playground.main(Array()); playground.$$doc.getResults() })
            |  @$jsExportT def attachedElements: $elemArrayT =
            |    playground match {
            |      case Right(p) => p.attachedElements
            |      case Left(_) => $elemArrayT()
            |    }
            |}""".stripMargin
      }

    (instrumentedCode, entryPoint)
  }

  private def hasMainMethod(source: Source): Boolean = {
    def hasMain(templ: Template): Boolean = {
      templ.body.stats.exists {
        case q"def main(args: Array[String]): $_ = $_" => true
        case _                                         => false
      }
    }
    val apps = Set("App", "IOApp")
    def hasApp(templ: Template): Boolean =
      templ.inits.exists(p => apps(p.syntax))

    source.stats.exists {
      case c: Defn.Object if c.name.value == instrumentedObject =>
        c.templ.body.stats.exists {
          case c: Defn.Class  => hasMain(c.templ) || hasApp(c.templ)
          case t: Defn.Trait  => hasMain(t.templ) || hasApp(t.templ)
          case o: Defn.Object => hasMain(o.templ) || hasApp(o.templ)
          case _              => false
        }
      case _ => false
    }
  }

  def separateDirectives(code: String, targetType: ScalaTargetType, additionalDirectives: Seq[String]): (String, String) = {
    if (targetType == ScalaTargetType.ScalaCli) {
      val directiveRegex = """^\s*//>.*(?:\n|$)""".r
      val lines = code.linesWithSeparators.toList
      val (directives, rest) = lines.partition(line => directiveRegex.pattern.matcher(line).matches)
      val directivesStr = directives.mkString
      val replaced = lines.map { line =>
        if (directiveRegex.pattern.matcher(line).matches) {
          val len = line.strip.length
          "/**" + (" " * (len - 5).max(0)) + "*/" + (if (line.endsWith("\n")) "\n" else "")
        } else line
      }.mkString
      val allDirectives = directivesStr + additionalDirectives.mkString
      (allDirectives, replaced)
    } else {
      ("", code)
    }
  }

  def apply(code: String, target: ScalaTarget): Either[InstrumentationFailure, InstrumentationSuccess] = {
    val runtimeImport = target match {
      case Scala3(scalaVersion) => s"import $runtimePackage.*"
      case _ => s"import $runtimePackage._"
    }

    val isScalaJs = target.targetType == ScalaTargetType.JS

    lazy val scalaCliRuntime = target.runtimeDependency.renderScalaCli.appended('\n')
    val (usingDirectives, remainingCode) = separateDirectives(code, target.targetType, Seq(scalaCliRuntime))

    val classBegin =
      if (!isScalaJs) s"object $instrumentedObject extends ScastieApp with $instrumentationRecorderT {"
      else s"object $instrumentedObject extends ScastieApp with $instrumentationRecorderT with $domhookT {"
    val (experimentalImports, codeWithoutExpImports) = extractExperimentalImports(remainingCode)
    val prelude = s"""$runtimeImport\n$experimentalImports$classBegin"""
    val code0 = s"""$usingDirectives$prelude\n$codeWithoutExpImports\n}"""

    def typelevel(scalaVersion: String): Option[Dialect] = {
      if (scalaVersion.startsWith("2.12")) Some(dialects.Typelevel212)
      else if (scalaVersion.startsWith("2.11")) Some(dialects.Typelevel211)
      else None
    }

    def scala(scalaVersion: String): Option[Dialect] = {
      if (scalaVersion.startsWith("2.13")) Some(dialects.Scala213)
      else if (scalaVersion.startsWith("2.12")) Some(dialects.Scala212)
      else if (scalaVersion.startsWith("2.11")) Some(dialects.Scala211)
      else if (scalaVersion.startsWith("2.10")) Some(dialects.Scala210)
      else if (scalaVersion.startsWith("3")) Some(dialects.Scala3)
      else None
    }

    val maybeDialect = target match {
      case Typelevel(scalaVersion) => typelevel(scalaVersion)
      case target                  => scala(target.scalaVersion)
    }

    val isScalaCli = target match {
      case _: ScalaCli => true
      case _ => false
    }

    val offset = isScalaCli match {
      case true  => usingDirectives.length + prelude.length + 1
      case false => prelude.length + 1
    }

    maybeDialect match {
      case Some(dialect) =>
        implicit val selectedDialect: Dialect = dialect

        try {
          code0.parse[Source] match {
            case parsed: Parsed.Success[_] =>
              if (!hasMainMethod(parsed.get)) {
                val (instrumentedCode, entryPoint) = instrument(parsed.get, offset, isScalaJs)

                val positionMapper = PositionMapper(instrumentedCode, isScalaCli)

                Right(InstrumentationSuccess(
                  s"""$instrumentedCode\n$entryPoint""",
                  Some(positionMapper)
                  ))
              } else {
                Left(HasMainMethod)
              }
            case e: Parsed.Error => Left(ParsingError(e))
          }
        } catch {
          case NonFatal(e) =>
            Left(InternalError(e))
        }
      case None => Left(UnsupportedDialect)
    }
  }
}
