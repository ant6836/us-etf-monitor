package com.example.etfdrawdown.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.ConcurrentHashMap

/**
 * 미국 증시(NYSE/나스닥) 개장 여부 판정.
 *
 * 휴장일을 연도별 하드코딩 대신 규칙으로 계산하므로 매년 갱신할 필요가 없다.
 * 조기 폐장일(13:00 마감)도 반영한다.
 * 서머타임(EDT/EST)은 ZoneId가 자동 처리한다. (minSdk 26이라 java.time 네이티브 사용)
 */
object MarketHours {

    private val NY: ZoneId = ZoneId.of("America/New_York")
    private val OPEN: LocalTime = LocalTime.of(9, 30)
    private val CLOSE: LocalTime = LocalTime.of(16, 0)
    private val EARLY_CLOSE: LocalTime = LocalTime.of(13, 0)

    // 연도별 휴장일 캐시(여러 스레드에서 접근 가능)
    private val holidayCache = ConcurrentHashMap<Int, Set<LocalDate>>()

    /** 현재 미국 증시 정규장 개장 여부. */
    fun isOpen(now: ZonedDateTime = ZonedDateTime.now(NY)): Boolean {
        val ny = now.withZoneSameInstant(NY)
        val date = ny.toLocalDate()
        if (!isTradingDay(date)) return false
        val t = ny.toLocalTime()
        return !t.isBefore(OPEN) && !t.isAfter(closeTime(date))
    }

    /**
     * 가장 최근 폐장 시각(epoch ms).
     * 폐장 중일 때 캐시가 이미 최신인지(=마지막 폐장 이후 갱신됐는지) 판단하는 데 쓴다.
     */
    fun lastCloseEpochMs(now: ZonedDateTime = ZonedDateTime.now(NY)): Long {
        val ny = now.withZoneSameInstant(NY)
        var date = ny.toLocalDate()
        // 주말+연휴를 감안해도 14일이면 충분
        repeat(14) {
            if (isTradingDay(date)) {
                val close = ZonedDateTime.of(date, closeTime(date), NY)
                if (!close.isAfter(ny)) return close.toInstant().toEpochMilli()
            }
            date = date.minusDays(1)
        }
        return 0L
    }

    /** 주말·휴장일이 아닌 거래일 여부. */
    private fun isTradingDay(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY &&
            date.dayOfWeek != DayOfWeek.SUNDAY &&
            date !in holidays(date.year)

    /** 해당 거래일의 폐장 시각(조기 폐장일은 13:00). */
    private fun closeTime(date: LocalDate): LocalTime =
        if (isEarlyCloseDay(date)) EARLY_CLOSE else CLOSE

    /**
     * 조기 폐장일(13:00 마감): 독립기념일 전날(7/3), 추수감사절 다음 날, 크리스마스 이브(12/24).
     * 주말·휴장일 여부는 호출부(isTradingDay)에서 이미 걸러진다.
     */
    private fun isEarlyCloseDay(date: LocalDate): Boolean =
        (date.monthValue == 7 && date.dayOfMonth == 3) ||
            (date.monthValue == 12 && date.dayOfMonth == 24) ||
            date == thanksgiving(date.year).plusDays(1)

    /** 해당 연도의 미국 증시 휴장일(관측일 기준). */
    private fun holidays(year: Int): Set<LocalDate> = holidayCache.getOrPut(year) {
        val set = HashSet<LocalDate>()
        // 신정 — 1/1이 토요일이면 NYSE는 별도 휴장이 없다(전년 12/31 정상 개장)
        val newYear = observed(LocalDate.of(year, 1, 1))
        if (newYear.year == year) set.add(newYear)
        set.add(nthWeekday(year, 1, DayOfWeek.MONDAY, 3)) // 마틴 루서 킹의 날(1월 셋째 월요일)
        set.add(nthWeekday(year, 2, DayOfWeek.MONDAY, 3)) // 대통령의 날(2월 셋째 월요일)
        set.add(goodFriday(year)) // 성금요일(부활절 이틀 전)
        set.add(LocalDate.of(year, 5, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY))) // 메모리얼 데이
        set.add(observed(LocalDate.of(year, 6, 19))) // 준틴스
        set.add(observed(LocalDate.of(year, 7, 4))) // 독립기념일
        set.add(nthWeekday(year, 9, DayOfWeek.MONDAY, 1)) // 노동절(9월 첫째 월요일)
        set.add(thanksgiving(year)) // 추수감사절(11월 넷째 목요일)
        set.add(observed(LocalDate.of(year, 12, 25))) // 크리스마스
        set
    }

    /** 토요일 → 전날(금), 일요일 → 다음 날(월)로 관측. */
    private fun observed(date: LocalDate): LocalDate = when (date.dayOfWeek) {
        DayOfWeek.SATURDAY -> date.minusDays(1)
        DayOfWeek.SUNDAY -> date.plusDays(1)
        else -> date
    }

    /** 해당 월의 n번째 특정 요일. */
    private fun nthWeekday(year: Int, month: Int, dow: DayOfWeek, n: Int): LocalDate =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, dow))

    private fun thanksgiving(year: Int): LocalDate =
        nthWeekday(year, 11, DayOfWeek.THURSDAY, 4)

    /** 성금요일 = 부활절(서방 교회, 그레고리력 익명 알고리즘) - 2일. */
    private fun goodFriday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day).minusDays(2)
    }
}
