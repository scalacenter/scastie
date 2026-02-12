package org.scastie.client.components.fileHierarchy

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.cls
import japgolly.scalajs.react.vdom.html_<^._


final case class FileOrFolderNode(file: FileOrFolder, selected: Boolean = false, depth: Int) {

  @inline def render: VdomElement = FileOrFolderNode.component((file, selected, depth))
}

object FileOrFolderNode {


  val component = ScalaFnComponent.withHooks[(FileOrFolder, Boolean, Int)]

    .useState(true)

    .render((props, isExpanded) => {
      val (file, s, depth) = props
      val selected = (file.name.equals("File A.1") || file.name.length > 8) && !file.isFolder

      var fafa = "file-o"
      if (file.isFolder) {
        fafa = "folder"
        if (isExpanded.value) {
          fafa = "folder-open"
        }
      }

      val handleClick = (e: ReactMouseEvent) => {
        e.stopPropagation()
        if (file.isFolder) {
          isExpanded.modState(x => !x).runNow()
        } else {
          // TODO trigger selection
        }
        Callback.empty
      }

      <.div(
        <.div(
          ^.cls := s"hierarchy-list-row ${if (selected) "file-selected" else ""}",
          ^.onClick ==> handleClick,
          ^.key := file.name,
          <.div(
            ^.paddingLeft := s"${16 * depth}px",
            <.i(^.className := s"fa fa-${fafa}"),
            file.name
          )
        ),

        <.div(
          if (isExpanded.value) {
            file match {
              case folder: Folder =>
                folder.files.map {
                  f: FileOrFolder => FileOrFolderNode(f, depth = depth + 1).render
                }.toVdomArray
              case _: File => <.i()
            }
          } else {
            EmptyVdom
          }
        )
      )

    })
}
