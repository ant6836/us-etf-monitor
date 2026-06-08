# 금융 지수 고점 대비 낙폭(Drawdown) 표시 위젯 — 개발 명세서 v4 (개인용 / Claude Code 구현 기준)

본 문서는 **클로드 코드(Claude Code)가 단계별로 구현할 수 있도록** 작성된 최종 개발 명세서입니다.
나스닥 100(`^NDX`)·S&P 500(`^GSPC`)의 **설정 기간 내 최고점 대비 현재 하락률(Drawdown from Period High)**을 계산해, 갤럭시 홈화면 위젯에 **"고점 대비 현재 몇 % 하락" 숫자만** 표시한다.

> 🔄 **v3 → v4 변경 요약**: 기존 v3의 **매수 신호(`HOLD`/`BUY_2X`/`BUY_3X`) 및 레버리지 ETF 추천 기능을 전면 제거**했다. 위젯은 이제 **낙폭 수치(%)만 표시**하는 단순 정보 위젯이다. 신호 판정, 히스테리시스, 조건별 매수 색상 분기, 투자 자문성 면책 문구가 모두 빠졌다.

> 🏗️ **아키텍처 결정(부록 A 채택)**: 개인 1인 사용이므로 **백엔드 서버 없이 앱이 야후 시세를 직접 호출**해 기기 내에서 낙폭을 계산하는 **서버리스 단일 앱 구조**를 채택한다. 호스팅·상시 구동 서버·월 비용이 모두 불필요하다. (기존 `backend/` 코드는 참고용으로 보존하되 빌드/배포에는 사용하지 않는다.) 야후 차트 엔드포인트가 인증 없이 동작함을 검증 완료(2026-06-08).

- **배포 방식**: 개인 사용. 구글 플레이 미등록, **APK 사이드로딩**으로 본인 갤럭시에만 설치.
- **구현 주체**: 코드는 Claude Code가 작성한다.
- **사람(운영자)이 담당(코드 외)**: 개발 환경 설치(Android Studio/SDK), 실물 폰 설치. → **9장 운영자 가이드** 참조.

> 📌 **Claude Code 사용 지침**: 이 프로젝트에는 사람만 할 수 있는 비(非)코드 단계가 있다(Android Studio 설치, 폰에 APK 설치). 각 Phase를 시작·종료할 때 9장의 해당 체크포인트를 사용자에게 안내하고, 사용자 입력이 필요한 값은 임의로 가정하지 말고 **반드시 사용자에게 물어본다.** 사용자가 막히면 9장의 절차를 단계별로 풀어 설명한다.

> ⚠️ **데이터 한계 고지**: 표시되는 낙폭은 약 15분 지연된 시세 기반의 **참고 정보**다. 투자 판단의 근거가 아니다. (앱 최초 실행 시 1회 노출 권장)

---

## 0. 핵심 개념 정의 (Terminology)

| 용어 | 정의 | 사용 |
| :--- | :--- | :--- |
| **고점 대비 낙폭 (Drawdown from Period High)** | 설정 기간 내 최고점 대비 **현재가**의 하락률 | ✅ 유일한 지표 |
| **MDD (Maximum Drawdown)** | 기간 내 고점→저점 **최대** 낙폭 | ⏸️ 범위 외 (향후 확장) |

> 본 시스템의 유일한 지표는 "현재 시점의 고점 대비 낙폭"이며 MDD와 다르다. 문서 전체에서 **Drawdown(DD)**으로 통일한다.
> 참고: 기준이 "기간 내 고점"이므로 현재가는 항상 고점 이하다. 따라서 결과는 **0%(고점 갱신 중) 또는 음수(하락)**만 나오며, 양수(+상승)는 표시되지 않는다.

---

## 1. 시스템 아키텍처 (서버리스 단일 앱)

서버 없이 **앱이 야후 시세를 직접 호출**하고 기기 내에서 낙폭을 계산하는 1-Tier 구조.

