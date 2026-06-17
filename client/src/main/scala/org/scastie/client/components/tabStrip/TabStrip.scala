package org.scastie.client.components.tabStrip

import org.scastie.api.File
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

final case class TabStrip(tabStripState: TabStrip.TabStripState, changeSelection: TabStrip.Tab => Callback, closeTab: TabStrip.Tab => Callback) {
  @inline def render: VdomElement = TabStrip.TabStripComponent(tabStripState, changeSelection, closeTab)
}

object TabStrip {

  case class Tab(tabId: String, title: String)

  object Tab {
    def fromFile(file: File): Tab = Tab(file.path, file.name)
  }

  case class TabStripState(selectedTab: Option[Tab], activeTabs: List[Tab])

  object TabStripState {
    val empty: TabStripState = TabStripState(None, List())
  }

  private val TabStripComponent = ScalaFnComponent.withHooks[(TabStripState, Tab => Callback, Tab => Callback)]
    .render($ => {
      val tabs: List[Tab] = $._1.activeTabs
      val selectedTab: Option[Tab] = $._1.selectedTab
      val changeSelection = $._2
      val closeTab = $._3

      val handleTabClickCb: Tab => Callback = {
        tab => changeSelection(tab)
      }

      <.div(
        ^.className := "tab-strip",
        tabs.map { tab: Tab =>
          renderTab(
            tab.title,
            tab.tabId,
            selectedTab.exists(_.tabId == tab.tabId)
          )(handleTabClickCb(tab), closeTab(tab))
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
