package org.scastie.client.components.fileHierarchy

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.cls
import japgolly.scalajs.react.vdom.html_<^._


final case class FileOrFolderNode(file: FileOrFolder, selectedFile: String, depth: Int, selectFile: String => Callback) {

  @inline def render: VdomElement = FileOrFolderNode.component((file, selectedFile, depth, selectFile))
}

object FileOrFolderNode {


  val component = ScalaFnComponent.withHooks[(FileOrFolder, String, Int, String => Callback)]

    .useState(true)

    .render((props, isExpanded) => {
      val (file, s, depth, selectFile) = props

      var fafa = "file-o"
      if (file.isFolder) {
        fafa = "folder"
        if (isExpanded.value) {
          fafa = "folder-open"
        }
      }

      val handleClick = (e: ReactMouseEvent) => {
        e.stopPropagation()
        selectFile(file.name).runNow()
        if (file.isFolder) {
          isExpanded.modState(x => !x).runNow()
        }
        Callback.empty
      }

      <.div(
        <.div(
          ^.cls := s"hierarchy-list-row ${if (file.name.equals(s)) "file-selected" else ""}",
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
                  f: FileOrFolder => FileOrFolderNode(f, s, depth + 1, selectFile).render
                }.toVdomArray
              case _: File => EmptyVdom
            }
          } else {
            EmptyVdom
          }
        )
      )

    })
}
