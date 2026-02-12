package org.scastie.client.components.fileHierarchy

import org.scastie.client.components.fileHierarchy.FileOrFolderUtils.{find, move, recomputePaths}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._


final case class FileHierarchy(rootFolder: Folder, openFile: File => Callback) {
  @inline def render: VdomElement = FileHierarchy.component((rootFolder, openFile))
}

object FileHierarchy {

  case class FileHierarchyState(root: Folder, selectedFile: String, dragSrc: String, dragOver: String)

  val initialState = FileHierarchyState(
    root = recomputePaths(
      Folder("root", isRoot = true)
        .add(File("ClientMain.scala", "client main content"))
        .add(File("LocalStorage.scala", "local storage content"))
        .add(Folder("awesomeFolder")
          .add(File("Routing.scala", "routing content"))
          .add(File("data.txt", "data content"))
        )
        .add(Folder("other"))),
    selectedFile = "root",
    dragSrc = "",
    dragOver = "")

  val component =
    ScalaFnComponent.withHooks[(Folder, File => Callback)]
      .useState(initialState)
      .render((props, fhs) => {
        val openFile = props._2

        val selectFile: File => Callback = {
          f => openFile(f)
        }
        val dragInfoUpdate: DragInfo => Callback = {
          di =>
            if (di.start && !di.end) {
              fhs.modState(_.copy(dragSrc = di.f.path))
            } else if (!di.start && di.end) {
              fhs.modState {
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
              fhs.modState(_.copy(dragOver = di.f.path))
            } else {
              Callback.throwException(new IllegalArgumentException())
            }
        }
        <.div(
          FileOrFolderNode(fhs.value.root, fhs.value.selectedFile, 0, selectFile, dragInfoUpdate).render
        )
      })
}
