package com.example.etfdrawdown.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.etfdrawdown.R
import com.example.etfdrawdown.data.IndexResult
import com.example.etfdrawdown.data.MarketHours
import com.example.etfdrawdown.data.PrefsStore
import com.example.etfdrawdown.data.Snapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 캐시된 스냅샷으로 위젯 RemoteViews를 구성하고, 모든 위젯 인스턴스에 반영한다.
 * (Glance 대신 전통적 RemoteViews — Compose 컴파일러 불필요)
 */
object WidgetRenderer {

    private val timeFmt = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)

    /** 모든 위젯 인스턴스를 현재 캐시로 갱신. */
    fun renderAll(context: Context) {
        val mgr = android.appwidget.AppWidgetManager.getInstance(context)
        val component = ComponentName(context, EtfWidgetProvider::class.java)
        val ids = mgr.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        val views = build(context)
        for (id in ids) mgr.updateAppWidget(id, views)
    }

    /** 캐시를 읽어 RemoteViews 한 벌을 만든다. */
    fun build(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_etf)
        val snapshot = PrefsStore.load(context)

        // 새로고침 버튼 → 브로드캐스트
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshIntent(context))

        if (snapshot == null) {
            views.setTextViewText(R.id.header_status, "불러오는 중…")
            return views
        }

        bindHeader(context, views, snapshot)
        bindIndex(context, views, snapshot.results.getOrNull(0),
            R.id.ndx_name, R.id.ndx_price, R.id.ndx_1m, R.id.ndx_sub)
        bindIndex(context, views, snapshot.results.getOrNull(1),
            R.id.spx_name, R.id.spx_price, R.id.spx_1m, R.id.spx_sub)
        return views
    }

    private fun bindHeader(context: Context, views: RemoteViews, snapshot: Snapshot) {
        val open = MarketHours.isOpen()
        val time = if (snapshot.updatedAtMs > 0) timeFmt.format(Date(snapshot.updatedAtMs)) else "-"
        val market = if (open) "개장중" else "폐장"
        views.setTextViewText(R.id.header_status, "$time 기준 · $market")
    }

    private fun bindIndex(
        context: Context,
        views: RemoteViews,
        index: IndexResult?,
        nameId: Int,
        priceId: Int,
        oneMonthId: Int,
        subId: Int,
    ) {
        if (index == null) return
        views.setTextViewText(nameId, index.name)
        views.setTextViewText(priceId, formatPrice(index.currentPrice))

        val m1 = index.periods["1m"]?.dropRatio ?: 0.0
        views.setTextViewText(oneMonthId, "1M  ${formatDrop(m1)}")
        views.setTextColor(oneMonthId, dropColor(context, m1))

        val m3 = index.periods["3m"]?.dropRatio ?: 0.0
        val y1 = index.periods["1y"]?.dropRatio ?: 0.0
        views.setTextViewText(subId, "3M ${formatDrop(m3)} · 1Y ${formatDrop(y1)}")
    }

    private fun formatPrice(p: Double): String = String.format(Locale.US, "%,.0f", p)

    private fun formatDrop(d: Double): String = String.format(Locale.US, "%.2f%%", d)

    /** 낙폭 크기에 따른 텍스트 색상(정보 강조용, 매수 신호 아님). */
    private fun dropColor(context: Context, d: Double): Int = when {
        d <= -10.0 -> ContextCompat.getColor(context, R.color.accent_red)
        d <= -5.0 -> ContextCompat.getColor(context, R.color.accent_orange)
        else -> ContextCompat.getColor(context, R.color.text_primary)
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
