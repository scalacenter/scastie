package org.scastie.client.components.fileHierarchy

import org.scastie.api._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._


final case class FileHierarchy(rootFolder: Folder, openFile: File => Callback, moveFile: (String, String) => Callback) {
  @inline def render: VdomElement = FileHierarchy.component((rootFolder, openFile, moveFile))
}

object FileHierarchy {

  case class FileHierarchyState(selectedFile: String, dragSrc: String, dragOver: String)

  val initialState = FileHierarchyState(
    selectedFile = "",
    dragSrc = "",
    dragOver = "")

  val component =
    ScalaFnComponent.withHooks[(Folder, File => Callback, (String, String) => Callback)]
      .useState(initialState)
      .render((props, fhs) => {
        val rootFolder = props._1
        val openFile = props._2
        val moveFileCb = props._3

        val selectFile: File => Callback = {
          f => openFile(f)
        }
        val dragInfoUpdate: DragInfo => Callback = {
          di =>
            if (di.start && !di.end) {
              fhs.modState(_.copy(dragSrc = di.f.path))
            } else if (!di.start && di.end) {
              val srcPath = fhs.value.dragSrc
              val dstPath = fhs.value.dragOver
              moveFileCb(srcPath, dstPath)
            } else if (!di.start && !di.end) {
              fhs.modState(_.copy(dragOver = di.f.path))
            } else {
              Callback.throwException(new IllegalArgumentException())
            }
        }
        <.div(
          FileOrFolderNode(rootFolder, fhs.value.selectedFile, 0, selectFile, dragInfoUpdate).render
        )
      })
}
