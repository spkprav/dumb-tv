package org.dumbtv.launcher.ads

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor

/** Bundled Latin OCR: frames are processed on-device, without a model server. */
class LocalFrameOcr {
    data class Line(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)
    data class Result(val text: String, val lines: List<Line>)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    fun process(bitmap: Bitmap, executor: Executor, complete: (Result?, Exception?) -> Unit) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener(executor) { task ->
            if (task.isSuccessful) {
                val result = task.result
                complete(Result(result.text, result.textBlocks.flatMap { block -> block.lines.mapNotNull { line ->
                    line.boundingBox?.let { box -> Line(line.text, box.left, box.top, box.right, box.bottom) }
                } }), null)
            } else complete(null, task.exception ?: IllegalStateException("OCR returned no result"))
        }
    }
    fun close() = recognizer.close()
}
