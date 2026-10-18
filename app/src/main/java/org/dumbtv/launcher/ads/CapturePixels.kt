package org.dumbtv.launcher.ads

import java.nio.ByteBuffer

/** ImageReader may pad rows and omit padding after the final row. */
object CapturePixels {
    fun packedRgba(buffer: ByteBuffer, width: Int, height: Int, pixelStride: Int, rowStride: Int): ByteArray {
        require(width in 1..1920 && height in 1..4096 && pixelStride == 4)
        val rowBytes = width * 4
        require(rowStride >= rowBytes)
        require((height - 1L) * rowStride + rowBytes <= buffer.remaining())
        val source = buffer.duplicate()
        val start = source.position()
        val packed = ByteArray(rowBytes * height)
        repeat(height) { row ->
            source.position(start + row * rowStride)
            source.get(packed, row * rowBytes, rowBytes)
        }
        return packed
    }
}
