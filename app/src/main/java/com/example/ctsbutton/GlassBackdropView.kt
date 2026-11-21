package com.example.ctsbutton

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/** Quiet ambient color behind translucent surfaces; moves slightly when scrolled. */
class GlassBackdropView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var scrollOffset = 0f

    fun setScrollOffset(offset: Int) {
        val value = (offset * 0.12f).coerceIn(0f, 120f * resources.displayMetrics.density)
        if (scrollOffset != value) {
            scrollOffset = value
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // A plain graphite canvas in system dark mode: no color pools or gradients.
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (isDarkMode) {
            canvas.drawColor(tint(R.color.app_background))
            return
        }

        paint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(tint(R.color.backdrop_start), tint(R.color.backdrop_middle),
                tint(R.color.backdrop_end)),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)

        // Broad, low-opacity light pools create color refraction under the frosted cards.
        glow(canvas, w * 0.92f, h * 0.10f - scrollOffset,
            w * 0.84f, tint(R.color.glow_first))
        glow(canvas, -w * 0.20f, h * 0.50f + scrollOffset * 0.45f,
            w * 0.90f, tint(R.color.glow_second))
        glow(canvas, w * 0.92f, h * 0.87f - scrollOffset * 0.4f,
            w * 0.80f, tint(R.color.glow_third))
        glow(canvas, w * 0.21f, h * 1.09f,
            w * 0.75f, tint(R.color.glow_fourth))
        paint.shader = null
    }

    private fun tint(res: Int) = ContextCompat.getColor(context, res)

    private fun glow(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        paint.shader = RadialGradient(
            x, y, radius,
            intArrayOf(color, color and 0x00FFFFFF, android.graphics.Color.TRANSPARENT),
            floatArrayOf(0f, 0.40f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, radius, paint)
    }
}
