package org.scastie.client.components.fileHierarchy

import org.scastie.client.components.fileHierarchy.FileOrFolderUtils.prependPath

sealed trait FileOrFolder {
  val name: String
  val path: String
  val isFolder: Boolean
  val isRoot: Boolean
}

case class File(override val name: String, content: String = "", override val path: String = "") extends FileOrFolder {
  override val isFolder: Boolean = false
  override val isRoot: Boolean = false
}

case class Folder(override val name: String, children: List[FileOrFolder] = List(), override val path: String = "", override val isRoot: Boolean = false) extends FileOrFolder {
  override val isFolder: Boolean = true

  def add(ff: FileOrFolder): Folder = {
    if (this.path.nonEmpty) {
      val f = prependPath(this.path, ff)
      this.copy(children = this.children :+ f)
    } else {
      val rootName = name
      val f = if (isRoot) prependPath(rootName, ff) else ff
      this.copy(children = this.children :+ f)
    }
  }

  def add2(path: String, isFolder: Boolean = false): Folder = {
    path.split("/").toList match {
      case Nil => this
      case head :: Nil =>
        val newFilePath = this.path + "/" + head
        val newFile = if (isFolder) Folder(head, path = newFilePath) else File(head, newFilePath)
        copy(children = children :+ newFile)
      case head :: tail =>
        val newIntermediate = Folder(head, path = this.path + "/" + head)
        val folder = children.find(_.name.equals(head)).getOrElse(newIntermediate).asInstanceOf[Folder]
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
          case l: Folder => find(l, path)
          case _ => None
        })
      }
    }
  }

  def remove(root: Folder, path: String): Folder = {
    root.copy(children = root.children
      .map {
        case f: File => f
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
        } else
          root.children.map {
            case f: File =>
              if (f.path.equals(path)) throw new IllegalArgumentException("BRUH")
              else f
            case l: Folder => insert(l, newFolder, path)
          }
      ))
  }

  def move(root: Folder, srcPath: String, dstPath: String): Folder = {
    if (dstPath.startsWith(srcPath)) {
      root
    } else {
      val movingFolder = find(root, srcPath).get
      try {
        recomputePaths(
          insert(
            remove(root, srcPath),
            movingFolder,
            dstPath)
        )
      } catch {
        case x: IllegalArgumentException =>
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
        case file: File => file.copy(path = prefix + "/" + f.name + "/" + file.name)
      })
  }

  def allFiles(root: Folder): List[File] = {
    root.children.flatMap {
      case f: File => List(f)
      case l: Folder => allFiles(l)
    }
  }

  def prependPath(p: String, fileOrFolder: FileOrFolder): FileOrFolder = {
    fileOrFolder match {
      case File(name, content, path) =>
        val nonEmptyPath = if (path.isEmpty) name else path
        File(name, content, p + "/" + nonEmptyPath)
      case Folder(name, files, path, ir) =>
        val nonEmptyPath = if (path.isEmpty) name else path
        Folder(name, files.map(x => prependPath(p + "/" + name, x)), p + "/" + nonEmptyPath)
    }
  }
}
