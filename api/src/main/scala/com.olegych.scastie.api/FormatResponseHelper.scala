package com.olegych.scastie.api

import com.olegych.scastie.proto.FormatResponse

object FormatResponseHelper {
  def success(formattedCode: String): FormatResponse.Value = {
    FormatResponse.Value.WrapSuccess(
      FormatResponse.Success(formattedCode = formattedCode)
    )
  }

  def failure(stackTrace: String): FormatResponse.Value = {
    FormatResponse.Value.WrapFailure(
      FormatResponse.Failure(stackTrace = stackTrace)
    )
  }
}
