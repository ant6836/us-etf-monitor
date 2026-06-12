package com.example.etfdrawdown.data

/** 기간 하나에 대한 고점/낙폭 결과. */
data class PeriodResult(
    val periodHigh: Double,
    val dropRatio: Double, // 0 또는 음수(%)
)

/** 지수 하나에 대한 결과(현재가 + 기간별 낙폭 + 1개월 종가 시계열). */
data class IndexResult(
    val symbol: String,        // "^NDX"
    val name: String,          // "Nasdaq 100"
    val currentPrice: Double,
    val periods: Map<String, PeriodResult>, // 현재 "1m"만 사용
    val closes1m: List<Double> = emptyList(), // 큰 위젯 차트용(마지막 점 = 현재가)
)

/** 캐시에서 불러온 스냅샷(결과 + 갱신 시각 + 성공 여부). */
data class Snapshot(
    val results: List<IndexResult>,
    val updatedAtMs: Long,
    val fromCache: Boolean = false, // 이번 갱신 실패로 과거 캐시를 보여주는 경우 true
)
