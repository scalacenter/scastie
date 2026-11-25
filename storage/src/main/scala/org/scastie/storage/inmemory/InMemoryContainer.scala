package org.scastie.storage.inmemory

import scala.concurrent.ExecutionContext

class InMemoryContainer(implicit val ec: ExecutionContext) extends InMemorySnippetsContainer {

}
