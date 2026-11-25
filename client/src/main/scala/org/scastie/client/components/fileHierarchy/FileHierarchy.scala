package org.scastie.client.components.fileHierarchy

import org.scastie.api._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._


/**
 * @param rootFolder       hierarchy to display
 * @param openFile         when user clicks on a file in this hierarchy this function is called
 * @param moveFileOrFolder when user moves a file or folder this function is called, the string is the destination folder path
 */
final case class FileHierarchy(rootFolder: Folder, openFile: File => Callback, moveFileOrFolder: (FileOrFolder, String) => Callback) {
  @inline def render: VdomElement = FileHierarchy.component((rootFolder, openFile, moveFileOrFolder))
}

object FileHierarchy {

  /**
   * @param selectedFile currently selected file in the hierarchy view
   * @param dragOver     FileOrFolder's path that the user could drop to
   */
  private case class FileHierarchyState(selectedFile: String, dragOver: String)

  private val initialFhs = FileHierarchyState(selectedFile = "", dragOver = "")

  private val component = ScalaFnComponent.withHooks[(Folder, File => Callback, (FileOrFolder, String) => Callback)]
    .useState(initialFhs)
    .render((props, fhs) => {
      val rootFolder: Folder = props._1
      val openFile: File => Callback = props._2
      val moveFileOrFolder: (FileOrFolder, String) => Callback = props._3

      val selectFile: File => Callback = {
        (f: File) => openFile(f) >> fhs.modState(_.copy(selectedFile = f.path))
      }

      val dragInfoUpdate: DragInfo => Callback = {
        dragInfo =>
          if (dragInfo.end) {
            val src = dragInfo.fileOrFolder
            val dstPath = fhs.value.dragOver
            if (dstPath.isEmpty) {
              /* do nothing if the user never dragged over a folder and just dropped the fileOrFolder on a file */
              Callback.empty
            } else {
              moveFileOrFolder(src, dstPath) >>
              fhs.setState(FileHierarchyState(selectedFile = dstPath.stripSuffix("/") + "/" + src.name, dragOver = ""))
            }
          } else {
            fhs.modState(_.copy(dragOver = dragInfo.fileOrFolder.path))
          }
      }
      <.div(
        FileOrFolderNode(rootFolder, fhs.value.selectedFile, 0, selectFile, dragInfoUpdate).render
      )
    })
}
