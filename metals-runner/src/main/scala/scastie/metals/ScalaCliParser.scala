package scastie.metals

import scala.jdk.CollectionConverters._
import com.virtuslab.using_directives.custom.utils.Source
import com.virtuslab.using_directives.config.Settings
import com.virtuslab.using_directives.Context
import com.virtuslab.using_directives.reporter.PersistentReporter
import com.virtuslab.using_directives.custom.Parser
import com.virtuslab.using_directives.custom.utils.ast.UsingDef
import com.virtuslab.using_directives.reporter.ConsoleReporter
import com.virtuslab.using_directives.custom.SimpleCommentExtractor
import com.olegych.scastie.api.ScalaTarget
import com.olegych.scastie.api.FailureType
import com.olegych.scastie.api.InvalidScalaVersion
import com.olegych.scastie.buildinfo.BuildInfo
import com.olegych.scastie.api.ScastieMetalsOptions
import com.virtuslab.using_directives.custom.utils.ast.SettingDefOrUsingValue
import com.virtuslab.using_directives.custom.utils.ast.NumericLiteral
import com.virtuslab.using_directives.custom.utils.ast.StringLiteral
import com.olegych.scastie.api.PresentationCompilerFailure
import com.olegych.scastie.api.ScalaDependency
import com.olegych.scastie.api.ScalaTarget.ScalaCli

object ScalaCliParser {

  def getScliDirectives(string: String) =
    SimpleCommentExtractor(string.toCharArray(), true).extractComments()

  def parse(string: String) =
    val source = new Source(getScliDirectives(string))
    val reporter = new PersistentReporter()
    val ctx = new Context(reporter)
    val parser = new Parser(source, ctx)
    
    val defs = parser.parse().getUsingDefs().asScala.toList 
    val allDefs = defs.flatMap(_.getSettingDefs().getSettings().asScala)

    allDefs

  private def extractValue(k: SettingDefOrUsingValue): Option[String] = {
    k match
      case k: NumericLiteral => Some(k.getValue())
      case k: StringLiteral => Some(k.getValue())
      case _ => None  
  }

  def getScalaTarget(code: String): Either[FailureType, ScastieMetalsOptions] = {
    val defs: Map[String, List[String]] = parse(code).groupMapReduce(
      _.getKey()
    )(
      t => {
        val option = extractValue(t.getValue())
        option.toList
      }
    )(_ ++ _)

    // get the scala version
    var scalaVersion = defs.get("scala").getOrElse(List(BuildInfo.latest3)).headOption.getOrElse("3")

    // now we have the scala version
    // get the target
    val scalaTarget: Either[FailureType, ScalaTarget] =
      ScalaTarget.fromScalaVersion(scalaVersion) match
        case None => Left(InvalidScalaVersion(s"Invalid Scala version $scalaVersion"))
        case Some(target) => Right(target)

    scalaTarget.map { scalaTarget => {
      val dependencies = defs.get("dep").getOrElse(List()) ++ defs.get("lib").getOrElse(List())

      val actualDependencies = dependencies.map(_.split(":").toList).flatMap {
        // "groupId::artifact:version"
        case List(groupId, "", artifactId, version) => List(ScalaDependency(groupId, artifactId, scalaTarget, version))
        // "groupId:artifact:version"
        case List(groupId, artifactId, version) => {
          val split = artifactId.split("_")
          val scalaLibVersion = split.last
          val libname = split.init.mkString("_")
          List(ScalaDependency(groupId, libname, ScalaCli(scalaLibVersion), version))
        }

        case _ => List()
      }

      ScastieMetalsOptions(actualDependencies.toSet, scalaTarget)
    } }
  }

}
