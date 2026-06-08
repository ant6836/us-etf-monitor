"""API 응답 Pydantic 모델 (schema_version 2.0 — 매수 신호 필드 제거)."""

from pydantic import BaseModel, Field

# 추적 대상 지수 정의
TICKERS: dict[str, str] = {"NDX": "^NDX", "SPX": "^GSPC"}
NAMES: dict[str, str] = {"NDX": "Nasdaq 100", "SPX": "S&P 500"}

# 기준 기간별 일수(달력 기준). 1m/3m/1y.
PERIOD_DAYS: dict[str, int] = {"1m": 30, "3m": 90, "1y": 365}


class PeriodData(BaseModel):
    """기간 하나에 대한 고점/낙폭 정보."""

    period_high: float = Field(..., description="기간 내 최고가")
    drop_ratio: float = Field(..., description="고점 대비 현재 낙폭(%). 0 또는 음수")


class TickerData(BaseModel):
    """지수 하나에 대한 응답."""

    ticker: str
    name: str
    current_price: float
    periods: dict[str, PeriodData]


class MddResponse(BaseModel):
    """GET /api/v1/ticker/mdd 응답 본문."""

    schema_version: str = "2.0"
    updated_at: str = Field(..., description="데이터 산출 시각(UTC, ISO8601)")
    data_delay_minutes: int = 15
    is_market_open: bool
    data: dict[str, TickerData]


class ErrorDetail(BaseModel):
    code: str
    message: str
    retry_after: int = 60


class ErrorResponse(BaseModel):
    error: ErrorDetail
