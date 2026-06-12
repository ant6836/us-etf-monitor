package com.example.etfdrawdown.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.etfdrawdown.data.MarketHours
import com.example.etfdrawdown.data.PrefsStore
import com.example.etfdrawdown.data.Repository
import com.example.etfdrawdown.widget.WidgetRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 야후에서 시세를 받아 낙폭을 계산하고 캐시에 저장한 뒤 위젯을 갱신하는 워커.
 * - 폐장 중이고 캐시가 마지막 폐장 이후 데이터면 네트워크 호출을 생략한다(배터리/API 절약).
 * - 지수별 부분 실패 시 실패한 지수는 기존 캐시 값으로 유지하고 재시도한다.
 */
class UpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val force = inputData.getBoolean(KEY_FORCE, false)
        val cached = PrefsStore.load(ctx)

        // 폐장 중 + 마지막 성공 갱신이 직전 폐장 이후 → 시세가 변하지 않으므로 호출 생략.
        // 수동 새로고침(force=true)은 항상 실제 갱신을 시도한다.
        if (!force && cached != null && !MarketHours.isOpen() &&
            cached.updatedAtMs >= MarketHours.lastCloseEpochMs()
        ) {
            WidgetRenderer.renderAll(ctx)
            return Result.success()
        }

        val watchlist = PrefsStore.loadWatchlist(ctx)
        val outcome = withContext(Dispatchers.IO) { Repository.load(watchlist) }

        if (outcome.results.isEmpty()) {
            // 전체 실패: 기존 캐시를 그대로 표시하고 실패 상태 기록 후 재시도
            PrefsStore.markFailure(ctx)
            WidgetRenderer.renderAll(ctx)
            return Result.retry()
        }

        // 부분 실패한 심볼은 기존 캐시 값으로 채워 추적 목록 순서대로 병합
        val fresh = outcome.results.associateBy { it.symbol }
        val old = cached?.results?.associateBy { it.symbol } ?: emptyMap()
        val merged = watchlist.mapNotNull { (symbol, _) ->
            fresh[symbol] ?: old[symbol]
        }
        PrefsStore.save(ctx, merged, System.currentTimeMillis())

        return if (outcome.failedSymbols.isEmpty()) {
            WidgetRenderer.renderAll(ctx)
            Result.success()
        } else {
            PrefsStore.markFailure(ctx) // save()가 플래그를 지우므로 그 뒤에 기록
            WidgetRenderer.renderAll(ctx)
            Result.retry()
        }
    }

    companion object {
        /** MainActivity에서 갱신 완료를 관찰할 때 쓰는 고유 작업 이름. */
        const val ONE_TIME = "etf_update_once"
        private const val PERIODIC = "etf_update_periodic"
        private const val KEY_FORCE = "force"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** 즉시 1회 갱신(위젯 추가/수동 새로고침 시). 폐장 스킵 없이 항상 실제 갱신. */
        fun enqueueOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<UpdateWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_FORCE to true))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.REPLACE, request)
        }

        /** 30분 주기 갱신(위젯이 하나라도 배치되어 있을 때). KEEP이라 중복 호출에 안전. */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(
                30, java.util.concurrent.TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** 위젯이 모두 제거되면 주기 갱신 중지. */
        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
