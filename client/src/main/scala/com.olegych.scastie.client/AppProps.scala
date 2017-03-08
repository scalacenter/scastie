package com.olegych.scastie
package client

case class AppProps(
    router: Option[RouterCtl[Page]],
    snippetId: Option[SnippetId],
    embedded: Option[EmbededOptions]
) {
  def isEmbedded: Boolean = embedded.isDefined
}
