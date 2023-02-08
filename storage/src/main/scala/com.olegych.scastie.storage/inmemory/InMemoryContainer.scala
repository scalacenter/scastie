package com.olegych.scastie.storage.inmemory

import scala.concurrent.ExecutionContext

class InMemoryContainer(implicit val ec: ExecutionContext) extends InMemoryUsersContainer with InMemorySnippetsContainer {

}
