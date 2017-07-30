package com.olegych.scastie.api

import com.olegych.scastie.proto.EnsimeRequest

object TypeAtPointRequest {
  def unapply(request: EnsimeRequest): Option[EnsimeRequest.Info]  = {
    request.value match {
      case EnsimeRequest.Value.WrapTypeAtPoint(EnsimeRequest.TypeAtPoint(info)) =>
        Some(info)
      case _ => None
    }
  }
}