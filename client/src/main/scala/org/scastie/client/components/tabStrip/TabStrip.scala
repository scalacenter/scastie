package org.scastie.client.components.tabStrip

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

final case class TabStrip() {
  @inline def render: VdomElement = TabStrip.TabStripComponent(Seq("Main.scala", "Second.scala", "FileManager.scala", "YetAnotherFile.scala", "LotsOfFiles.scala", "AmazinFileAmount.scala", "WeLoveScastie.scala", "NoIdeaNameFIle.scala", "HelloWorld.scala", "Raendom.scala"))
}

object TabStrip {

  private case class TabStripState(selectionIdx: Int, activeTabs: List[Int])

  private val initialTabStripState: TabStripState = TabStripState(0, List())

  private val TabStripComponent = ScalaFnComponent.withHooks[Seq[String]]
    .useState(initialTabStripState)
    .render($ => {
      val tabs: Seq[String] = $.props
      val localTabStripState: TabStripState = $.hook1.value

      val handleTabClickCb: Int => Callback = {
        tabIndex =>
          $.hook1.modState(c =>
            TabStripState(tabIndex,
              c.activeTabs
            )).void
      }

      <.div(
        ^.className := "tab-strip",
        tabs.zipWithIndex.map { case (tab, tabIndex) =>
          renderTab(
            tab,
            tabIndex,
            tabIndex == localTabStripState.selectionIdx
          )(handleTabClickCb(tabIndex))
        }.toVdomArray
      )
    })

  private def renderTab(tab: String, tabIndex: Int, isActive: Boolean)(onTabClick: Callback): VdomElement = {
    <.div(
      ^.key := tabIndex,
      ^.className := "tab-strip-item",
      ^.className := (if (isActive) "active" else ""),
      ^.onClick --> onTabClick,
      <.span(tab),
      <.p(
        ^.className := "close-btn",
        "x")
    )
  }
}
