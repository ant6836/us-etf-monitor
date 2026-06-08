"""고점 대비 낙폭(Drawdown from Period High) 계산 모듈.

본 시스템의 유일한 지표를 계산한다. 매수 신호 판정은 v4에서 제거되었다.
"""


def calc_drawdown(current_price: float, period_highs: list[float]) -> float:
    """기간 내 고가 최댓값(peak) 대비 현재가의 하락률(%)을 반환한다.

    현재가는 항상 peak 이하이므로 결과는 0.0(고점 갱신 중) 또는 음수(하락)다.

    Args:
        current_price: 현재가(최신 시세).
        period_highs: 기간 내 일별 고가(High) 리스트. 당일 intraday 값 포함 가능.

    Returns:
        소수점 둘째 자리로 반올림한 하락률(%). 데이터가 없거나 비정상이면 0.0.
    """
    if not period_highs:
        return 0.0
    peak = max(period_highs)
    if peak <= 0:
        return 0.0
    return round((current_price - peak) / peak * 100, 2)
