# Python 3.12 사용: pandas/yfinance 휠 호환성이 안정적이다(호스트의 3.14와 무관).
FROM python:3.12-slim

WORKDIR /app

# 빌드 캐시 활용을 위해 의존성 먼저 설치
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

EXPOSE 8000

# 컨테이너 헬스체크
HEALTHCHECK --interval=60s --timeout=5s --start-period=20s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/healthz')" || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
