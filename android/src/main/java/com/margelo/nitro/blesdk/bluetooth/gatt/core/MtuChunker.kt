package com.margelo.nitro.blesdk.bluetooth.gatt.core

import kotlin.math.min

/**
 * Utility for MTU-aware chunking.
 *
 * ATT payload max:
 * - Write request/command characteristic value size is MTU - 3.
 */
object MtuChunker {
  private const val ATT_WRITE_OVERHEAD = 3
  private const val DEFAULT_MTU = 23
  private const val MAX_MTU = 517

  fun attPayloadBytes(mtu: Int): Int {
    val bounded = mtu.coerceIn(DEFAULT_MTU, MAX_MTU)
    return (bounded - ATT_WRITE_OVERHEAD).coerceAtLeast(1)
  }

  fun split(data: ByteArray, mtu: Int): List<ByteArray> {
    if (data.isEmpty()) return emptyList()

    val chunkSize = attPayloadBytes(mtu)
    val chunks = ArrayList<ByteArray>((data.size + chunkSize - 1) / chunkSize)
    var offset = 0
    while (offset < data.size) {
      val end = min(offset + chunkSize, data.size)
      chunks.add(data.copyOfRange(offset, end))
      offset = end
    }
    return chunks
  }
}
