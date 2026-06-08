"""API 엔드포인트 테스트(MockFetcher 사용, 네트워크 불필요)."""

import os

os.environ["USE_MOCK"] = "1"  # 앱 import 전에 mock 강제

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_healthz():
    r = client.get("/healthz")
    assert r.status_code == 200
    assert r.json() == {"status": "ok"}


def test_mdd_shape_and_no_signal():
    r = client.get("/api/v1/ticker/mdd")
    assert r.status_code == 200
    body = r.json()

    assert body["schema_version"] == "2.0"
    assert "NDX" in body["data"]
    assert "SPX" in body["data"]

    ndx = body["data"]["NDX"]
    assert ndx["ticker"] == "^NDX"
    assert set(ndx["periods"].keys()) == {"1m", "3m", "1y"}

    one_month = ndx["periods"]["1m"]
    # 매수 신호 필드가 제거되었는지 확인
    assert "signal" not in one_month
    assert "drop_ratio" in one_month
    assert "period_high" in one_month
    # 낙폭은 0 이하
    assert one_month["drop_ratio"] <= 0


def test_mdd_mock_values():
    # MockFetcher 기준: NDX 1m 고점 19211, 현재가 18250.45 → -5.0%
    body = client.get("/api/v1/ticker/mdd").json()
    assert body["data"]["NDX"]["periods"]["1m"]["drop_ratio"] == -5.0
    assert body["data"]["NDX"]["periods"]["1m"]["period_high"] == 19211.0
