package org.scastie.client.components.tabStrip

import org.scastie.client.components.fileHierarchy.File
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

final case class TabStrip(tabs: TabStrip.TabStripState, changeSelection: File => Callback, closeTab: File => Callback) {
  @inline def render: VdomElement = TabStrip.TabStripComponent(tabs, changeSelection, closeTab)
}

object TabStrip {

  case class TabStripState(selectedTab: Option[File], activeTabs: List[File])

  object TabStripState {
    val empty: TabStripState = TabStripState(None, List())
  }

  private val TabStripComponent = ScalaFnComponent.withHooks[(TabStripState, File => Callback, File => Callback)]
    .render($ => {
      val tabs: List[File] = $._1.activeTabs
      val selectedTab: Option[File] = $._1.selectedTab
      val changeSelection = $._2
      val closeTab = $._3

      val handleTabClickCb: File => Callback = {
        file => changeSelection(file)
      }

      <.div(
        ^.className := "tab-strip",
        tabs.map { file: File =>
          renderTab(
            file.name,
            file.path,
            selectedTab.exists(_.path == file.path)
          )(handleTabClickCb(file), closeTab(file))
        }.toVdomArray
      )
    })

  private def renderTab(tabText: String, tabKey: String, isActive: Boolean)(onTabClick: Callback, onTabClose: Callback): VdomElement = {
    <.div(
      ^.key := tabKey,
      ^.className := "tab-strip-item",
      ^.className := (if (isActive) "active" else ""),
      ^.onClick --> onTabClick,
      <.span(tabText),
      <.p(
        ^.onClick ==> { (e: ReactMouseEvent) =>
          e.stopPropagation()
          onTabClose
        },
        ^.className := "close-btn",
        "x")
    )
  }
}
