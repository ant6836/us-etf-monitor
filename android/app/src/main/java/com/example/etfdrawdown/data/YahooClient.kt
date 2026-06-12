package com.example.etfdrawdown.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 야후 파이낸스 차트 API 직접 호출(서버리스 구조).
 *
 * 엔드포인트: GET https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=3mo&interval=1d
 * (1개월 낙폭·차트만 쓰므로 3개월치면 충분 — 여유분 포함)
 * 인증 없이 User-Agent 헤더만으로 동작함을 검증함(2026-06-08).
 */
object YahooClient {

    private const val BASE = "https://query1.finance.yahoo.com/v8/finance/chart/"
    private const val SEARCH = "https://query1.finance.yahoo.com/v1/finance/search"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14)"
    private const val TIMEOUT_MS = 10_000

    /** 위젯에 추가하려면 3개월 응답에 유효한 일봉이 최소 이만큼 있어야 한다(^DJUSDIV 같은 이력 미제공 종목 차단). */
    const val MIN_DAILY_BARS = 20

    /** 일봉 하나(epoch초, 고가, 종가). */
    data class Bar(val ts: Long, val high: Double, val close: Double)

    /** 한 심볼의 원시 시계열. */
    data class Raw(val currentPrice: Double, val series: List<Bar>)

    /**
     * 3개월치 일봉을 받아 현재가와 시계열(고가·종가)을 반환한다.
     * @throws Exception 네트워크/파싱 실패 시(호출부에서 처리)
     */
    fun fetchChart(symbol: String): Raw {
        val encoded = URLEncoder.encode(symbol, "UTF-8") // ^NDX -> %5ENDX
        val url = URL("$BASE$encoded?range=3mo&interval=1d")
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

    /** 검색 결과 한 건. type 예: INDEX, ETF, EQUITY */
    data class SearchItem(val symbol: String, val name: String, val type: String)

    /**
     * 야후 검색 API로 심볼/종목명을 검색한다.
     * @throws Exception 네트워크/파싱 실패 시(호출부에서 처리)
     */
    fun search(query: String): List<SearchItem> {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = URL("$SEARCH?q=$q&quotesCount=10&newsCount=0")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw RuntimeException("야후 검색 응답 코드 $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val quotes = JSONObject(body).optJSONArray("quotes") ?: return emptyList()
            val items = ArrayList<SearchItem>(quotes.length())
            for (i in 0 until quotes.length()) {
                val o = quotes.getJSONObject(i)
                val symbol = o.optString("symbol")
                if (symbol.isEmpty()) continue
                val name = o.optString("longname")
                    .ifEmpty { o.optString("shortname") }
                    .ifEmpty { symbol }
                items.add(SearchItem(symbol, name, o.optString("quoteType")))
            }
            return items
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 위젯에 쓸 수 있는 종목인지 검증: 3개월 일봉 수를 반환한다(실패 시 예외).
     * MIN_DAILY_BARS 미만이면 과거 이력 미제공 종목(예: ^DJUSDIV)이다.
     */
    fun countDailyBars(symbol: String): Int = fetchChart(symbol).series.size

    private fun parse(body: String): Raw {
        val result = JSONObject(body)
            .getJSONObject("chart")
            .getJSONArray("result")
            .getJSONObject(0)

        val meta = result.getJSONObject("meta")
        val current = meta.getDouble("regularMarketPrice")

        val timestamps = result.getJSONArray("timestamp")
        val quote = result.getJSONObject("indicators")
            .getJSONArray("quote")
            .getJSONObject(0)
        val highs = quote.getJSONArray("high")
        val closes = quote.getJSONArray("close")

        val series = ArrayList<Bar>(highs.length())
        val n = minOf(timestamps.length(), highs.length(), closes.length())
        for (i in 0 until n) {
            if (highs.isNull(i) || closes.isNull(i)) continue // 거래 없는 날은 null
            series.add(Bar(timestamps.getLong(i), highs.getDouble(i), closes.getDouble(i)))
        }
        if (series.isEmpty()) throw RuntimeException("시계열 데이터 없음")
        return Raw(current, series)
    }
}
