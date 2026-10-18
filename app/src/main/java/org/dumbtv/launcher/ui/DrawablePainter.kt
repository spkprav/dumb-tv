package org.dumbtv.launcher.ui

import android.graphics.drawable.Drawable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import kotlin.math.roundToInt

class DrawablePainter(drawable: Drawable) : Painter() {
    private val drawable = drawable.constantState?.newDrawable()?.mutate() ?: drawable

    override val intrinsicSize: Size
        get() {
            val width = drawable.intrinsicWidth.toFloat().takeIf { it > 0f } ?: 1f
            val height = drawable.intrinsicHeight.toFloat().takeIf { it > 0f } ?: 1f
            return Size(width, height)
        }

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))
            drawable.draw(canvas.nativeCanvas)
        }
    }
}
