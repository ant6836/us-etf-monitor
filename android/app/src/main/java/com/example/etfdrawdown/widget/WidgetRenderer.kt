package com.example.etfdrawdown.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.etfdrawdown.R
import com.example.etfdrawdown.data.IndexResult
import com.example.etfdrawdown.data.MarketHours
import com.example.etfdrawdown.data.PrefsStore
import com.example.etfdrawdown.data.Snapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 캐시된 스냅샷으로 위젯 RemoteViews를 구성하고 위젯 인스턴스별로 반영한다.
 * 위젯 높이가 LARGE_MIN_HEIGHT_DP 이상이면 1개월 종가 차트가 있는 큰 레이아웃을 쓴다.
 * (Glance 대신 전통적 RemoteViews — Compose 컴파일러 불필요)
 */
object WidgetRenderer {

    /** 개장 중인데 이 시간 이상 갱신이 없으면 헤더에 '지연' 경고를 띄운다. */
    private const val STALE_THRESHOLD_MS = 2 * 60 * 60 * 1000L

    /** 위젯 높이(dp)가 이 값 이상이면 차트가 있는 큰 레이아웃 사용(약 3셀부터). */
    private const val LARGE_MIN_HEIGHT_DP = 180

    /** 큰 레이아웃에서 텍스트 열·패딩이 차지하는 가로폭(dp) 근사치 — 차트 비트맵 크기 계산용. */
    private const val CHART_TEXT_COLUMN_DP = 150

