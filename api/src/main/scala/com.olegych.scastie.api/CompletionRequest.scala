package com.olegych.scastie.api

import com.olegych.scastie.proto.EnsimeRequest

object CompletionRequest {
  def unapply(request: EnsimeRequest): Option[EnsimeRequest.Info]  = {
    request.value match {
      case EnsimeRequest.Value.WrapCompletion(EnsimeRequest.Completion(info)) =>
        Some(info)
      case _ => None
    }
  }
}