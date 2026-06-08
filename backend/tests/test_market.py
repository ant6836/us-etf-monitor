"""미국 증시 개장 판정 테스트(서머타임 포함)."""

from datetime import datetime
from zoneinfo import ZoneInfo

from app.market import is_market_open

NY = ZoneInfo("America/New_York")


def test_weekday_open_hours():
    # 2026-06-08(월) 12:00 ET → 개장
    assert is_market_open(datetime(2026, 6, 8, 12, 0, tzinfo=NY)) is True


def test_before_open():
    # 09:00 ET → 폐장(09:30 전)
    assert is_market_open(datetime(2026, 6, 8, 9, 0, tzinfo=NY)) is False


def test_after_close():
    # 16:30 ET → 폐장
    assert is_market_open(datetime(2026, 6, 8, 16, 30, tzinfo=NY)) is False


def test_open_boundary_0930():
    assert is_market_open(datetime(2026, 6, 8, 9, 30, tzinfo=NY)) is True


def test_close_boundary_1600():
    assert is_market_open(datetime(2026, 6, 8, 16, 0, tzinfo=NY)) is True


def test_weekend_closed():
    # 2026-06-06(토)
    assert is_market_open(datetime(2026, 6, 6, 12, 0, tzinfo=NY)) is False


def test_holiday_closed():
    # 2026-12-25(금) 크리스마스
    assert is_market_open(datetime(2026, 12, 25, 12, 0, tzinfo=NY)) is False


def test_kst_input_converted():
    # KST(서울) 자정은 전날 ET 정규장 시간일 수 있다.
    # 2026-06-09 00:00 KST == 2026-06-08 11:00 ET (서머타임, EDT=UTC-4) → 개장
    seoul = ZoneInfo("Asia/Seoul")
    assert is_market_open(datetime(2026, 6, 9, 0, 0, tzinfo=seoul)) is True
