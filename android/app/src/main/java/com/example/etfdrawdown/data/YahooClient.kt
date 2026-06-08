package com.example.etfdrawdown.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 야후 파이낸스 차트 API 직접 호출(서버리스 구조).
 *
 * 엔드포인트: GET https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=1y&interval=1d
 * 인증 없이 User-Agent 헤더만으로 동작함을 검증함(2026-06-08).
 */
object YahooClient {

    private const val BASE = "https://query1.finance.yahoo.com/v8/finance/chart/"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14)"
    private const val TIMEOUT_MS = 10_000

    /** 한 심볼의 원시 시계열. series = (epoch초, 일별 고가) 목록. */
    data class Raw(val currentPrice: Double, val series: List<Pair<Long, Double>>)

    /**
     * 1년치 일봉을 받아 현재가와 (타임스탬프, 고가) 시계열을 반환한다.
     * @throws Exception 네트워크/파싱 실패 시(호출부에서 처리)
     */
    fun fetchChart(symbol: String): Raw {
        val encoded = URLEncoder.encode(symbol, "UTF-8") // ^NDX -> %5ENDX
        val url = URL("$BASE$encoded?range=1y&interval=1d")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw RuntimeException("야후 응답 코드 $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parse(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String): Raw {
        val result = JSONObject(body)
            .getJSONObject("chart")
            .getJSONArray("result")
            .getJSONObject(0)

        val meta = result.getJSONObject("meta")
        val current = meta.getDouble("regularMarketPrice")

        val timestamps = result.getJSONArray("timestamp")
        val highs = result.getJSONObject("indicators")
            .getJSONArray("quote")
            .getJSONObject(0)
            .getJSONArray("high")

        val series = ArrayList<Pair<Long, Double>>(highs.length())
        val n = minOf(timestamps.length(), highs.length())
        for (i in 0 until n) {
            if (highs.isNull(i)) continue // 거래 없는 날은 null
            series.add(timestamps.getLong(i) to highs.getDouble(i))
        }
        if (series.isEmpty()) throw RuntimeException("시계열 데이터 없음")
        return Raw(current, series)
    }
}
