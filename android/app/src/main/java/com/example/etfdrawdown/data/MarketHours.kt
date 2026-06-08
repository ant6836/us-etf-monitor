package com.example.etfdrawdown.data

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 미국 증시 개장 여부 판정.
 *
 * 시각을 하드코딩하지 않고 America/New_York 기준으로 동적 판정한다.
 * 서머타임(EDT/EST)은 ZoneId가 자동 처리한다. (minSdk 26이라 java.time 네이티브 사용)
 */
object MarketHours {

    private val NY: ZoneId = ZoneId.of("America/New_York")
    private val OPEN: LocalTime = LocalTime.of(9, 30)
    private val CLOSE: LocalTime = LocalTime.of(16, 0)

    // 미국 증시 주요 휴장일(관측일). 필요 시 갱신.
    private val HOLIDAYS: Set<String> = setOf(
        // 2026
        "2026-01-01", "2026-01-19", "2026-02-16", "2026-04-03", "2026-05-25",
        "2026-06-19", "2026-07-03", "2026-09-07", "2026-11-26", "2026-12-25",
    )

    /** 현재 미국 증시 정규장 개장 여부. */
    fun isOpen(now: ZonedDateTime = ZonedDateTime.now(NY)): Boolean {
        val ny = now.withZoneSameInstant(NY)
        if (ny.dayOfWeek == DayOfWeek.SATURDAY || ny.dayOfWeek == DayOfWeek.SUNDAY) return false
        if (ny.toLocalDate().toString() in HOLIDAYS) return false
        val t = ny.toLocalTime()
        return !t.isBefore(OPEN) && !t.isAfter(CLOSE)
    }
}
