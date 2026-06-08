"""시세 수집 모듈.

PriceFetcher 인터페이스로 추상화해 추후 정식 API(Polygon 등)로 교체 가능하게 한다.
기본 구현은 yfinance(비공식, 약 15분 지연). 개발/오프라인 검증용 MockFetcher도 제공한다.
"""

from __future__ import annotations

from abc import ABC, abstractmethod

from .schemas import PERIOD_DAYS, TICKERS


class UpstreamError(Exception):
    """시세 소스 장애(503으로 매핑)."""


class PriceFetcher(ABC):
    """시세 수집 인터페이스.

    fetch()는 지수 키(NDX/SPX)별로 아래 구조를 반환한다::

        {
          "NDX": {
            "current_price": float,
            "highs": {"1m": [float, ...], "3m": [...], "1y": [...]}
          },
          ...
        }

    highs 리스트는 해당 기간 내 일별 고가 + 당일 현재가(intraday 보정)를 담는다.
    """

    @abstractmethod
    def fetch(self) -> dict[str, dict]:
        ...


class YFinanceFetcher(PriceFetcher):
    """yfinance 기반 실제 수집기."""

    def fetch(self) -> dict[str, dict]:
        import pandas as pd
        import yfinance as yf

        out: dict[str, dict] = {}
        for key, sym in TICKERS.items():
            t = yf.Ticker(sym)
            hist = t.history(period="1y", interval="1d", auto_adjust=False)
            if hist is None or hist.empty:
                raise UpstreamError(f"시세 소스 응답 없음: {sym}")

            # 현재가: fast_info 우선, 실패 시 최근 종가
            current: float
            try:
                current = float(t.fast_info["last_price"])
            except Exception:
                current = float(hist["Close"].iloc[-1])

            last_date = hist.index[-1]
            highs: dict[str, list[float]] = {}
            for period, days in PERIOD_DAYS.items():
                cutoff = last_date - pd.Timedelta(days=days)
                window = hist.loc[hist.index >= cutoff]
                values = [float(v) for v in window["High"].tolist()]
                # 당일 intraday 현재가를 고점 후보로 포함
                values.append(current)
                highs[period] = values

            out[key] = {"current_price": current, "highs": highs}
        return out


class MockFetcher(PriceFetcher):
    """네트워크 없이 동작 확인용 고정 데이터 수집기.

    환경변수 USE_MOCK=1 일 때 사용한다.
    """

    def fetch(self) -> dict[str, dict]:
        return {
            "NDX": {
                "current_price": 18250.45,
                "highs": {
                    "1m": [19000.0, 19211.0, 18900.0, 18250.45],
                    "3m": [19500.0, 20000.0, 19211.0, 18250.45],
                    "1y": [20000.0, 20500.0, 20000.0, 18250.45],
                },
            },
            "SPX": {
                "current_price": 5230.15,
                "highs": {
                    "1m": [5300.0, 5310.0, 5280.0, 5230.15],
                    "3m": [5350.0, 5400.5, 5310.0, 5230.15],
                    "1y": [5450.0, 5495.0, 5400.5, 5230.15],
                },
            },
        }


def get_fetcher() -> PriceFetcher:
    """환경변수에 따라 적절한 수집기를 반환한다."""
    import os

    if os.getenv("USE_MOCK") == "1":
        return MockFetcher()
    return YFinanceFetcher()
