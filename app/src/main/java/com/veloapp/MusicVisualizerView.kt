package com.veloapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

class MusicVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barCount = 18
    private val barHeights = FloatArray(barCount) { 0.15f }
    private var isPlaying = false
    private var animTick = 0.0

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isPlaying) {
                animTick += 0.12
                for (i in 0 until barCount) {
                    // Chaque barre suit une onde sinusoïdale décalée = effet "equalizer"
                    val wave = abs(sin(animTick + i * 0.45)) * 0.75f + 0.1f
                    barHeights[i] = barHeights[i] + (wave.toFloat() - barHeights[i]) * 0.35f
                }
            } else {
                animTick = 0.0
                for (i in 0 until barCount) {
                    // Retour au repos en douceur
                    barHeights[i] = barHeights[i] + (0.12f - barHeights[i]) * 0.15f
                }
            }
            invalidate()
            postDelayed(this, 50) // ~20 fps, économe en batterie
        }
    }

    init {
        post(updateRunnable)
    }

    fun setPlaying(playing: Boolean) {
        isPlaying = playing
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val gap = w * 0.04f / barCount
        val barWidth = (w - gap * (barCount - 1)) / barCount

        for (i in 0 until barCount) {
            val barH = (barHeights[i] * h).coerceAtLeast(4f)
            val x = i * (barWidth + gap)
            val top = h - barH

            // Dégradé cyan → bleu pour chaque barre
            val alpha = (100 + barHeights[i] * 155).toInt().coerceIn(0, 255)
            paint.shader = LinearGradient(
                x, top, x, h,
                Color.argb(alpha, 100, 230, 255),
                Color.argb(alpha / 2, 50, 100, 200),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(x, top, x + barWidth, h, 3f, 3f, paint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(updateRunnable)
    }
}
