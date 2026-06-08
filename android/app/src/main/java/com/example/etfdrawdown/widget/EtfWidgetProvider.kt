package com.example.etfdrawdown.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.example.etfdrawdown.work.UpdateWorker

/**
 * 홈 화면 위젯 Provider.
 * - onUpdate: 캐시 즉시 표시 + 최신 데이터 1회 요청
 * - onEnabled: 30분 주기 갱신 시작
 * - onDisabled: 주기 갱신 중지
 * - ACTION_REFRESH: 새로고침 버튼 처리
 */
class EtfWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // 우선 캐시로 즉시 렌더(빈 화면 방지)
        WidgetRenderer.renderAll(context)
        // 최신값 비동기 요청
        UpdateWorker.enqueueOnce(context)
    }

    override fun onEnabled(context: Context) {
        UpdateWorker.enqueuePeriodic(context)
        UpdateWorker.enqueueOnce(context)
    }

    override fun onDisabled(context: Context) {
        UpdateWorker.cancelPeriodic(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            WidgetRenderer.renderAll(context)
            UpdateWorker.enqueueOnce(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.etfdrawdown.ACTION_REFRESH"
    }
}
