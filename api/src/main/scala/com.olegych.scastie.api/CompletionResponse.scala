package com.olegych.scastie.api

import com.olegych.scastie.proto.EnsimeResponse
import EnsimeResponse.CompletionItem

object CompletionResponse {
  def unapply(response: EnsimeResponse): Option[Seq[EnsimeResponse.CompletionItem]]  = {
    response.value match {
      case EnsimeResponse.Value.WrapCompletion(EnsimeResponse.Completion(completions)) =>
        Some(completions)
      case _ => None
    }
  }
  def apply(completions: Seq[CompletionItem]): EnsimeResponse = {
    EnsimeResponse(
      value = EnsimeResponse.Value.WrapCompletion(
        EnsimeResponse.Completion(
          completions = completions
        )
      )
    )
  }
}