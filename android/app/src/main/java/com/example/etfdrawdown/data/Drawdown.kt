package com.example.etfdrawdown.data

import kotlin.math.roundToInt

/**
 * 고점 대비 낙폭(Drawdown from Period High) 계산.
 *
 * 백엔드(파이썬 `calc_drawdown`)와 동일한 로직/경계값을 따른다.
 * 현재가는 항상 고점 이하이므로 결과는 0.0(고점 갱신 중) 또는 음수(하락)다.
 */
object Drawdown {

    /** 기준 기간별 일수(달력 기준). 현재는 1개월만 사용. */
    val PERIOD_DAYS: Map<String, Long> = linkedMapOf(
        "1m" to 30L,
    )

    /**
     * @param current 현재가
     * @param highs 기간 내 고가 리스트(당일 현재가를 후보로 포함시킬 수 있음)
     * @return 소수점 둘째 자리로 반올림한 낙폭(%). 데이터 없거나 비정상이면 0.0
     */
    fun calc(current: Double, highs: List<Double>): Double {
        if (highs.isEmpty()) return 0.0
        val peak = highs.max()
        if (peak <= 0.0) return 0.0
        val ratio = (current - peak) / peak * 100.0
        // 둘째 자리 반올림
        return (ratio * 100.0).roundToInt() / 100.0
    }
}
