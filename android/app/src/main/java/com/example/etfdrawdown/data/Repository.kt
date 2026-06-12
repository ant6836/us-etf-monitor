package com.example.etfdrawdown.data

/**
 * 시세 수집 + 낙폭 계산을 묶는 계층.
 * 야후에서 3개월치를 받아 기기 내에서 1개월 구간을 잘라 계산한다.
 */
object Repository {

    /** 기본 추적 종목: (심볼, 표시명). 사용자가 목록을 바꾸지 않았을 때 사용. */
    val DEFAULT_INDICES: List<Pair<String, String>> = listOf(
        "^NDX" to "Nasdaq 100",
        "^GSPC" to "S&P 500",
        // ^DJUSDIV(DJ 배당 100)는 야후가 과거 일봉을 제공하지 않아 제외(2026-06-12 확인)
    )

    /** 수집 결과: 성공한 지수 목록 + 실패한 심볼 목록. */
    data class LoadOutcome(
        val results: List<IndexResult>,
        val failedSymbols: List<String>,
    )

    /**
     * 주어진 추적 목록의 모든 종목 낙폭을 계산해 반환한다.
     * 종목별로 독립 처리하므로 하나가 실패해도 나머지는 살린다(예외를 던지지 않음).
     */
    fun load(indices: List<Pair<String, String>>): LoadOutcome {
        val nowSec = System.currentTimeMillis() / 1000
        val results = ArrayList<IndexResult>(indices.size)
        val failed = ArrayList<String>()
        for ((symbol, name) in indices) {
            try {
                val raw = YahooClient.fetchChart(symbol)
                val periods = Drawdown.PERIOD_DAYS.mapValues { (_, days) ->
                    val cutoff = nowSec - days * 86_400
                    val highs = raw.series
                        .filter { it.ts >= cutoff }
                        .map { it.high }
                        .toMutableList()
                    // 당일 현재가를 고점 후보로 포함(intraday 보정)
                    highs.add(raw.currentPrice)
                    val peak = highs.max()
                    PeriodResult(
                        periodHigh = peak,
                        dropRatio = Drawdown.calc(raw.currentPrice, highs),
                    )
                }
                // 큰 위젯 차트용 1개월 종가 시계열(마지막 점은 현재가)
                val cutoff1m = nowSec - Drawdown.PERIOD_DAYS.getValue("1m") * 86_400
                val closes1m = raw.series
                    .filter { it.ts >= cutoff1m }
                    .map { it.close } + raw.currentPrice
                results.add(IndexResult(symbol, name, raw.currentPrice, periods, closes1m))
            } catch (e: Exception) {
                failed.add(symbol)
            }
        }
        return LoadOutcome(results, failed)
    }
}