```
[Yahoo Finance Chart API (query1.finance.yahoo.com)]
        │ (앱이 WorkManager로 주기 호출, User-Agent 헤더만 필요)
        ▼
[Android App]
        │  ├─ JSON 파싱(현재가 + 일별 고가)
        │  ├─ 기간별(1M·3M·1Y) 고점 추출 + Drawdown 연산
        │  └─ 마지막 성공값 DataStore 캐싱
        ▼
[Galaxy Home Screen Widget (RemoteViews + AppWidgetProvider)]
```

- **호출 엔드포인트**: `GET https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=1y&interval=1d`
  - `{symbol}`: `^NDX`(URL 인코딩 `%5ENDX`), `^GSPC`(`%5EGSPC`)
  - 헤더 `User-Agent`만 있으면 인증 없이 동작(검증 완료).
  - 응답에서 `chart.result[0].meta.regularMarketPrice`(현재가), `chart.result[0].indicators.quote[0].high`(일별 고가 배열) 사용.
- **호출량**: 지수당 1년치 1회 = **총 2회/갱신**. 1년치를 받아 앱에서 1M/3M/1Y 구간을 잘라 계산.
- **소스 불안정성 대비**: 야후 비공식 엔드포인트는 가끔 실패/차단 가능. 실패 시 마지막 성공값(DataStore)을 그대로 표시하고 다음 주기에 재시도한다.

---

## 2. 프로젝트 구조

```
us_etf_monitor/
├─ android/                  # ← 실제 빌드/배포 대상 (서버리스 단일 앱)
│  ├─ app/src/main/java/.../
│  │  ├─ data/   (YahooApi 서비스, DTO, 낙폭 계산, Repository)
│  │  ├─ work/   (UpdateWorker, WorkManager 스케줄러)
│  │  ├─ widget/ (GlanceAppWidget, Receiver, Configuration Activity)
│  │  └─ ui/     (테마, 색상 컴포저블)
│  ├─ app/build.gradle.kts
│  └─ gradle/...
└─ backend/                  # (참고용 보존, 부록 A에서는 미사용)
   ├─ app/ (drawdown, market, fetcher, cache, schemas, main)
   └─ tests/
```

> 낙폭 계산 로직(`calc_drawdown`)과 개장 판정은 `backend/`에 검증된 파이썬 구현이 있으므로, 안드로이드(Kotlin) 포팅 시 동일 경계값 테스트를 그대로 옮긴다.

---

## 3. 백엔드 명세 (Backend)

### 3.1 기술 스택
- FastAPI(Async), `pandas`, `yfinance`
- 스케줄러: `APScheduler`
- 캐시: 인메모리(`cachetools`)
- 배포: Docker

### 3.2 데이터 수집 정책 — 중요
- **소스 한계**: `yfinance`는 비공식이라 **약 15분 지연**, 중단·rate limit 가능. 본 시스템은 "지연 시세" 전제.
- **추상화**: `fetcher.py`를 인터페이스로 두어 추후 정식 API(Polygon 등)로 교체 가능하게 한다.
- **기준 명확화**: `period_high`는 기간 내 **일별 고가(High) 최댓값 + 당일 intraday 반영**, `current_price`는 최신가.
- **수집 주기**: 개장 중 5분 주기로 재연산·캐싱(클라이언트 폴링 30분보다 짧게).

### 3.3 Drawdown 산출
$$\text{Drawdown}_{\%} = \frac{P_{current} - \max(H_T)}{\max(H_T)} \times 100$$

```python
def calc_drawdown(current_price: float, period_highs: list[float]) -> float:
    # 기간 내 고가 최댓값(peak) 대비 현재가의 하락률(%)을 반환한다.
    # 현재가는 항상 peak 이하이므로 결과는 0.0(고점) 또는 음수(하락)다.
    peak = max(period_highs)
    if peak <= 0:
        return 0.0
    return round((current_price - peak) / peak * 100, 2)
```

> v3에 있던 신호 판정(`decide_signal`, `HOLD`/`BUY_2X`/`BUY_3X`)과 히스테리시스는 **v4에서 제거**했다. 백엔드는 낙폭 수치만 계산해 반환한다.

### 3.4 API 엔드포인트

