package org.scastie.client.components.fileHierarchy

import org.scastie.client.components.fileHierarchy.FileOrFolderUtils.{find, move, recomputePaths}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._


final case class FileHierarchy(rootFolder: Folder) {
  @inline def render: VdomElement = FileHierarchy.component()
}

object FileHierarchy {

  case class FileHierarchyState(root: Folder, selectedFile: String, dragSrc: String, dragOver: String)

  val initialState = FileHierarchyState(
    root = recomputePaths(
      Folder("root", isRoot = true)
        .add2("i.txt")
        .add2("j.txt")
        .add2("A/k.txt")
        .add2("B", isFolder = true)),
    selectedFile = "root",
    dragSrc = "",
    dragOver = "")

  val component =
    ScalaFnComponent.withHooks[Unit]
      .useState(initialState)
      .render($ => {
        val selectFile: String => Callback = {
          s =>
            $.hook1.modState(_.copy(selectedFile = s))
        }
        val dragInfoUpdate: DragInfo => Callback = {
          di =>
            if (di.start && !di.end) {
              $.hook1.modState(_.copy(dragSrc = di.f.path))
            } else if (!di.start && di.end) {
              $.hook1.modState {
                case FileHierarchyState(root, selectedFile, dragSrc, dragOver) =>
                  val srcPath = dragSrc
                  val dstPath = dragOver
                  Callback.log(srcPath + " to " + dstPath).runNow()

                  val newRoot = move(root, srcPath, dstPath)
                  val newSelectedFile = dstPath + "/" + find(root, srcPath).get.name
                  FileHierarchyState(newRoot, newSelectedFile, dragSrc, dragOver)
              }.runNow()
              Callback.empty
            } else if (!di.start && !di.end) {
              $.hook1.modState(_.copy(dragOver = di.f.path))
            } else {
              Callback.throwException(new IllegalArgumentException())
            }
        }
        <.div(
          FileOrFolderNode($.hook1.value.root, $.hook1.value.selectedFile, 0, selectFile, dragInfoUpdate).render
        )
      })
}
