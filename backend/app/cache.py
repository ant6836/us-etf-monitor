"""연산 결과 캐시.

마지막으로 성공적으로 산출한 응답을 보관한다. 스케줄러가 주기적으로 갱신하고,
엔드포인트는 이 캐시를 읽어 반환한다(외부 호출량을 위젯 수와 무관하게 유지).
"""

import threading
from typing import Any


class ResultCache:
    """스레드 안전한 단일 결과 캐시."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._value: Any | None = None

    def get(self) -> Any | None:
        with self._lock:
            return self._value

    def set(self, value: Any) -> None:
        with self._lock:
            self._value = value


# 모듈 전역 캐시 인스턴스
result_cache = ResultCache()
