import java.nio.file.{Path, Files, SimpleFileVisitor, FileVisitResult}
import java.nio.file.attribute.BasicFileAttributes

object CopyRecursively {
  def apply(source: Path, destination: Path, directoryFilter: (Path, Int) => Boolean): Unit = {

    Files.walkFileTree(
      source,
      new CopyVisitor(source, destination, directoryFilter)
    )
  }
}

class CopyVisitor(source: Path, destination: Path, directoryFilter: (Path, Int) => Boolean) extends SimpleFileVisitor[Path] {

  private def relative(subPath: Path): Path =
    destination.resolve(source.relativize(subPath))

  private def pathDepth(dir: Path): Int = {
    dir.getNameCount - source.getNameCount - 1
  }

  override def preVisitDirectory(
      dir: Path,
      attrs: BasicFileAttributes
  ): FileVisitResult = {

    def copy(): FileVisitResult = {
      Files.createDirectories(relative(dir))
      FileVisitResult.CONTINUE
    }

    if (dir == source) {
      copy()
    } else if (directoryFilter(source.relativize(dir), pathDepth(dir))) {
      copy()
    } else {
      FileVisitResult.SKIP_SUBTREE
    }
  }

  override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
    Files.copy(file, relative(file))
    FileVisitResult.CONTINUE
  }
}
