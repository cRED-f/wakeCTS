package com.example.ctsbutton

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Minimal, translucent water with slow surface movement and one physical entry ripple. */
class OceanDropView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val rear = Paint(Paint.ANTI_ALIAS_FLAG)
    private val water = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.25f)
        color = Color.argb(180, 219, 241, 250)
    }
    private val ripple = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.6f)
        color = Color.WHITE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(245, 244, 249, 255)
        textAlign = Paint.Align.CENTER
        textSize = 11f * resources.displayMetrics.scaledDensity
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val path = Path()
    private val line = Path()
    private var phase = 0f
    private var highlighted = false
    private var impact = -1f
    private var impactX = 0f
    private var waveAnimator: ValueAnimator? = null
    private var impactAnimator: ValueAnimator? = null

    // Keep a fixed hit threshold so highlighting cannot cause drag flicker.
    fun surfaceThresholdPx(): Float = dp(42f)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val height = h.toFloat()
        rear.shader = LinearGradient(0f, 0f, 0f, height,
            intArrayOf(Color.argb(78, 138, 205, 233), Color.argb(128, 41, 116, 167)),
            null, Shader.TileMode.CLAMP)
        water.shader = LinearGradient(0f, 0f, 0f, height,
            intArrayOf(Color.argb(140, 47, 115, 158), Color.argb(225, 14, 47, 82)),
            null, Shader.TileMode.CLAMP)
    }

    fun startWaves() {
        if (waveAnimator != null) return
        waveAnimator = ValueAnimator.ofFloat(0f, (2.0 * PI).toFloat()).apply {
            duration = 5600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopWaves() {
        waveAnimator?.cancel()
        waveAnimator = null
        impactAnimator?.cancel()
        impactAnimator = null
    }

    fun setHighlighted(value: Boolean) {
        if (highlighted != value) {
            highlighted = value
            invalidate()
        }
    }

    fun swallowAt(screenX: Int) {
        val location = IntArray(2)
        getLocationOnScreen(location)
        impactX = (screenX - location[0]).toFloat().coerceIn(0f, width.toFloat())
        impactAnimator?.cancel()
        impactAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 860L
            addUpdateListener {
                impact = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val surface = dp(if (highlighted) 36f else 42f)
        drawSurface(canvas, surface + dp(13f), dp(6f), phase * 0.75f + 1.4f, rear, false)
        drawSurface(canvas, surface, dp(if (highlighted) 5.8f else 4.1f), phase, water, true)
        canvas.drawText(
            context.getString(if (highlighted) R.string.ocean_drop_hint else R.string.ocean_drag_hint),
            width / 2f, height - dp(35f), textPaint
        )
        if (impact in 0f..1f) drawEntryRipple(canvas, surface)
    }

    private fun drawSurface(
        canvas: Canvas, baseline: Float, amplitude: Float,
        shift: Float, paint: Paint, drawRim: Boolean
    ) {
        path.reset()
        line.reset()
        val steps = 80
        for (i in 0..steps) {
            val x = width.toFloat() * i / steps
            val u = i / steps.toFloat()
            val y = baseline + sin(u * (2.0 * PI).toFloat() + shift) * amplitude +
                sin(u * (4.0 * PI).toFloat() - shift * 0.65f) * amplitude * 0.22f + impactDip(x)
            if (i == 0) {
                line.moveTo(x, y)
                path.moveTo(x, y)
            } else {
                line.lineTo(x, y)
                path.lineTo(x, y)
            }
        }
        path.lineTo(width.toFloat(), height.toFloat())
        path.lineTo(0f, height.toFloat())
        path.close()
        canvas.drawPath(path, paint)
        if (drawRim) canvas.drawPath(line, rim)
    }

    private fun impactDip(x: Float): Float {
        if (impact !in 0f..1f) return 0f
        val spread = ((x - impactX) / dp(66f)).toDouble()
        val pulse = sin(PI * impact.toDouble()).coerceAtLeast(0.0)
        return (dp(16f) * pulse * exp(-spread * spread)).toFloat()
    }

    private fun drawEntryRipple(canvas: Canvas, surface: Float) {
        val progress = impact
        val eased = 1f - (1f - progress) * (1f - progress)
        val opacity = ((1f - progress) * 205f).toInt().coerceIn(0, 255)
        ripple.color = Color.argb(opacity, 224, 245, 252)
        ripple.strokeWidth = dp(1.7f - progress * 0.6f)
        val radius = dp(8f + 62f * eased)
        val y = surface + impactDip(impactX)
        canvas.drawOval(impactX - radius, y - radius * 0.12f,
            impactX + radius, y + radius * 0.12f, ripple)
        if (progress < 0.60f) {
            ripple.color = Color.argb((opacity * 0.50f).toInt(), 225, 246, 252)
            val inner = dp(5f + 24f * eased)
            canvas.drawOval(impactX - inner, y - inner * 0.16f,
                impactX + inner, y + inner * 0.16f, ripple)
        }
        // Four small drops rather than a particle shower.
        if (progress < 0.70f) {
            val drops = floatArrayOf(-0.82f, -0.28f, 0.35f, 0.88f)
            ripple.style = Paint.Style.FILL
            for ((index, offset) in drops.withIndex()) {
                val travel = sin((progress / 0.70f * PI).coerceIn(0.0, PI)).toFloat()
                val px = impactX + offset * dp(39f) * eased
                val py = surface - dp(10f + (14f + index % 2 * 7f) * travel)
                canvas.drawCircle(px, py, dp(1.4f + index % 2), ripple)
            }
            ripple.style = Paint.Style.STROKE
        }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    override fun onDetachedFromWindow() {
        stopWaves()
        super.onDetachedFromWindow()
    }
}
