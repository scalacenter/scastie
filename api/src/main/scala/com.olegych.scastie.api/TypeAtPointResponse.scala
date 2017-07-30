package com.olegych.scastie.api

import com.olegych.scastie.proto.EnsimeResponse
import EnsimeResponse.CompletionItem

object TypeAtPointResponse {
  def apply(symbol: Option[String]): EnsimeResponse = {
    EnsimeResponse(
      value = EnsimeResponse.Value.WrapTypeAtPoint(
        EnsimeResponse.TypeAtPoint(
          symbol = symbol
        )
      )
    )
  }
}