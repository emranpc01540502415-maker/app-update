package com.voicealert.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

class QiblaView(c: Context) : View(c) {
    var qibla = 0f
    var azimuth = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(w: Int, h: Int) {
        val s = MeasureSpec.getSize(w)
        setMeasuredDimension(s, min(s, (240 * resources.displayMetrics.density).toInt()))
    }

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = min(cx, cy) - 16f
        p.style = Paint.Style.STROKE; p.strokeWidth = 5f; p.color = 0xFF3A4A6B.toInt()
        cv.drawCircle(cx, cy, r, p)

        cv.save(); cv.rotate(-azimuth, cx, cy)
        p.style = Paint.Style.FILL; p.color = 0xFFFF5C93.toInt()
        cv.drawCircle(cx, cy - r, 10f, p)
        p.color = 0xFFFFFFFF.toInt(); p.textSize = 34f; p.textAlign = Paint.Align.CENTER
        cv.drawText("N", cx, cy - r + 44f, p)
        cv.restore()

        cv.save(); cv.rotate(qibla - azimuth, cx, cy)
        val path = Path().apply {
            moveTo(cx, cy - r + 24f); lineTo(cx - 22f, cy + 10f); lineTo(cx, cy - 6f); lineTo(cx + 22f, cy + 10f); close()
        }
        p.color = 0xFF2ECC71.toInt(); cv.drawPath(path, p)
        cv.restore()
        p.color = 0xFFFFFFFF.toInt(); cv.drawCircle(cx, cy, 6f, p)
    }
}
