import java.nio.file._
import scala.collection.JavaConverters._
val dir = Paths.get("/home/gui/snippetsmis")
Files.isDirectory(dir)

val inputs = collection.mutable.Buffer.empty[Path]

val ds = Files.newDirectoryStream(dir)
ds.asScala.foreach{user =>
  val userStream = Files.newDirectoryStream(user)
  userStream.asScala.foreach{base =>
    if(Files.isDirectory(base)) {
      val baseStream = Files.newDirectoryStream(base)
      baseStream.asScala.foreach{sid =>
        if(Files.isRegularFile(sid.resolve("input.json"))) {
          inputs += sid
        }
      }
      baseStream.close
    }
  }
  userStream.close
}
ds.close()


