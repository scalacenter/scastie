package scastie.endpoints


import sttp.tapir._


object DownloadEndpoints {

  val endpointBase = endpoint.in("api")
  val downloadSnippetEndpoint = SnippetMatcher.getApiSnippetEndpoints(endpointBase.in("download")).map { endpoint =>
    endpoint.out(fileBody)
  }

  val endpoints = downloadSnippetEndpoint
}
