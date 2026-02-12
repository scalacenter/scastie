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


final case class FileHierarchy(rootFolder: Folder) {
  @inline def render: VdomElement = FileHierarchy.component()
}

object FileHierarchy {

  val initialState =
    Folder("Folder A",
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
    )

  val component =
    ScalaFnComponent.withHooks[Unit]
      .useState(initialState)
      .render($ =>
        <.div(
          FileOrFolderNode($.hook1.value, true, 0).render
        )
      )
}
