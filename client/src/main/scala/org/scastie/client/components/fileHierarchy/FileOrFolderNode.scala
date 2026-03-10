package org.scastie.client.components.fileHierarchy

import org.scastie.api._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.cls
import japgolly.scalajs.react.vdom.html_<^._

case class DragInfo(start: Boolean, end: Boolean, f: FileOrFolder)

final case class FileOrFolderNode(file: FileOrFolder, selectedFile: String, depth: Int, selectFile: File => Callback, dragStartOrEnd: DragInfo => Callback) {

  @inline def render: VdomElement = FileOrFolderNode.component((file, selectedFile, depth, selectFile, dragStartOrEnd))
}

object FileOrFolderNode {


  val component = ScalaFnComponent.withHooks[(FileOrFolder, String, Int, File => Callback, DragInfo => Callback)]

    .useState(true) //isExpanded
    .useState(false) //isMouseOver

    .render((props, isExpanded, isMouseOver) => {
      val (file, s, depth, selectFile, dragStartOrEnd) = props

      val icon = file match {
        case _: Folder if isExpanded.value => "folder-open"
        case _: Folder                     => "folder"
        case _: File                       => "file-o"
      }

      val handleClick = (e: ReactMouseEvent) => {
        e.stopPropagationCB >> (file match {
          case _: Folder    => isExpanded.modState(x => !x)
          case f: File      => selectFile(f)
        })
      }

      val onDragStart = (e: ReactDragEvent) => {
        dragStartOrEnd(DragInfo(start = true, end = false, file))
      }

      val onDragOver = (e: ReactDragEvent) => {
        isMouseOver.setState(true).when(file.isFolder) >>
          dragStartOrEnd(DragInfo(start = false, end = false, file))
      }

      val onDragEnd = (e: ReactDragEvent) => {
        dragStartOrEnd(DragInfo(start = false, end = true, file))
      }

      <.div(
        <.div(
          ^.cls := s"hierarchy-list-row",
          ^.cls := s"${if (file.path.equals(s)) " file-selected" else ""}",
          ^.cls := s"${if (isMouseOver.value) "file-mouse-over" else ""}",
          ^.onClick ==> handleClick,
          ^.draggable := true,
          ^.onDragStart ==> onDragStart,
          ^.onDragEnd ==> onDragEnd,
          ^.onDragOver ==> onDragOver,
          ^.onDragLeave --> isMouseOver.setState(false),
          ^.onMouseOver --> isMouseOver.setState(true),
          ^.onMouseLeave --> isMouseOver.setState(false),
          ^.key := file.path,
          <.div(
            ^.paddingLeft := s"${16 * depth}px",
            <.i(^.className := s"fa fa-${icon}"),
            file.name
          )
        ),

        <.div(
          if (isExpanded.value) {
            file match {
              case folder: Folder =>
                folder.children.map {
                  f: FileOrFolder => FileOrFolderNode(f, s, depth + 1, selectFile, dragStartOrEnd).render
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