#### `GET /api/v1/ticker/mdd`
- 전 기간(1M·3M·1Y) 데이터 일괄 반환(파라미터 없음).
- (선택) 헤더 `X-API-Key` 검증. 개인용·비공개 서버면 생략 가능.
- **응답 예시(검산 완료)**:

```json
{
  "schema_version": "2.0",
  "updated_at": "2026-06-05T16:00:02Z",
  "data_delay_minutes": 15,
  "is_market_open": false,
  "data": {
    "NDX": {
      "ticker": "^NDX", "name": "Nasdaq 100", "current_price": 18250.45,
      "periods": {
        "1m": { "period_high": 19211.00, "drop_ratio": -5.00 },
        "3m": { "period_high": 20000.00, "drop_ratio": -8.75 },
        "1y": { "period_high": 20500.00, "drop_ratio": -10.97 }
      }
    },
    "SPX": {
      "ticker": "^GSPC", "name": "S&P 500", "current_price": 5230.15,
      "periods": {
        "1m": { "period_high": 5310.00, "drop_ratio": -1.50 },
        "3m": { "period_high": 5400.50, "drop_ratio": -3.15 },
        "1y": { "period_high": 5495.00, "drop_ratio": -4.82 }
      }
    }
  }
}
```

> v3 응답에 있던 `"signal"` 필드는 **v4에서 제거**(`schema_version`을 `2.0`으로 올림). 클라이언트는 `drop_ratio`만 사용한다.

#### `GET /healthz`
- 헬스체크. `{"status":"ok"}` 반환.

### 3.5 캐싱
- 캐시 TTL: 개장 중 5분 / 폐장·주말 6시간.
- 응답 헤더 `Cache-Control: max-age=300`.

### 3.6 에러 응답 규격
```json
{ "error": { "code": "UPSTREAM_UNAVAILABLE", "message": "시세 소스 응답 없음", "retry_after": 60 } }
```
| 상황 | HTTP | code |
| :--- | :--- | :--- |
| 시세 소스 장애 | 503 | `UPSTREAM_UNAVAILABLE` |
| 내부 오류 | 500 | `INTERNAL_ERROR` |

### 3.7 실행 방법 (로컬 검증)
```bash
cd backend
python -m venv .venv && source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
# 확인: curl http://localhost:8000/api/v1/ticker/mdd
pytest                                               # 단위 테스트
```

### 3.8 Docker
```bash
cd backend
docker build -t mdd-backend .
docker run -d -p 8000:8000 --restart unless-stopped --name mdd mdd-backend
```

---

## 4. 안드로이드 클라이언트 명세 (Android)

### 4.1 기술 스택 (구현 확정)
- Kotlin, **RemoteViews + AppWidgetProvider**(전통적 위젯 — 최신 AGP 9.x에서 Compose 컴파일러 의존 없이 안정적), WorkManager(CoroutineWorker), 네트워크는 내장 `HttpURLConnection`, JSON 파싱은 내장 `org.json`, 저장은 `SharedPreferences`.
- 추가 의존성은 `androidx.work:work-runtime-ktx` 하나뿐(나머지는 안드로이드/AGP 기본 제공).
- 빌드 환경: AGP 9.2.1 / Gradle 9.4.1 / compileSdk 36 / minSdk 26 / JDK 21. (Kotlin은 AGP 9.x 내장)

> **Glance 대신 RemoteViews를 쓴 이유**: 생성된 프로젝트가 AGP 9.2.1(Kotlin 내장, Compose 플러그인 미설정)이라, Glance(Compose 기반)를 쓰려면 Compose 컴파일러 구성이 추가로 필요해 빌드 리스크가 크다. 낙폭 % 텍스트 표시에는 RemoteViews로 충분하다.

### 4.2 데이터 소스 설정 (야후 직접 호출)
- `BASE_URL = https://query1.finance.yahoo.com/`로 고정(상수). 운영자가 제공할 서버 주소가 없다.
- 모든 요청에 `User-Agent` 헤더를 붙인다(미설정 시 차단될 수 있음).
- 심볼: `^NDX`, `^GSPC`. 경로에 넣을 때 URL 인코딩(`%5ENDX`, `%5EGSPC`).
- 1년치(`range=1y&interval=1d`)를 받아 앱에서 1M/3M/1Y로 분할 계산하므로 지수당 1회 호출.

