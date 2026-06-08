"""낙폭 계산 경계값 테스트."""

import pytest

from app.drawdown import calc_drawdown


def test_at_peak_returns_zero():
    # 현재가가 고점과 같으면 0%
    assert calc_drawdown(100.0, [100.0, 90.0, 80.0]) == 0.0


def test_minus_five_percent():
    # 100 고점에서 95 → -5.0%
    assert calc_drawdown(95.0, [100.0, 95.0]) == -5.0


def test_just_above_minus_five():
    # -4.99% 경계
    assert calc_drawdown(95.01, [100.0]) == -4.99


def test_minus_ten_percent():
    assert calc_drawdown(90.0, [100.0]) == -10.0


def test_below_minus_ten():
    assert calc_drawdown(85.0, [100.0]) == -15.0


def test_never_positive():
    # 현재가가 고점 후보에 포함되므로 양수가 나오지 않는다
    highs = [100.0, 110.0, 120.0]
    assert calc_drawdown(120.0, highs) == 0.0


def test_empty_highs_returns_zero():
    assert calc_drawdown(100.0, []) == 0.0


def test_nonpositive_peak_returns_zero():
    assert calc_drawdown(100.0, [0.0, -5.0]) == 0.0


def test_rounding_two_decimals():
    # 반올림이 둘째 자리까지인지
    result = calc_drawdown(18250.45, [19211.0])
    assert result == pytest.approx(-5.0, abs=0.01)
