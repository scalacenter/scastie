// package com.olegych.scastie
// package balancer

// class ApiImpl(pasteActor: ActorRef, ip: String)(
//     implicit timeout: Timeout,
//     executionContext: ExecutionContext)
//     extends Api {

//   def run(inputs: Inputs): Future[Ressource] = {
//     (pasteActor ? InputsWithIp(inputs, ip)).mapTo[Ressource]
//   }

//   def save(inputs: Inputs): Future[Ressource] = run(inputs)

//   def fetch(id: Int): Future[Option[FetchResult]] = {
//     (pasteActor ? GetPaste(id)).mapTo[Option[FetchResult]]
//   }

//   def format(formatRequest: FormatRequest): Future[FormatResponse] = {
//     (pasteActor ? formatRequest).mapTo[FormatResponse]
//   }
// }
