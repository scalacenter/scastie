package scastie.metals

import scala.meta.internal.metals.CompilerOffsetParams
import java.nio.file.Files
import java.nio.file.Path
import com.olegych.scastie.api.ScalaTarget
import com.olegych.scastie.api.ScalaDependency
import io.circe.Encoder
import io.circe.Decoder
import io.circe.generic.semiauto._
import io.circe._
import io.circe.syntax._
import io.circe.Codec
import scala.reflect.io.AbstractFile
import java.net.URI
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.internal.util.NoSourceFile
import scala.meta.internal.metals.ScalaTarget.apply


case class ScastieMetalsOptions(dependencies: Set[ScalaDependency], scalaTarget: ScalaTarget)
case class ScastieOffsetParams(content: String, offset: Int) {
  def toOffsetParams: CompilerOffsetParams = {
    val noSourceFilePath = Path.of(NoSourceFile.path).toUri
    new CompilerOffsetParams(noSourceFilePath, content, offset)
  }

}

case class LSPRequestDTO(options: ScastieMetalsOptions, offsetParams: ScastieOffsetParams)

object DTOCodecs {

  implicit val scalaDependencyDecoder: Decoder[ScalaDependency] = deriveDecoder
  implicit val scalaDependencyEncoder: Encoder[ScalaDependency] = deriveEncoder

  implicit val lspRequestDecoder: Decoder[LSPRequestDTO] = deriveDecoder
  implicit val lspRequestEncoder: Encoder[LSPRequestDTO] = deriveEncoder

}



