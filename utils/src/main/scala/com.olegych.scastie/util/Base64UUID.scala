package com.olegych.scastie.util

import java.nio.ByteBuffer
import java.util.{Base64, UUID}

object Base64UUID {
  // example output: GGdknrcEQVu3elXyboKcYQ
  def create: String = {
    def toBase64(uuid: UUID): String = {
      val (high, low) =
        (uuid.getMostSignificantBits, uuid.getLeastSignificantBits)
      val buffer = ByteBuffer.allocate(java.lang.Long.BYTES * 2)
      buffer.putLong(high)
      buffer.putLong(low)
      val encoded = Base64.getMimeEncoder.encodeToString(buffer.array())
      encoded.take(encoded.length - 2)
    }

    var res: String = null
    val allowed = ('a' to 'z').toSet ++ ('A' to 'Z').toSet ++ ('0' to '9').toSet

    while (res == null || res.exists(c => !allowed.contains(c))) {
      val uuid = java.util.UUID.randomUUID()
      res = toBase64(uuid)
    }

    res
  }
}
