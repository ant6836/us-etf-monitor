package com.example.etfdrawdown.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.ColorUtils

/**
 * 1개월 종가 라인 차트를 비트맵으로 그린다.
 * RemoteViews는 커스텀 뷰를 못 쓰므로 Canvas → Bitmap → ImageView 방식 사용.
 */
object ChartRenderer {

    /**
     * @param values 종가 시계열(시간 오름차순, 마지막 점 = 현재가)
     * @param periodHigh 기간 고점(점선 기준선으로 표시)
     * @param highLabel 고점 가격 라벨(예: "30,664"). null이면 표시 안 함(차트가 낮을 때)
     */
    fun render(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        values: List<Double>,
        periodHigh: Double,
        lineColor: Int,
        highLineColor: Int,
        highLabel: String? = null,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (values.size < 2) return bmp // 점이 부족하면 빈(투명) 차트

        val canvas = Canvas(bmp)
        val pad = 4f * density
        val labelSize = 9f * density
        // 라벨이 있으면 고점선 위에 가격이 들어갈 상단 여백 확보
        val padTop = if (highLabel != null) labelSize + 6f * density else pad
        val plotW = w - pad * 2
        val plotH = h - padTop - pad

        // 고점 기준선이 항상 보이도록 범위에 포함 + 위아래 5% 여유
        var max = maxOf(values.max(), periodHigh)
        var min = values.min()
        val span = (max - min).takeIf { it > 0.0 } ?: (max * 0.01).coerceAtLeast(1.0)
        max += span * 0.05
        min -= span * 0.05

        fun x(i: Int): Float = pad + plotW * i / (values.size - 1).toFloat()
        fun y(v: Double): Float = padTop + plotH * (1f - ((v - min) / (max - min)).toFloat())

        // 종가 라인 경로
        val path = Path()
        values.forEachIndexed { i, v ->
            if (i == 0) path.moveTo(x(0), y(v)) else path.lineTo(x(i), y(v))
        }

        // 라인 아래 영역 채움(라인보다 먼저 그려서 라인이 위에 보이게)
        // 투명도 가로 그라데이션: 과거(왼쪽) 거의 투명 → 현재(오른쪽) 진하게
        val bottom = padTop + plotH
        val fillPath = Path(path).apply {
            lineTo(x(values.size - 1), bottom)
            lineTo(x(0), bottom)
            close()
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                pad, 0f, pad + plotW, 0f,
                ColorUtils.setAlphaComponent(lineColor, 20), // 약 8%
                ColorUtils.setAlphaComponent(lineColor, 205), // 약 80%
                Shader.TileMode.CLAMP,
            )
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

        // 고점 가격 라벨(점선 위 우측 정렬)
        if (highLabel != null) {
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = highLineColor
                textSize = labelSize
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(highLabel, pad + plotW, y(periodHigh) - 3f * density, labelPaint)
        }
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
