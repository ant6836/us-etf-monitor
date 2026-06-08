"""미국 증시 개장 여부 판정 모듈.

시각을 하드코딩하지 않고 America/New_York 타임존 기준으로 동적 판정한다.
서머타임(EDT/EST)은 ZoneInfo가 자동 처리한다.
"""

from datetime import datetime, time
from zoneinfo import ZoneInfo

NY_TZ = ZoneInfo("America/New_York")

# 정규장 시간(ET): 09:30 ~ 16:00
_OPEN = time(9, 30)
_CLOSE = time(16, 0)

# 미국 증시 주요 휴장일(고정·관측일). 필요 시 갱신.
# yfinance가 휴장일엔 데이터를 주지 않으므로 보조적 판정 용도다.
_HOLIDAYS = {
    # 2026년
    "2026-01-01",  # 신년
    "2026-01-19",  # MLK Day
    "2026-02-16",  # Presidents Day
    "2026-04-03",  # Good Friday
    "2026-05-25",  # Memorial Day
    "2026-06-19",  # Juneteenth
    "2026-07-03",  # Independence Day(관측)
    "2026-09-07",  # Labor Day
    "2026-11-26",  # Thanksgiving
    "2026-12-25",  # Christmas
}


def is_market_open(now: datetime | None = None) -> bool:
    """현재 미국 증시 정규장 개장 여부를 반환한다.

    Args:
        now: 판정 기준 시각(테스트용). None이면 현재 시각.
             tz-naive면 ET로 간주, tz-aware면 ET로 변환한다.

    Returns:
        정규장 시간대(주중 09:30~16:00 ET, 휴장일 제외)면 True.
    """
    if now is None:
        now = datetime.now(NY_TZ)
    elif now.tzinfo is None:
        now = now.replace(tzinfo=NY_TZ)
    else:
        now = now.astimezone(NY_TZ)

    # 주말 제외
    if now.weekday() >= 5:  # 5=토, 6=일
        return False

    # 휴장일 제외
    if now.strftime("%Y-%m-%d") in _HOLIDAYS:
        return False

    return _OPEN <= now.time() <= _CLOSE
