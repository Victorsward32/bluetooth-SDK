package com.margelo.nitro.blesdk.bluetooth.gatt.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Message framing for high-throughput notification streams.
 *
 * Frame wire format (little-endian):
 * - 2 bytes: frame length (payload bytes)
 * - 2 bytes: sequence number
 * - N bytes: payload
 */
object StreamFrameCodec {
  private const val HEADER_BYTES = 4
  private const val MAX_PAYLOAD = 0xFFFF

  fun encode(sequence: Int, payload: ByteArray): ByteArray {
    require(payload.size <= MAX_PAYLOAD) { "Payload too large: ${payload.size}" }

    val buffer = ByteBuffer.allocate(HEADER_BYTES + payload.size)
      .order(ByteOrder.LITTLE_ENDIAN)
    buffer.putShort(payload.size.toShort())
    buffer.putShort(sequence.toShort())
    buffer.put(payload)
    return buffer.array()
  }

  class Decoder {
    private var pending = ByteArray(0)

    fun append(data: ByteArray): List<DecodedFrame> {
      if (data.isEmpty()) return emptyList()
      pending += data

      val out = mutableListOf<DecodedFrame>()
      var cursor = 0

      while (pending.size - cursor >= HEADER_BYTES) {
        val header = ByteBuffer.wrap(pending, cursor, HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        val payloadSize = header.short.toInt() and 0xFFFF
        val sequence = header.short.toInt() and 0xFFFF

        if (pending.size - cursor < HEADER_BYTES + payloadSize) {
          break
        }

        val payload = pending.copyOfRange(
          cursor + HEADER_BYTES,
          cursor + HEADER_BYTES + payloadSize,
        )
        out.add(DecodedFrame(sequence = sequence, payload = payload))
        cursor += HEADER_BYTES + payloadSize
      }

      pending = if (cursor >= pending.size) ByteArray(0) else pending.copyOfRange(cursor, pending.size)
      return out
    }

    fun reset() {
      pending = ByteArray(0)
    }
  }
}

data class DecodedFrame(
  val sequence: Int,
  val payload: ByteArray,
)
