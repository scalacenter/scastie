import ScalaJSHelper._
import org.scalajs.sbtplugin.JSModuleID
import com.typesafe.sbt.SbtStartScript

val jdkVersion = settingKey[String]("")

def logging(allDependencies: Seq[ModuleID]): Seq[ModuleID] = {
  Seq(
    "org.slf4j"      % "slf4j-api"        % "1.7.6"
  , "org.slf4j"      % "jul-to-slf4j"     % "1.7.6"
  , "ch.qos.logback" % "logback-core"     % "1.1.1" % Runtime
  , "ch.qos.logback" % "logback-classic"  % "1.1.1" % Runtime
  , "org.slf4j"      % "jcl-over-slf4j"   % "1.7.6" % Runtime
  , "org.slf4j"      % "log4j-over-slf4j" % "1.7.6" % Runtime
  ) ++
    allDependencies.map(
      _.exclude("commons-logging", "commons-logging")
        .exclude("log4j", "log4j")
        .exclude("org.slf4j", "slf4j-log4j12")
        .exclude("org.slf4j", "slf4j-jcl")
        .exclude("org.slf4j", "slf4j-jdk14")
    )
}
def akka(module: String) = "com.typesafe.akka" %% ("akka-" + module) % "2.3.11"

val defaultSettings = Seq(
  incOptions := incOptions.value.withNameHashing(true)
, jdkVersion := "1.7"
, javacOptions ++= Seq(
    "-source", jdkVersion.value
  , "-target", jdkVersion.value
  )
, scalacOptions ++= Seq(
    s"-target:jvm-${jdkVersion.value}"
  , "-encoding", "UTF-8"
  , "-feature"
  , "-deprecation"
  , "-unchecked"
  , "-Xfatal-warnings"
  , "-Xlint"
  , "-Yinline-warnings"
  , "-Yno-adapted-args"
  , "-Yrangepos"
  , "-Ywarn-dead-code"
  , "-Ywarn-numeric-widen"
  , "-Ywarn-unused-import"
  )
, updateOptions := updateOptions.value.withCachedResolution(true).withLatestSnapshots(false)
, scalaVersion := "2.11.8"
, libraryDependencies ++= Seq(
      "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3-1"
    , "org.scalaz"                    %% "scalaz-core"   % "7.1.3"
  // , "io.netty"                     %  "netty"         % "3.10.4.Final"
  )
, allDependencies ~= logging
, aggregate in reStart := false
) ++ SbtStartScript.startScriptForClassesSettings

lazy val renderer = project
  .settings(defaultSettings)
  .settings(
    mainClass in Compile := Option("com.olegych.scastie.RendererMain")
  , libraryDependencies ++= Seq(
       akka("actor")
    ,  akka("remote")
    ,  akka("slf4j")
    ,  "org.apache.commons"          % "commons-lang3"       % "3.1"
    ,  "net.sourceforge.collections" % "collections-generic" % "4.01"
    )
  )

lazy val scastie = project.in(file("."))
  .settings(defaultSettings)
  .settings(packageScalaJS(client))
  .settings(
    scalacOptions --= Seq(
       "-Ywarn-unused-import"
    ,  "-Xfatal-warnings"
    )
  , allDependencies ~= (_.map(
      _.exclude("com.typesafe.play", "play-doc_2.11")
       .exclude("com.typesafe.play", "play-docs_2.11")
       .exclude("com.lihaoyi", "upickle_sjs0.6_2.11")
    ))
  , mainClass in Compile := Option("ProdNettyServer")
  , products in Compile <<= (products in Compile).dependsOn(WebKeys.assets in Assets)
  , reStart <<= reStart.dependsOn(WebKeys.assets in Assets)
  , WebKeys.public in Assets := (classDirectory in Compile).value / "public"
  )
  .enablePlugins(SbtWeb, play.PlayScala)
  .dependsOn(renderer, client, apiJVM)

lazy val baseSettings = Seq(
  scalaVersion := "2.11.8"
, scalacOptions := Seq(
    "-deprecation"
  , "-encoding", "UTF-8"
  , "-feature"
  , "-unchecked"
  , "-Xfatal-warnings"
  , "-Xlint"
  , "-Yinline-warnings"
  , "-Yno-adapted-args"
  , "-Ywarn-dead-code"
  , "-Ywarn-numeric-widen"
  , "-Ywarn-unused-import"
  , "-Ywarn-value-discard"
  )
)

def codemirrorD(path: String): JSModuleID =
"org.webjars.bower"  % "codemirror" % "5.18.2" % "compile" / s"$path.js" minified s"$path.js"

lazy val codemirror = project
  .settings(baseSettings)
  .settings(
    scalacOptions -= "-Ywarn-dead-code"
  , jsDependencies ++=
    List(
      "lib/codemirror"
    ,  "addon/comment/comment"
    ,  "addon/dialog/dialog"
    ,  "addon/edit/closebrackets"
    ,  "addon/edit/matchbrackets"
    ,  "addon/fold/brace-fold"
    ,  "addon/fold/foldcode"
    ,  "addon/hint/show-hint"
    ,  "addon/runmode/runmode"
    ,  "addon/scroll/scrollpastend"
    ,  "addon/scroll/simplescrollbars"
    ,  "addon/search/match-highlighter"
    ,  "addon/search/search"
    ,  "addon/search/searchcursor"
    ,  "keymap/sublime"
    ,  "mode/clike/clike"
    ).map(codemirrorD)
  , libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.9.1"
  )
  .enablePlugins(ScalaJSPlugin)

def react(artifact: String, name: String): JSModuleID = 
  "org.webjars.bower" % "react" % "15.2.1" % "compile" / s"$artifact.js" minified s"$artifact.min.js" commonJSName name

def react(artifact: String, name: String, depends: String): JSModuleID =
  react(artifact, name).dependsOn(s"$depends.js")

lazy val client = project
  .settings(baseSettings)
  .settings(
    JsEngineKeys.engineType := JsEngineKeys.EngineType.Node
  , skip in packageJSDependencies := false
  , jsDependencies ++= Seq(
      react("react-with-addons", "React")
    , react("react-dom", "ReactDOM", "react-with-addons")
    , react("react-dom-server", "ReactDOMServer", "react-dom")
    )
  , libraryDependencies += "com.github.japgolly.scalajs-react" %%% "extra" % "0.11.1"
  )
  .enablePlugins(ScalaJSPlugin, SbtWeb)
  .dependsOn(codemirror, apiJS)

lazy val api = crossProject
  .settings(baseSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "autowire" % "0.2.5"
    , "com.lihaoyi" %%% "upickle"  % "0.4.0"
    )
  )
  .jsSettings(
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.9.1"
  )

lazy val apiJVM = api.jvm
lazy val apiJS = api.js