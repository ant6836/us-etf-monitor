package com.example.etfdrawdown.data

/**
 * 시세 수집 + 낙폭 계산을 묶는 계층.
 * 야후에서 1년치를 받아 기기 내에서 1M/3M/1Y 구간을 잘라 계산한다.
 */
object Repository {

    /** 추적 대상 지수: (심볼, 표시명). */
    val INDICES: List<Pair<String, String>> = listOf(
        "^NDX" to "Nasdaq 100",
        "^GSPC" to "S&P 500",
    )

    /**
     * 모든 지수의 낙폭을 계산해 반환한다.
     * @throws Exception 한 지수라도 수집 실패 시 전파(호출부에서 캐시 fallback 처리)
     */
    fun load(): List<IndexResult> {
        val nowSec = System.currentTimeMillis() / 1000
        return INDICES.map { (symbol, name) ->
            val raw = YahooClient.fetchChart(symbol)
            val periods = Drawdown.PERIOD_DAYS.mapValues { (_, days) ->
                val cutoff = nowSec - days * 86_400
                val highs = raw.series
                    .filter { it.first >= cutoff }
                    .map { it.second }
                    .toMutableList()
                // 당일 현재가를 고점 후보로 포함(intraday 보정)
                highs.add(raw.currentPrice)
                val peak = highs.max()
                PeriodResult(
                    periodHigh = peak,
                    dropRatio = Drawdown.calc(raw.currentPrice, highs),
                )
            }
            IndexResult(symbol, name, raw.currentPrice, periods)
        }
    }
}