### 4.3 백그라운드 업데이트 전략 (`WorkManager`)
1. **미국 증시 운영 시간(KST) — 정정값**

   | 구분 | KST 운영 시간 |
   | :--- | :--- |
   | 평시(겨울, EST=UTC−5) | **23:30 ~ 익일 06:00** |
   | 서머타임(여름, EDT=UTC−4) | **22:30 ~ 익일 05:00** |

   > 시각 하드코딩 금지. `ZoneId.of("America/New_York")`로 개장 여부를 **앱이 직접 동적 판정**한다(서버의 `is_market_open` 필드가 없으므로 기기에서 계산).
2. **갱신 주기**: 개장 시간대 30분 / 폐장·주말 6시간 또는 수동.
3. **제약 조건**: `NetworkType.CONNECTED`에서만 실행.
4. **한계 인지**: `PeriodicWorkRequest` 최소 주기 15분, Doze로 정확한 시각 보장 안 됨.
5. **휴장일/소스 실패**: 야후 호출 실패 시 마지막 성공값(DataStore)을 유지하고 다음 주기에 재시도. 폐장 판정 시 갱신 주기를 6시간으로 전환.

### 4.4 위젯 Configuration
- 배치 시 `GlanceAppWidgetReceiver`로 구성 Activity 실행.
- 위젯별 기준 기간(1달/3달/1년)을 선택, `appWidgetId`별로 DataStore에 저장.
- 최초 진입 시 데이터 한계 고지(2장 ⚠️) 1회 노출.

### 4.5 빌드 / 사이드로딩 (개인 배포)
```bash
cd android
./gradlew assembleRelease
# 산출물: app/build/outputs/apk/release/app-release.apk
```
- 위 APK를 갤럭시로 전송 → "출처를 알 수 없는 앱 설치" 허용 → 설치 → 홈 화면에 위젯 추가.
- (디버그 서명으로도 개인 설치는 충분. 자동 업데이트는 없으며 새 APK로 교체 설치.)

---

## 5. UI/UX

다크 모드 기본, `4x2` 기준 레이아웃. **낙폭 수치 표시에만 집중**한다.

### 5.1 레이아웃
- **Header**: 기준 기간(예: `기준: 최근 1개월 고점`) + 데이터 기준 시각 + 새로고침 아이콘
- **Body(2단)**: Left(Nasdaq 100) / Right(S&P 500)
- **Row**: 지수명·현재가(`10sp` Bold) / **고점 대비 낙폭(`%`, 큰 글씨 Bold)**

### 5.2 낙폭 표시 스타일 (정보 표시 전용 — 매수 신호 아님)
낙폭 수치는 가독성을 위한 **단계별 색상 강도**만 적용한다. "매수하라"는 의미가 아니라 **하락 폭의 시각적 강조**일 뿐이다.

| 낙폭(`d`) | 텍스트 색상 | 표현 예 |
| :--- | :--- | :--- |
| `-5.0 < d ≤ 0` | `#E2E8F0` (기본) | `-1.5%` |
| `-10.0 < d ≤ -5.0` | `#F59E0B` (주황) | `-7.2%` |
| `d ≤ -10.0` | `#EF4444` (적색) | `-12.4%` |

> 색상 구간은 단순히 "낙폭이 큰지" 눈에 띄게 하기 위한 것이며, 임계값을 넘어도 어떤 행동 지시(매수 등)도 표시하지 않는다.
> **색상 주의(한국 사용자)**: 한국은 통상 빨강=상승. 본 위젯은 "큰 하락"을 적색으로 강조하므로 색상에만 의존하지 말고 숫자(`-%`)를 항상 병기한다. 설정에서 색 테마 단색화 옵션 제공 가능.

---

## 6. 예외 처리 및 최적화
1. **네트워크 단절**: 마지막 성공 데이터를 DataStore 캐시로 렌더링, 최하단에 `연결 실패 (15:20 데이터)` 표기.
2. **수동 새로고침 셰이핑**: 마지막 호출 1분 미경과 시 요청 차단(디바운스).
3. **메모리 누수 방지**: 위젯 상태에는 가공된 `String`/`Float`만 보관(대용량 시계열 객체 금지).

