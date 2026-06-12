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
        // 재부팅·강제 종료 후에도 주기 갱신을 재보장(KEEP 정책이라 중복 호출에 안전)
        UpdateWorker.enqueuePeriodic(context)
        // 우선 캐시로 즉시 렌더(빈 화면 방지)
        WidgetRenderer.renderAll(context)
        // 최신값 비동기 요청
        UpdateWorker.enqueueOnce(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?,
    ) {
        // 사용자가 위젯 크기를 조절하면 새 크기에 맞는 레이아웃(텍스트/차트)으로 다시 렌더
        WidgetRenderer.renderOne(context, appWidgetId)
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
            // 버튼이 눌렸음을 즉시 보여주고(갱신 중…), 완료되면 워커가 다시 렌더링한다
            WidgetRenderer.renderAll(context, "갱신 중…")
            UpdateWorker.enqueueOnce(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.etfdrawdown.ACTION_REFRESH"
    }
}
