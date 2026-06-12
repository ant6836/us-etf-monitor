package com.example.etfdrawdown.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path

/**
 * 1개월 종가 라인 차트를 비트맵으로 그린다.
 * RemoteViews는 커스텀 뷰를 못 쓰므로 Canvas → Bitmap → ImageView 방식 사용.
 */
object ChartRenderer {

    /**
     * @param values 종가 시계열(시간 오름차순, 마지막 점 = 현재가)
     * @param periodHigh 기간 고점(점선 기준선으로 표시)
     * @param fillColor 라인 아래 영역 채움색(불투명 — 호출부에서 배경과 혼합해 전달)
     */
    fun render(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        values: List<Double>,
        periodHigh: Double,
        lineColor: Int,
        fillColor: Int,
        highLineColor: Int,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (values.size < 2) return bmp // 점이 부족하면 빈(투명) 차트

        val canvas = Canvas(bmp)
        val pad = 4f * density
        val plotW = w - pad * 2
        val plotH = h - pad * 2

        // 고점 기준선이 항상 보이도록 범위에 포함 + 위아래 5% 여유
        var max = maxOf(values.max(), periodHigh)
        var min = values.min()
        val span = (max - min).takeIf { it > 0.0 } ?: (max * 0.01).coerceAtLeast(1.0)
        max += span * 0.05
        min -= span * 0.05

        fun x(i: Int): Float = pad + plotW * i / (values.size - 1).toFloat()
        fun y(v: Double): Float = pad + plotH * (1f - ((v - min) / (max - min)).toFloat())

        // 종가 라인 경로
        val path = Path()
        values.forEachIndexed { i, v ->
            if (i == 0) path.moveTo(x(0), y(v)) else path.lineTo(x(i), y(v))
        }

        // 라인 아래 영역 채움(라인보다 먼저 그려서 라인이 위에 보이게)
        val bottom = pad + plotH
        val fillPath = Path(path).apply {
            lineTo(x(values.size - 1), bottom)
            lineTo(x(0), bottom)
            close()
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }
        canvas.drawPath(fillPath, fillPaint)

        // 기간 고점 점선(채움 위에 보이도록 라인보다 먼저, 채움 다음에)
        val highPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = highLineColor
            style = Paint.Style.STROKE
            strokeWidth = density
            pathEffect = DashPathEffect(floatArrayOf(4f * density, 4f * density), 0f)
        }
        canvas.drawLine(pad, y(periodHigh), pad + plotW, y(periodHigh), highPaint)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * density
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(path, linePaint)

        // 현재가(마지막 점) 강조
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(x(values.size - 1), y(values.last()), 2.5f * density, dotPaint)

        return bmp
    }
}
