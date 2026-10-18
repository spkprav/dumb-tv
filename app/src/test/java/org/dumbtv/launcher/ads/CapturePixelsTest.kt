package org.dumbtv.launcher.ads

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class CapturePixelsTest {
    @Test fun removesRowPaddingEvenWhenFinalRowHasNoPadding() {
        val source = byteArrayOf(1,2,3,4,5,6,7,8, 99,99,99,99, 9,10,11,12,13,14,15,16)
        assertArrayEquals(ByteArray(16) { (it + 1).toByte() }, CapturePixels.packedRgba(ByteBuffer.wrap(source), 2, 2, 4, 12))
    }
    @Test fun respectsTheBufferPositionWithoutChangingIt() {
        val buffer = ByteBuffer.wrap(byteArrayOf(99,99,1,2,3,4)).apply { position(2) }
        assertArrayEquals(byteArrayOf(1,2,3,4), CapturePixels.packedRgba(buffer, 1, 1, 4, 4))
        assertEquals(2, buffer.position())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsTruncatedFrame() {
        CapturePixels.packedRgba(ByteBuffer.allocate(15), 2, 2, 4, 8)
    }
}
