package com.example.etfdrawdown

import com.example.etfdrawdown.data.MarketHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * MarketHours 검증.
 * 핵심: 서머타임(EDT/EST) 전환으로 한국 시간 기준 개장 시각이
 * 22:30(여름) ↔ 23:30(겨울)으로 바뀌어도 뉴욕 시간 기준으로 올바르게 판정하는지.
 */
class MarketHoursTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private val ny = ZoneId.of("America/New_York")

    private fun kst(y: Int, mo: Int, d: Int, h: Int, mi: Int): ZonedDateTime =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, seoul)

    private fun nyt(y: Int, mo: Int, d: Int, h: Int, mi: Int): ZonedDateTime =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ny)

    // ---- 서머타임(EDT, 3월 둘째 일요일 ~ 11월 첫째 일요일): 한국 22:30 개장 ----

    @Test
    fun `여름 한국시간 22시30분은 뉴욕 9시30분 - 개장`() {
        assertTrue(MarketHours.isOpen(kst(2026, 7, 15, 22, 30))) // 수요일
    }

    @Test
    fun `여름 한국시간 22시29분은 개장 전`() {
        assertFalse(MarketHours.isOpen(kst(2026, 7, 15, 22, 29)))
    }

    @Test
    fun `여름 한국시간 다음날 새벽 4시59분은 뉴욕 15시59분 - 개장중`() {
        assertTrue(MarketHours.isOpen(kst(2026, 7, 16, 4, 59))) // 뉴욕은 아직 7/15
    }

    @Test
    fun `여름 한국시간 새벽 5시 1분은 폐장 후`() {
        assertFalse(MarketHours.isOpen(kst(2026, 7, 16, 5, 1)))
    }

    // ---- 표준시(EST, 11월~3월): 한국 23:30 개장 ----

    @Test
    fun `겨울 한국시간 22시30분은 뉴욕 8시30분 - 아직 개장 전`() {
        assertFalse(MarketHours.isOpen(kst(2026, 1, 15, 22, 30))) // 목요일
    }

    @Test
    fun `겨울 한국시간 23시30분은 뉴욕 9시30분 - 개장`() {
        assertTrue(MarketHours.isOpen(kst(2026, 1, 15, 23, 30)))
    }

    @Test
    fun `겨울 한국시간 다음날 새벽 6시 정각은 뉴욕 16시 - 폐장 직전(포함)`() {
        assertTrue(MarketHours.isOpen(kst(2026, 1, 16, 6, 0)))
    }

    @Test
    fun `겨울 한국시간 새벽 6시 1분은 폐장 후`() {
        assertFalse(MarketHours.isOpen(kst(2026, 1, 16, 6, 1)))
    }

    // ---- 주말/휴장일 ----

    @Test
    fun `토요일은 폐장`() {
        assertFalse(MarketHours.isOpen(nyt(2026, 7, 18, 11, 0)))
    }

    @Test
    fun `MLK의 날(1월 셋째 월요일)은 휴장`() {
        assertFalse(MarketHours.isOpen(nyt(2026, 1, 19, 11, 0)))
    }

    @Test
    fun `성금요일은 휴장 - 부활절 알고리즘 검증`() {
        assertFalse(MarketHours.isOpen(nyt(2026, 4, 3, 11, 0))) // 2026년 부활절 4/5
        assertFalse(MarketHours.isOpen(nyt(2027, 3, 26, 11, 0))) // 2027년 부활절 3/28
    }

    @Test
    fun `추수감사절(11월 넷째 목요일)은 휴장`() {
        assertFalse(MarketHours.isOpen(nyt(2026, 11, 26, 11, 0)))
    }

    @Test
    fun `독립기념일이 토요일이면 금요일 관측 휴장`() {
        // 2026-07-04는 토요일 → 7/3(금) 휴장
        assertFalse(MarketHours.isOpen(nyt(2026, 7, 3, 11, 0)))
    }

    @Test
    fun `준틴스가 토요일이면 금요일 관측 휴장 - 2027년`() {
        // 2027-06-19는 토요일 → 6/18(금) 휴장
        assertFalse(MarketHours.isOpen(nyt(2027, 6, 18, 11, 0)))
        assertTrue(MarketHours.isOpen(nyt(2027, 6, 17, 11, 0))) // 전날 목요일은 정상 개장
    }

    @Test
    fun `연도 하드코딩 없음 - 2030년 크리스마스도 휴장`() {
        assertFalse(MarketHours.isOpen(nyt(2030, 12, 25, 11, 0)))
    }

    // ---- 조기 폐장(13:00) ----

    @Test
    fun `추수감사절 다음날은 13시 조기 폐장`() {
        assertTrue(MarketHours.isOpen(nyt(2026, 11, 27, 12, 30)))
        assertFalse(MarketHours.isOpen(nyt(2026, 11, 27, 13, 30)))
    }

    @Test
    fun `크리스마스 이브는 13시 조기 폐장`() {
        // 2026-12-24는 목요일
        assertTrue(MarketHours.isOpen(nyt(2026, 12, 24, 12, 0)))
        assertFalse(MarketHours.isOpen(nyt(2026, 12, 24, 14, 0)))
    }

    // ---- 최근 폐장 시각(워커의 폐장 중 호출 생략에 사용) ----

    @Test
    fun `토요일의 최근 폐장은 금요일 16시`() {
        val saturdayNoon = nyt(2026, 7, 18, 12, 0)
        val expected = nyt(2026, 7, 17, 16, 0).toInstant().toEpochMilli()
        assertEquals(expected, MarketHours.lastCloseEpochMs(saturdayNoon))
    }

    @Test
    fun `조기 폐장일 오후의 최근 폐장은 당일 13시`() {
        val afterEarlyClose = nyt(2026, 11, 27, 15, 0)
        val expected = nyt(2026, 11, 27, 13, 0).toInstant().toEpochMilli()
        assertEquals(expected, MarketHours.lastCloseEpochMs(afterEarlyClose))
    }

    @Test
    fun `추수감사절 당일의 최근 폐장은 수요일 16시`() {
        val onHoliday = nyt(2026, 11, 26, 12, 0)
        val expected = nyt(2026, 11, 25, 16, 0).toInstant().toEpochMilli()
        assertEquals(expected, MarketHours.lastCloseEpochMs(onHoliday))
    }
}
