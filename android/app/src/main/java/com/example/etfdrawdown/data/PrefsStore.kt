package com.example.etfdrawdown.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 마지막으로 성공한 결과를 SharedPreferences에 JSON으로 캐싱한다.
 * (DataStore 대신 SharedPreferences 사용 — 의존성 없이 위젯/워커에서 동기 접근 용이)
 */
object PrefsStore {

    private const val PREF = "etf_widget"
    private const val KEY_DATA = "data_json"
    private const val KEY_UPDATED = "updated_at_ms"
    private const val KEY_FAILED = "last_update_failed"

    fun save(context: Context, results: List<IndexResult>, updatedAtMs: Long) {
        val arr = JSONArray()
        for (r in results) {
            val periods = JSONObject()
            for ((p, pr) in r.periods) {
                periods.put(
                    p,
                    JSONObject()
                        .put("high", pr.periodHigh)
                        .put("drop", pr.dropRatio),
                )
            }
            val closes = JSONArray()
            for (c in r.closes1m) closes.put(c)
            arr.put(
                JSONObject()
                    .put("symbol", r.symbol)
                    .put("name", r.name)
                    .put("price", r.currentPrice)
                    .put("periods", periods)
                    .put("closes1m", closes),
            )
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DATA, arr.toString())
            .putLong(KEY_UPDATED, updatedAtMs)
            .putBoolean(KEY_FAILED, false) // 저장 성공 = 직전 실패 상태 해제
            .apply()
    }

    /** 이번 갱신 시도가 실패했음을 기록한다(위젯에 실패 상태 표시용). */
    fun markFailure(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FAILED, true)
            .apply()
    }

    /** 마지막 갱신 시도가 실패했는지 여부. */
    fun lastFailed(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_FAILED, false)

    /** 캐시된 스냅샷을 반환한다. 없으면 null. */
    fun load(context: Context): Snapshot? {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_DATA, null) ?: return null
        val updated = prefs.getLong(KEY_UPDATED, 0L)
        return try {
            val arr = JSONArray(json)
            val results = ArrayList<IndexResult>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val periodsObj = o.getJSONObject("periods")
                val periods = LinkedHashMap<String, PeriodResult>()
                for (p in Drawdown.PERIOD_DAYS.keys) {
                    if (periodsObj.has(p)) {
                        val po = periodsObj.getJSONObject(p)
                        periods[p] = PeriodResult(po.getDouble("high"), po.getDouble("drop"))
                    }
                }
                // 구버전 캐시에는 closes1m이 없을 수 있음 → 빈 리스트(차트만 비워짐)
                val closesArr = o.optJSONArray("closes1m")
                val closes1m = if (closesArr != null) {
                    List(closesArr.length()) { closesArr.getDouble(it) }
                } else emptyList()
                results.add(
                    IndexResult(
                        symbol = o.getString("symbol"),
                        name = o.getString("name"),
                        currentPrice = o.getDouble("price"),
                        periods = periods,
                        closes1m = closes1m,
                    ),
                )
            }
            Snapshot(results, updated, fromCache = true)
        } catch (e: Exception) {
            null
        }
    }
}
