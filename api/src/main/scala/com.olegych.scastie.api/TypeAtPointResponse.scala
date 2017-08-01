package com.olegych.scastie.api

import com.olegych.scastie.proto.EnsimeResponse
import EnsimeResponse.CompletionItem

object TypeAtPointResponse {
  def unapply(response: EnsimeResponse): Option[Option[String]] = {
    response.value match {
      case EnsimeResponse.Value
            .WrapTypeAtPoint(EnsimeResponse.TypeAtPoint(symbol)) =>
        Some(symbol)
      case _ => None
    }
  }
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
