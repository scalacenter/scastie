package com.olegych.scastie.storage.filesystem

import java.nio.file._
import scala.concurrent.ExecutionContext

class FilesystemContainer(val root: Path, val oldRoot: Path)(
  implicit val ec: ExecutionContext
) extends FilesystemUsersContainer with FilesystemSnippetsContainer
