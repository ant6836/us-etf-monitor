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
import com.example.etfdrawdown.data.PrefsStore
import com.example.etfdrawdown.data.Repository
import com.example.etfdrawdown.widget.WidgetRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 야후에서 시세를 받아 낙폭을 계산하고 캐시에 저장한 뒤 위젯을 갱신하는 워커.
 * 실패 시 기존 캐시를 그대로 렌더링하고 재시도한다.
 */
class UpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val results = withContext(Dispatchers.IO) { Repository.load() }
            PrefsStore.save(applicationContext, results, System.currentTimeMillis())
            WidgetRenderer.renderAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            // 실패: 마지막 성공 캐시를 그대로 표시하고 재시도
            WidgetRenderer.renderAll(applicationContext)
            Result.retry()
        }
    }

    companion object {
        private const val ONE_TIME = "etf_update_once"
        private const val PERIODIC = "etf_update_periodic"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** 즉시 1회 갱신(위젯 추가/수동 새로고침 시). */
        fun enqueueOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<UpdateWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.REPLACE, request)
        }

        /** 30분 주기 갱신(위젯이 하나라도 배치되어 있을 때). */
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
