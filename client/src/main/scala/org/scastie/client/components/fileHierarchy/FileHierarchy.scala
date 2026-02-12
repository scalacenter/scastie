package org.scastie.client.components.fileHierarchy

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._


trait FileOrFolder {
  val name: String = ""
  val isFolder: Boolean
}

case class File(override val name: String, content: String = "<empty content>") extends FileOrFolder {
  override val isFolder: Boolean = false
}

case class Folder(override val name: String, files: List[FileOrFolder] = List()) extends FileOrFolder {
  override val isFolder: Boolean = true
}

case class FileHierarchyState(
  root: Folder,
  selectedFile: String,
  dragSource: String = "",
  dragTarget: String = ""
)

final case class FileHierarchy(rootFolder: Folder) {
  @inline def render: VdomElement = FileHierarchy.component()
}

object FileHierarchy {

  val initialState = FileHierarchyState(
    root = Folder("Folder A",
      List(
        File("File A.1"),
        Folder("Folder A.B", List(
          File("File A.B.1"),
          File("File A.B.2"),
          File("FA.B.2"),
          File("File A.B.3")
        )),
        File("File A.2"),
        File("File A.3"),
        Folder("File A.C", List(
          File("File A.C.1")
        ))
      )
    ),
    selectedFile = "File A.B.1"
  )

  val component =
    ScalaFnComponent.withHooks[Unit]
      .useState(initialState)
      .render($ => {

        val fn: String => Callback = {
          s => $.hook1.modState(st => st.copy(selectedFile = s))
        }

        val fn2: DragInfo => Callback = {
          di =>
            if (di.start && !di.end) {
              $.hook1.modState(st => st.copy(dragSource = di.f.name)).void
            } else if (!di.start && di.end) {
              Callback.log($.hook1.value.dragSource + " to " + $.hook1.value.dragTarget) // TODO move in file hierarchy
            } else if (!di.start && !di.end) {
              $.hook1.modState(st => st.copy(dragTarget = di.f.name)).void
            } else {
              Callback.throwException(new IllegalArgumentException())
            }
        }
        <.div(
          FileOrFolderNode($.hook1.value.root, $.hook1.value.selectedFile, 0, fn, fn2).render
        )
      })
}
