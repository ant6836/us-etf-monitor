"""FastAPI 진입점.

- 스케줄러가 개장 중 5분 주기로 시세를 수집·연산해 캐시에 저장한다.
- GET /api/v1/ticker/mdd 는 캐시된 결과를 반환한다.
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from datetime import datetime, timezone

from apscheduler.schedulers.background import BackgroundScheduler
from fastapi import FastAPI, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from .cache import result_cache
from .drawdown import calc_drawdown
from .fetcher import UpstreamError, get_fetcher
from .market import is_market_open
from .schemas import NAMES, TICKERS

logger = logging.getLogger("mdd")
logging.basicConfig(level=logging.INFO)

# 갱신 주기(초): 개장 중 5분 / 폐장·주말 6시간
REFRESH_OPEN_SEC = 300
REFRESH_CLOSED_SEC = 6 * 60 * 60


def build_response() -> dict:
    """시세를 수집해 낙폭을 계산하고 응답 dict를 만든다.

    Raises:
        UpstreamError: 시세 소스 장애 시.
    """
    fetcher = get_fetcher()
    raw = fetcher.fetch()

    data: dict[str, dict] = {}
    for key, info in raw.items():
        current = info["current_price"]
        periods: dict[str, dict] = {}
        for period, highs in info["highs"].items():
            peak = max(highs) if highs else 0.0
            periods[period] = {
                "period_high": round(peak, 2),
                "drop_ratio": calc_drawdown(current, highs),
            }
        data[key] = {
            "ticker": TICKERS[key],
            "name": NAMES[key],
            "current_price": round(current, 2),
            "periods": periods,
        }

    return {
        "schema_version": "2.0",
        "updated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "data_delay_minutes": 15,
        "is_market_open": is_market_open(),
        "data": data,
    }


def refresh_cache() -> None:
    """시세를 갱신해 캐시에 저장한다. 실패해도 기존 캐시는 유지된다."""
    try:
        result = build_response()
        result_cache.set(result)
        logger.info("캐시 갱신 완료 (is_market_open=%s)", result["is_market_open"])
    except Exception as exc:  # noqa: BLE001 - 스케줄러가 죽지 않도록 광범위 포착
        logger.warning("캐시 갱신 실패: %s", exc)


def _schedule_next(scheduler: BackgroundScheduler) -> None:
    """현재 개장 여부에 맞춰 다음 갱신 주기를 (재)등록한다."""
    interval = REFRESH_OPEN_SEC if is_market_open() else REFRESH_CLOSED_SEC
    scheduler.add_job(
        refresh_cache,
        "interval",
        seconds=interval,
        id="refresh",
        replace_existing=True,
        next_run_time=datetime.now(timezone.utc),
    )
    logger.info("다음 갱신 주기 등록: %d초", interval)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 시작 시 1회 즉시 수집 + 스케줄러 가동
    refresh_cache()
    scheduler = BackgroundScheduler(timezone="UTC")
    _schedule_next(scheduler)
    # 개장 상태가 바뀔 수 있으므로 주기적으로 스케줄을 재평가(10분마다)
    scheduler.add_job(
        lambda: _schedule_next(scheduler),
        "interval",
        minutes=10,
        id="reschedule",
        replace_existing=True,
    )
    scheduler.start()
    logger.info("스케줄러 시작")
    try:
        yield
    finally:
        scheduler.shutdown(wait=False)
        logger.info("스케줄러 종료")


app = FastAPI(title="금융지수 낙폭 위젯 API", version="2.0", lifespan=lifespan)

# 개인용이지만 위젯/브라우저 검증 편의를 위해 CORS 허용
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET"],
    allow_headers=["*"],
)


@app.get("/healthz")
def healthz() -> dict:
    return {"status": "ok"}


@app.get("/api/v1/ticker/mdd")
def get_mdd(response: Response):
    """전 기간(1M·3M·1Y) 고점 대비 낙폭을 일괄 반환한다."""
    cached = result_cache.get()
    if cached is not None:
        max_age = REFRESH_OPEN_SEC if cached.get("is_market_open") else REFRESH_CLOSED_SEC
        response.headers["Cache-Control"] = f"max-age={max_age}"
        return cached

    # 캐시가 비어 있으면 즉시 한 번 시도
    try:
        result = build_response()
        result_cache.set(result)
        response.headers["Cache-Control"] = f"max-age={REFRESH_OPEN_SEC}"
        return result
    except UpstreamError as exc:
        return JSONResponse(
            status_code=503,
            content={
                "error": {
                    "code": "UPSTREAM_UNAVAILABLE",
                    "message": str(exc) or "시세 소스 응답 없음",
                    "retry_after": 60,
                }
            },
        )
    except Exception as exc:  # noqa: BLE001
        logger.exception("내부 오류")
        return JSONResponse(
            status_code=500,
            content={
                "error": {
                    "code": "INTERNAL_ERROR",
                    "message": "내부 오류",
                    "retry_after": 60,
                }
            },
        )
