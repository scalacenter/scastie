package org.scastie.api

import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

sealed trait FileOrFolder {
  val name: String
  val path: String
  def isFolder: Boolean
}

object FileOrFolder {

  implicit val fileOrFolderEncoder: Encoder[FileOrFolder] = Encoder.instance {
    case f: File   => f.asJson
    case f: Folder => f.asJson
  }

  implicit val fileOrFolderDecoder: Decoder[FileOrFolder] = Decoder.instance { cursor =>
    cursor.downField("isFolder").as[Boolean].flatMap {
      case true  => cursor.as[Folder]
      case false => cursor.as[File]
    }
  }

}

case class File(
  override val name: String,
  content: String = "",
  override val path: String = ""
) extends FileOrFolder {
  def isFolder: Boolean = false
}

object File {
  implicit val fileEncoder: Encoder[File] =
    deriveEncoder[File].mapJson(_.deepMerge(Json.obj("isFolder" -> Json.fromBoolean(false))))
  implicit val fileDecoder: Decoder[File] = deriveDecoder[File]
}

object Folder {

  def singleton(code: String): Folder = {
    Folder("root", List(File("Main.scala", code, "/root/Main.scala")), "/root", isRoot = true)
  }

  implicit val folderEncoder: Encoder[Folder] =
    deriveEncoder[Folder].mapJson(_.deepMerge(Json.obj("isFolder" -> Json.fromBoolean(true))))
  implicit val folderDecoder: Decoder[Folder] = deriveDecoder[Folder]
}

case class Folder(
  override val name: String,
  children: List[FileOrFolder] = List(),
  override val path: String = "",
  isRoot: Boolean = false
) extends FileOrFolder {
  def isFolder: Boolean = true

  def isEmpty: Boolean = children.isEmpty

  def childHeadFileContent: String = {
    children.headOption match {
      case Some(f: File) => f.content
      case _             => ""
    }
  }

  def take(i: Int): String = childHeadFileContent.take(i)

  def split(nl: String): Array[String] = childHeadFileContent.split(nl)

  def add(ff: FileOrFolder): Folder = {
    if (this.path.nonEmpty) {
      val f = FileOrFolderUtils.prependPath(this.path, ff)
      this.copy(children = this.children :+ f)
    } else {
      val rootName = name
      val f        = if (isRoot) FileOrFolderUtils.prependPath(rootName, ff) else ff
      this.copy(children = this.children :+ f)
    }
  }

  def add2(path: String, isFolder: Boolean = false): Folder = {
    path.split("/").toList match {
      case Nil => this
      case head :: Nil =>
        val newFilePath = this.path + "/" + head
        val newFile     = if (isFolder) Folder(head, path = newFilePath) else File(head, newFilePath)
        copy(children = children :+ newFile)
      case head :: tail =>
        val newIntermediate = Folder(head, path = this.path + "/" + head)
        val folder          = children.find(_.name.equals(head)).getOrElse(newIntermediate).asInstanceOf[Folder]
        copy(children = children :+ folder.add2(tail.mkString("/"), isFolder))
    }
  }

}

object FileOrFolderUtils {

  def find(root: Folder, path: String): Option[FileOrFolder] = {
    if (root.path == path) {
      Some(root)
    } else {
      root.children.foldLeft[Option[FileOrFolder]](None) { (acc, fileOrFolder) =>
        acc.orElse(fileOrFolder match {
          case f: File if f.path == path => Some(f)
          case l: Folder                 => find(l, path)
          case _                         => None
        })
      }
    }
  }

  def remove(root: Folder, path: String): Folder = {
    root.copy(children =
      root.children
        .map {
          case f: File   => f
          case l: Folder => remove(l, path)
        }
        .filterNot(_.path == path)
    )
  }

  def insert(root: Folder, newFolder: FileOrFolder, path: String): Folder = {
    recomputePaths(
      root.copy(children =
        if (root.path.equals(path)) {
          root.children.filterNot(_.name == newFolder.name) :+ newFolder
        } else root.children.map {
          case f: File =>
            if (f.path.equals(path)) throw new IllegalArgumentException("Cannot insert into a file")
            else f
          case l: Folder => insert(l, newFolder, path)
        }
      )
    )
  }

  def move(root: Folder, srcPath: String, dstPath: String): Folder = {
    if (dstPath.startsWith(srcPath)) {
      root
    } else {
      val movingFolder = find(root, srcPath).get
      try {
        recomputePaths(
          insert(remove(root, srcPath), movingFolder, dstPath)
        )
      } catch {
        case _: IllegalArgumentException =>
          Console.println("Cannot drop into a file :)")
          root
      }
    }
  }

  def recomputePaths(f: Folder, prefix: String = ""): Folder = {
    f.copy(
      path = prefix + "/" + f.name,
      children = f.children.map {
        case folder: Folder => recomputePaths(folder, prefix + "/" + f.name)
        case file: File     => file.copy(path = prefix + "/" + f.name + "/" + file.name)
      }
    )
  }

  def allFiles(root: Folder): List[File] = {
    root.children.flatMap {
      case f: File   => List(f)
      case l: Folder => allFiles(l)
    }
  }

  def updateFile(root: Folder, newFile: File): Folder = {
    root.copy(children = root.children.map {
      case f: File if f.path == newFile.path => newFile
      case f: File                           => f
      case l: Folder                         => updateFile(l, newFile)
    })
  }

  def prependPath(p: String, fileOrFolder: FileOrFolder): FileOrFolder = {
    fileOrFolder match {
      case f: File =>
        val nonEmptyPath = if (f.path.isEmpty) f.name else f.path
        f.copy(path = p + "/" + nonEmptyPath)
      case f: Folder =>
        val nonEmptyPath = if (f.path.isEmpty) f.name else f.path
        f.copy(children = f.children.map(x => prependPath(p + "/" + f.name, x)), path = p + "/" + nonEmptyPath)
    }
  }

}