---

## 7. 테스트 전략
| 레이어 | 항목 |
| :--- | :--- |
| Backend | `calc_drawdown` 경계값(0.0 / -5.0 / -10.0), 음수 반환 검증 |
| Backend | 시세 장애 fallback·에러 포맷, 캐시 TTL |
| Client | KST 개장 판정(서머타임 포함), 네트워크 단절 캐시 렌더링, 기간 설정 저장/복원, 낙폭 색상 구간 분기 |

---

## 8. 개발 로드맵 & 완료 기준 (Definition of Done)

### Phase 1 — Backend
- 구현: 수집·연산·캐싱·엔드포인트·에러·테스트.
- **완료 기준**: `uvicorn` 로컬 실행 후 `curl /api/v1/ticker/mdd`가 명세대로(신호 필드 없이 `drop_ratio`만) JSON 반환, 경계값 단위 테스트 통과, Docker 빌드/구동 성공.

### Phase 2 — Android Core
- 구현: Retrofit 통신, Repository, WorkManager 스케줄러(동적 개장 판정·휴장일), DataStore 캐시.
- **완료 기준**: 앱에서 백엔드 호출 성공, 네트워크 끊겨도 캐시 렌더링, 개장/폐장에 따라 주기 전환.

### Phase 3 — Android Widget UI
- 구현: Glance 레이아웃, 낙폭 수치 표시 + 색상 구간 분기, Configuration Activity, 데이터 한계 고지 노출.
- **완료 기준**: 갤럭시 홈 화면에서 위젯 동작, 낙폭 % 정상 표시, 기간 설정 반영, `assembleRelease`로 APK 산출.

### Phase 4 — 안정화(선택)
- 정식 시세 API 전환 검토, 로깅 보강, (원하면) 다른 기준점/추가 지수 확장.

> **Phase별 운영자 연동**: Phase 1 시작 전 → 9.1(환경)·9.2(호스팅 결정). Phase 1 배포 시 → 9.3(서버 올리기). Phase 2 시작 시 → 9.4(BASE_URL 제공). Phase 3 완료 후 → 9.5(폰 설치). 이후 → 9.6(운영).

---

## 9. 사람(운영자)이 직접 해야 하는 작업 (Operator Guide)

> Claude Code는 코드를 작성·로컬 실행·테스트한다. 그러나 아래 작업은 **사람만** 할 수 있다(도구 설치, 서버를 켜둘 장소 마련, 실물 폰 조작, 계정/결제/설정값 결정). Claude Code는 각 단계에서 이 절을 근거로 사용자를 안내한다.
>
> **전체 순서**: 9.1 환경 설치 → 9.2 호스팅 결정 → (Phase 1 개발) → 9.3 서버 올리기 → 9.4 BASE_URL 제공 → (Phase 2·3 개발) → 9.5 폰 설치 → 9.6 운영.

### 9.1 개발 환경 설치 (1회, 운영자 PC)
- **Claude Code 설치**: 네이티브 설치 권장 — macOS/Linux `curl -fsSL https://claude.ai/install.sh | bash`, Windows(PowerShell) `irm https://claude.ai/install.ps1 | iex`. 네이티브 방식은 Node.js가 필요 없다. npm 방식(`npm install -g @anthropic-ai/claude-code`)을 쓰면 **Node.js 18 이상** 필요. 로그인에는 유료 Claude 구독(또는 API) 계정이 필요.
- **Android Studio + JDK 설치**: APK 빌드에 필요(코드는 Claude Code가 쓰지만 빌드 도구는 PC에 있어야 함).
- 작업 폴더를 만들고 이 명세서를 넣은 뒤 `claude` 실행 → "이 명세서대로 Phase 1부터 구현해줘"로 시작.
- **체크포인트**: `claude --version`, `node --version`(npm 방식 시), Android Studio 정상 실행 확인.

