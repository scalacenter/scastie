package com.olegych.scastie

import com.olegych.scastie.proto.{
  Inputs,
  ScalaDependency,
  ScalaTarget,
  ScalaTargetType,
  Project,
  SnippetId,
  User,
  SnippetUserPart
}

package object api {
  implicit class SnippetIdExtensions(val snippetId: SnippetId) extends AnyVal {
    def isOwnedBy(user2: Option[User]): Boolean = {
      (snippetId.user, user2) match {
        case (Some(SnippetUserPart(snippetLogin, _)),
              Some(User(userLogin, _, _))) =>
          snippetLogin == userLogin
        case _ => false
      }
    }

    def show: String = url

    def url: String = {
      snippetId match {
        case SnippetId(uuid, None) => uuid.value
        case SnippetId(uuid, Some(SnippetUserPart(login, update))) =>
          s"$login/$uuid/${update.getOrElse(0)}"
      }
    }

    def scalaJsUrl(end: String): String = {
      val middle = url
      s"/${Shared.scalaJsHttpPathPrefix}/$middle/$end"
    }
  }

  implicit class InputsExtensions(val inputs: Inputs) extends AnyVal {
    def sbtConfig: String = InputsHelper.sbtConfig(inputs)
    def sbtPluginsConfig: String = InputsHelper.sbtPluginsConfig(inputs)
    def isDefault: Boolean = InputsHelper.isDefault(inputs)

    // we only autocomplete for the default configuration
    // https://github.com/scalacenter/scastie/issues/275
    def isEnsimeEnabled: Boolean = isDefault

    def addScalaDependency(scalaDependency: ScalaDependency,
                           project: Project): Inputs =
      InputsHelper.addScalaDependency(inputs, scalaDependency, project)

    def removeScalaDependency(scalaDependency: ScalaDependency): Inputs =
      InputsHelper.removeScalaDependency(inputs, scalaDependency)
  }

  implicit class ScalaTargetExtensions(val scalaTarget: ScalaTarget)
      extends ScalaTargetExtensionsBase {
    private def dispatch: ScalaTargetExtensionsBase = {
      scalaTarget.value match {
        case ScalaTarget.Value.WrapPlainScala(t) =>
          new PlainScalaExtension(scalaTarget, t)

        case ScalaTarget.Value.WrapTypelevelScala(t) =>
          new TypelevelScalaExtension(scalaTarget, t)

        case ScalaTarget.Value.WrapDotty(t) =>
          new DottyExtension(scalaTarget, t)

        case ScalaTarget.Value.WrapScalaJs(t) =>
          new ScalaJsExtension(scalaTarget, t)

        case ScalaTarget.Value.WrapScalaNative(t) =>
          new ScalaNativeExtension(scalaTarget, t)

        case ScalaTarget.Value.Empty =>
          sys.error("ScalaTargetType.Empty")
      }
    }

    def targetType: ScalaTargetType = dispatch.targetType
    def scaladexRequest: Map[String, String] = dispatch.scaladexRequest
    def renderSbt(lib: ScalaDependency): String = dispatch.renderSbt(lib)
    def sbtConfig: String = dispatch.sbtConfig
    def sbtPluginsConfig: String = dispatch.sbtPluginsConfig
    def runtimeDependency: Option[ScalaDependency] = dispatch.runtimeDependency
    def show: String = dispatch.show
  }
}
