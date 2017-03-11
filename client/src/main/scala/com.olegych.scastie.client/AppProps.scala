package com.olegych.scastie
package client

import api.SnippetId

import japgolly.scalajs.react.extra.router.RouterCtl

case class AppProps(
    router: Option[RouterCtl[Page]],
    snippetId: Option[SnippetId],
    embedded: Option[EmbededOptions]
) {
  def isEmbedded: Boolean = embedded.isDefined
}