### 9.2 백엔드 호스팅 결정 (운영자 선택 — 가장 중요)
백엔드는 Python이라 **항상 켜져 있는 컴퓨터**에서 돌아야 한다. 코드/배포 스크립트는 Claude Code가 작성하지만, "어디에 둘지"는 운영자가 정하고 계정을 만든다. 택1:

| 선택지 | 운영자가 할 일 | 비용 | 비고 |
| :--- | :--- | :--- | :--- |
| **A. 클라우드 VPS(권장)** | 우분투 서버 1대 생성·결제, 공인 IP 확보 | 월 ~5달러 | 가장 무난, 항상 공개 주소 |
| **B. 집 PC/미니PC + Tailscale** | 상시 구동 PC 준비, 폰·PC에 Tailscale 설치해 사설망 연결 | 0원 | PC 꺼지면 중단 |
| **C. 서버 없는 단일 앱(부록 A)** | 없음(서버 자체 제거) | 0원 | 소스 불안정성↑, 유지보수 부담↑ |

> 서버 부담을 최소화하려면 C, 안정성 우선이면 A, 무료 상시구동이면 B. 결정 결과를 Claude Code에 알려주면 그에 맞는 배포 절차를 안내한다.

### 9.3 백엔드 서버에 올리기 (Phase 1 완료 후)
- 로컬에서 `curl http://localhost:8000/api/v1/ticker/mdd`로 JSON이 명세대로 나오는지 먼저 확인(운영자).
- 9.2에서 고른 곳에 Docker로 구동(명령은 3.8 참조, Claude Code가 안내). 서버의 **외부 접속 주소(URL)**를 확보해 둔다.
- 가능하면 HTTPS 적용. 개인·비공개(예: Tailscale)면 평문도 허용 가능하나 권장하지 않음.
- **체크포인트**: 폰이 아닌 외부에서 `https://<서버주소>/healthz`가 `{"status":"ok"}` 반환.

### 9.4 BASE_URL 등 설정값 제공 (Phase 2 시작 시)
- 앱의 `BASE_URL`(예: `https://my-server.example.com/` 또는 Tailscale 주소)을 운영자가 Claude Code에 알려준다. Claude Code는 이를 `local.properties`/`BuildConfig`로 주입한다(하드코딩 금지).
- (선택) API Key를 쓰기로 했다면 그 값도 운영자가 정해 서버·앱 양쪽에 동일하게 제공.

### 9.5 폰에 설치 (Phase 3 완료 후, 실물 조작)
- Claude Code가 `./gradlew assembleRelease`로 `app-release.apk`까지 생성. **이 파일을 폰에 넣고 설치하는 것은 운영자 몫.**
1. 갤럭시 설정에서 개발자 옵션·USB 디버깅을 켜고 PC와 연결하거나, APK를 폰으로 직접 전송.
2. "출처를 알 수 없는 앱 설치" 허용 → APK 실행 → 설치.
3. 홈 화면 길게 눌러 위젯 추가 → 기준 기간 선택 → 고지 확인.
- **체크포인트**: 위젯에 실제 지수·낙폭 %가 표시되고 갱신되는지 확인.

### 9.6 운영 (지속)
- 9.2에서 고른 서버를 계속 켜둔다(VPS는 자동, 집 PC는 절전/종료 주의).
- 기능 변경 시: Claude Code로 코드 수정 → 새 APK 빌드 → 폰에 재설치(개인용은 자동 업데이트 없음).
- yfinance가 갑자기 막히면(소스 변경) Claude Code에 알려 `fetcher.py` 교체 또는 정식 API 전환을 진행.

---

## 부록 A. (선택) 서버 없는 단일 앱 구조
서버 운영이 부담이면, 백엔드 없이 **앱이 Yahoo 시세 API를 직접 호출**해 기기 내에서 낙폭을 계산하는 변형도 가능하다.
- 장점: 호스팅 비용·상시 구동 서버 불필요.
- 단점: Yahoo 비공식 엔드포인트 불안정성, 연산 로직이 앱에 들어가 유지보수·디버깅 부담 증가, intraday 보정 난이도.
- 개인용 한정으로 "서버를 없애는 것"이 최우선이면 이 구조를 채택할 수 있다. (기본 권장은 본문 2-Tier)
