package com.kng7.sevenpro.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * Lightweight speed chart (up/down) drawn on canvas — no external dependency.
 */
class SpeedChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val samples = 90
    private val up = FloatArray(samples)
    private val down = FloatArray(samples)
    private var head = 0
    private var count = 0

    private val upPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF31C463.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val downPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3B82F6.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun addSample(upBps: Float, downBps: Float) {
        up[head] = upBps
        down[head] = downBps
        head = (head + 1) % samples
        if (count < samples) count++
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (count < 2) return
        val w = width.toFloat()
        val h = height.toFloat() - 8f
        var max = 1f
        for (i in 0 until count) {
            max = maxOf(max, up[i], down[i])
        }

        fun drawSeries(data: FloatArray, line: Paint, fillColor: Int) {
            val path = Path()
            var started = false
            for (i in 0 until count) {
                val idx = (head - count + i + samples * 2) % samples
                val x = w * i / (samples - 1).coerceAtLeast(1)
                val y = h - h * (data[idx] / max)
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
            }
            canvas.drawPath(path, line)
            fillPaint.color = fillColor
            val closed = Path(path)
            closed.lineTo(w * (count - 1) / (samples - 1).coerceAtLeast(1), h)
            closed.lineTo(0f, h)
            closed.close()
            fillPaint.alpha = 38
            canvas.drawPath(closed, fillPaint)
            fillPaint.alpha = 255
        }

        drawSeries(down, downPaint, 0x663B82F6.toInt())
        drawSeries(up, upPaint, 0x6631C463.toInt())
    }

    fun clear() {
        head = 0; count = 0
        invalidate()
    }
}
