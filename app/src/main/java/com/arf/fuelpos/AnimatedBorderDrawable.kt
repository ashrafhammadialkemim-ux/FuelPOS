package com.arf.fuelpos

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

/**
 * Rounded "outlined button" background whose border is a red / orange / maroon
 * gradient that rotates smoothly (one turn every 3.5 s).
 */
class AnimatedBorderDrawable(
    private val strokePx: Float,
    private val radiusPx: Float
) : Drawable() {

    private val colors = intArrayOf(
        Color.parseColor("#DC2626"), // red
        Color.parseColor("#F97316"), // orange
        Color.parseColor("#7F1D1D"), // maroon
        Color.parseColor("#F43F5E"), // rose
        Color.parseColor("#DC2626")
    )
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF7F7") }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
    }
    private val rect = RectF()
    private val matrix = Matrix()
    private var angle = 0f
    private var shader: SweepGradient? = null

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 3500
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            angle = it.animatedValue as Float
            invalidateSelf()
        }
    }

    fun start() { if (!animator.isStarted) animator.start() }
    fun stop() { animator.cancel() }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        val half = strokePx / 2f
        rect.set(bounds.left + half, bounds.top + half, bounds.right - half, bounds.bottom - half)
        shader = SweepGradient(rect.centerX(), rect.centerY(), colors, null)
        stroke.shader = shader
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRoundRect(rect, radiusPx, radiusPx, fill)
        shader?.let {
            matrix.setRotate(angle, rect.centerX(), rect.centerY())
            it.setLocalMatrix(matrix)
        }
        canvas.drawRoundRect(rect, radiusPx, radiusPx, stroke)
    }

    override fun setAlpha(alpha: Int) { stroke.alpha = alpha; fill.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { stroke.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
