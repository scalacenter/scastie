package com.olegych.scastie.api

import com.olegych.scastie.proto.EnsimeResponse
import EnsimeResponse.CompletionItem

object CompletionResponse {
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