    /**
     * 모든 위젯 인스턴스를 현재 캐시로 갱신. 인스턴스별 크기에 맞는 레이아웃을 고른다.
     * @param statusOverride 지정 시 헤더 상태를 이 문구로 강제(예: "갱신 중…")
     */
    fun renderAll(context: Context, statusOverride: String? = null) {
        val mgr = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, EtfWidgetProvider::class.java)
        for (id in mgr.getAppWidgetIds(component)) {
            mgr.updateAppWidget(id, buildFor(context, mgr, id, statusOverride))
        }
    }

    /** 위젯 하나만 갱신(크기 변경 콜백에서 사용). */
    fun renderOne(context: Context, appWidgetId: Int) {
        val mgr = AppWidgetManager.getInstance(context)
        mgr.updateAppWidget(appWidgetId, buildFor(context, mgr, appWidgetId, null))
    }

    /** 인스턴스의 현재 크기 옵션을 읽어 알맞은 레이아웃으로 빌드한다. */
    private fun buildFor(
        context: Context,
        mgr: AppWidgetManager,
        appWidgetId: Int,
        statusOverride: String?,
    ): RemoteViews {
        val opts = mgr.getAppWidgetOptions(appWidgetId)
        // 세로 모드 홈 화면 기준: 가로는 MIN_WIDTH, 세로는 MAX_HEIGHT
        val widthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            .takeIf { it > 0 } ?: 320
        val heightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            .takeIf { it > 0 } ?: 110
        val large = heightDp >= LARGE_MIN_HEIGHT_DP
        return build(context, statusOverride, large, widthDp, heightDp)
    }

    /** 캐시를 읽어 RemoteViews 한 벌을 만든다. */
    fun build(
        context: Context,
        statusOverride: String? = null,
        large: Boolean = false,
        widthDp: Int = 320,
        heightDp: Int = 110,
    ): RemoteViews {
        val layout = if (large) R.layout.widget_etf_large else R.layout.widget_etf
        val views = RemoteViews(context.packageName, layout)
        val snapshot = PrefsStore.load(context)

        // 새로고침 버튼 → 브로드캐스트
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshIntent(context))

        if (snapshot == null) {
            // 캐시 없음: 첫 로드 실패와 단순 로딩을 구분해 표시
            val failed = PrefsStore.lastFailed(context)
            val status = statusOverride ?: if (failed) "갱신 실패 · 재시도 중" else "불러오는 중…"
            views.setTextViewText(R.id.header_status, status)
            val color = if (failed && statusOverride == null) {
                ContextCompat.getColor(context, R.color.accent_orange)
            } else {
                ContextCompat.getColor(context, R.color.text_muted)
            }
            views.setTextColor(R.id.header_status, color)
            return views
        }

        bindHeader(context, views, snapshot, statusOverride)
        bindIndex(context, views, snapshot.results.getOrNull(0),
            R.id.ndx_name, R.id.ndx_price, R.id.ndx_1m)
        bindIndex(context, views, snapshot.results.getOrNull(1),
            R.id.spx_name, R.id.spx_price, R.id.spx_1m)

        if (large) {
            bindChart(context, views, snapshot.results.getOrNull(0), R.id.ndx_chart, widthDp, heightDp)
            bindChart(context, views, snapshot.results.getOrNull(1), R.id.spx_chart, widthDp, heightDp)
        }
        return views
    }

    /** 1개월 종가 차트를 비트맵으로 그려 ImageView에 꽂는다. */
    private fun bindChart(
        context: Context,
        views: RemoteViews,
        index: IndexResult?,
        chartId: Int,
        widthDp: Int,
        heightDp: Int,
    ) {
        if (index == null || index.closes1m.size < 2) return
        val density = context.resources.displayMetrics.density
        // 차트 영역 근사: 가로 = 전체 - 텍스트 열, 세로 = (전체 - 헤더/여백) / 지수 2개
        val chartWDp = (widthDp - CHART_TEXT_COLUMN_DP).coerceAtLeast(80)
        val chartHDp = ((heightDp - 60) / 2).coerceAtLeast(50)
        val wPx = (chartWDp * density).toInt().coerceAtMost(1200)
        val hPx = (chartHDp * density).toInt().coerceAtMost(600)
        // 낙폭 구간 색을 텍스트와 동일하게 라인·면에 적용
        val lineColor = dropColor(context, index.periods["1m"]?.dropRatio ?: 0.0)
        val bg = ContextCompat.getColor(context, R.color.widget_bg)
        val fillColor = ColorUtils.blendARGB(bg, lineColor, 0.35f) // 불투명(배경과 혼합한 옅은 톤)
        val bmp = ChartRenderer.render(
            widthPx = wPx,
            heightPx = hPx,
            density = density,
            values = index.closes1m,
            periodHigh = index.periods["1m"]?.periodHigh ?: index.closes1m.max(),
            lineColor = lineColor,
            fillColor = fillColor,
            highLineColor = ContextCompat.getColor(context, R.color.text_muted),
        )
        views.setImageViewBitmap(chartId, bmp)
    }

    private fun bindHeader(
        context: Context,
        views: RemoteViews,
        snapshot: Snapshot,
        statusOverride: String?,
    ) {
        val muted = ContextCompat.getColor(context, R.color.text_muted)
        if (statusOverride != null) {
            views.setTextViewText(R.id.header_status, statusOverride)
            views.setTextColor(R.id.header_status, muted)
            return
        }

        val open = MarketHours.isOpen()
        val time = if (snapshot.updatedAtMs > 0) {
            SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(snapshot.updatedAtMs))
        } else "-"
        val market = if (open) "개장중" else "폐장"
        val failed = PrefsStore.lastFailed(context)
        val stale = open && snapshot.updatedAtMs > 0 &&
            System.currentTimeMillis() - snapshot.updatedAtMs > STALE_THRESHOLD_MS

        val status = buildString {
            append(time).append(" 기준 · ").append(market)
            when {
                failed -> append(" · 갱신 실패")
                stale -> append(" · 지연")
            }
        }
        views.setTextViewText(R.id.header_status, status)
        val color = if (failed || stale) {
            ContextCompat.getColor(context, R.color.accent_orange)
        } else muted
        views.setTextColor(R.id.header_status, color)
    }

    private fun bindIndex(
        context: Context,
        views: RemoteViews,
        index: IndexResult?,
        nameId: Int,
        priceId: Int,
        oneMonthId: Int,
    ) {
        if (index == null) return
        views.setTextViewText(nameId, index.name)
        views.setTextViewText(priceId, formatPrice(index.currentPrice))

        // 기간(1개월)은 위젯 제목에 표시되므로 숫자만 크게
        val m1 = index.periods["1m"]?.dropRatio ?: 0.0
        views.setTextViewText(oneMonthId, formatDrop(m1))
        views.setTextColor(oneMonthId, dropColor(context, m1))
    }

    private fun formatPrice(p: Double): String = String.format(Locale.US, "%,.0f", p)

    private fun formatDrop(d: Double): String = String.format(Locale.US, "%.2f%%", d)

    /**
     * 낙폭 구간 색상(정보 강조용, 매수 신호 아님). 텍스트·차트 라인·면에 공통 적용.
     * 낙폭은 항상 0% 이하이므로 0% = 고점 갱신 중(파랑).
     */
    private fun dropColor(context: Context, d: Double): Int = when {
        d <= -10.0 -> ContextCompat.getColor(context, R.color.accent_red)
        d <= -5.0 -> ContextCompat.getColor(context, R.color.accent_orange)
        d < 0.0 -> ContextCompat.getColor(context, R.color.drop_yellow)
        else -> ContextCompat.getColor(context, R.color.chart_line)
    }

    private fun refreshIntent(context: Context): PendingIntent {
        val intent = Intent(context, EtfWidgetProvider::class.java).apply {
            action = EtfWidgetProvider.ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
