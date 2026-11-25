package org.scastie.client.components.fileHierarchy

import org.scastie.api._
import japgolly.scalajs.react._
import japgolly.scalajs.react.hooks.Hooks.UseState
import japgolly.scalajs.react.vdom.html_<^._

/**
 * Provides information about drag over or end.
 *
 * @param end          true if drag ended
 * @param fileOrFolder file or folder that is being dragged (end is true) or dragged over (end is false)
 */
case class DragInfo(end: Boolean, fileOrFolder: FileOrFolder)

/**
 * Node in the file hierarchy view that represents a file or a folder.
 * In case of a folder we can collapse or expand the children.
 * It can be dragged around and dropped on another folder.
 *
 * @param fileOrFolder  node that we display
 * @param selectedFile  selected file's path in the view hierarchy
 * @param depth         depth of the node in the hierarchy from root (used to shift the node to the right)
 * @param selectFile    callback when user clicks on a file
 * @param dragOverOrEnd callback when user drags over a folder or ends dragging a file or folder
 */
final case class FileOrFolderNode(fileOrFolder: FileOrFolder, selectedFile: String, depth: Int, selectFile: File => Callback, dragOverOrEnd: DragInfo => Callback) {

  @inline def render: VdomElement = FileOrFolderNode.component((fileOrFolder, selectedFile, depth, selectFile, dragOverOrEnd))
}

object FileOrFolderNode {

  val component = ScalaFnComponent.withHooks[(FileOrFolder, String, Int, File => Callback, DragInfo => Callback)]

    .useState(true) //isExpanded
    .useState(false) //isMouseOver

    .render((props, isExpanded: UseState[Boolean], isMouseOver: UseState[Boolean]) => {
      val (fileOrFolder, s, depth, selectFile, dragOverOrEnd) = props

      val icon = fileOrFolder match {
        case _: File                       => "file-o"
        case _: Folder if isExpanded.value => "folder-open"
        case _: Folder                     => "folder"
      }

      val handleClick = (e: ReactMouseEvent) => {
        e.stopPropagation()
        fileOrFolder match {
          case f: File   => selectFile(f)
          case _: Folder => isExpanded.modState(x => !x)
        }
      }

      val onDragOver = (e: ReactDragEvent) => {
        if (fileOrFolder.isFolder) {
          isMouseOver.setState(true) >>
            dragOverOrEnd(DragInfo(end = false, fileOrFolder))
        } else Callback.empty
      }

      val onDragEnd = (e: ReactDragEvent) => {
        dragOverOrEnd(DragInfo(end = true, fileOrFolder))
      }

      <.div(
        <.div(
          ^.cls := s"hierarchy-list-row",
          ^.cls := s"${if (fileOrFolder.path.equals(s)) " file-selected" else ""}",
          ^.cls := s"${if (isMouseOver.value) "file-mouse-over" else ""}",
          ^.onClick ==> handleClick,
          ^.draggable := true,
          ^.onDragEnd ==> onDragEnd,
          ^.onDragOver ==> onDragOver,
          ^.onDragLeave --> isMouseOver.setState(false),
          ^.onMouseOver --> isMouseOver.setState(true),
          ^.onMouseLeave --> isMouseOver.setState(false),
          ^.key := fileOrFolder.path,
          <.div(
            ^.paddingLeft := s"${16 * depth}px",
            <.i(^.className := s"fa fa-${icon}"),
            fileOrFolder.name
          )
        ),

        <.div(
          if (isExpanded.value) {
            fileOrFolder match {
              case folder: Folder =>
                folder.children.map {
                  f: FileOrFolder => FileOrFolderNode(f, s, depth + 1, selectFile, dragOverOrEnd).render
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
