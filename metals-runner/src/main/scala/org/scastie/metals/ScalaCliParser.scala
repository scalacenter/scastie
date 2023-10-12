package org.scastie.metals

import scala.jdk.CollectionConverters._
import com.virtuslab.using_directives.custom.utils.Source
import com.virtuslab.using_directives.config.Settings
import com.virtuslab.using_directives.Context
import com.virtuslab.using_directives.reporter.PersistentReporter
import com.virtuslab.using_directives.custom.Parser
import com.virtuslab.using_directives.custom.utils.ast.UsingDef
import com.virtuslab.using_directives.reporter.ConsoleReporter
import com.virtuslab.using_directives.custom.SimpleCommentExtractor
import org.scastie.buildinfo.BuildInfo
import com.virtuslab.using_directives.custom.utils.ast.SettingDefOrUsingValue
import com.virtuslab.using_directives.custom.utils.ast.NumericLiteral
import com.virtuslab.using_directives.custom.utils.ast.StringLiteral
import org.scastie.api._
import com.virtuslab.using_directives.custom.utils.ast.SettingDef

object ScalaCliParser:

  def getScliDirectives(string: String): Array[Char] =
    SimpleCommentExtractor(string.toCharArray(), true).extractComments()

  def parse(string: String): List[SettingDef] =
    val source = new Source(getScliDirectives(string))
    val reporter = new PersistentReporter()
    val ctx = new Context(reporter)
    val parser = new Parser(source, ctx)

    val defs = parser.parse().getUsingDefs().asScala.toList
    val allDefs = defs.flatMap(_.getSettingDefs().getSettings().asScala)
    allDefs

  private def extractValue(setting: SettingDefOrUsingValue): Option[String] =
    setting match
      case numericLiteral: NumericLiteral => Option(numericLiteral.getValue())
      case stringLiteral: StringLiteral => Option(stringLiteral.getValue())
      case _ => None

  def getScalaTarget(code: String): ScastieMetalsOptions =
    val usingDirectives: Map[String, List[String]] = parse(code).groupMapReduce(_.getKey)(
      settingDef => {
        val option = extractValue(settingDef.getValue)
        option.toList
      }
    )(_ ++ _)

    val scalaVersion = usingDirectives.get("scala").flatMap(_.headOption).getOrElse(BuildInfo.latest3)
    val scalaTarget = ScalaCli(scalaVersion)
    val dependenciesDirectives = (usingDirectives.get("dep") zip usingDirectives.get("lib"))
      .map(_ ++ _).getOrElse(Nil)

    val dependencies = dependenciesDirectives.map(_.split(":").toList).collect:
      // "groupId::artifact:version"
      case groupId :: "" :: artifactId :: version :: Nil => ScalaDependency(groupId, artifactId, scalaTarget, version)
      // "groupId:artifact:version"
      case groupId :: artifactId :: version :: Nil => ScalaDependency(groupId, artifactId, scalaTarget, version)

    ScastieMetalsOptions(dependencies.toSet, scalaTarget, code)

