# US ETF Monitor — 미국 지수 고점 대비 낙폭 위젯

**원하는 미국 지수·ETF·주식(1~4개)의 최근 1개월 최고점 대비 현재 낙폭(%)**을 갤럭시 홈 화면 위젯에 표시하는 개인용 안드로이드 앱입니다. (현재 버전 **v2.1**)

> 매수 신호 같은 투자 판단 기능은 없습니다. **"고점 대비 지금 몇 % 하락했는지" 숫자만** 보여줍니다.

---

## 무엇을 보여주나

### 위젯
- **추적 종목 1~4개**(기본: 나스닥 100 `^NDX`, S&P 500 `^GSPC`) 각각에 대해
- **최근 1개월 고점 대비 현재 낙폭(%)** 을 크게 표시 + 현재가
- **1개월 종가 라인 차트**: 고점 점선 + 고점 가격 라벨 + 면 채움(과거→현재 투명도 그라데이션) + 현재가 점
  - 차트는 위젯 높이가 충분할 때 표시(갤럭시 기본 4×2 크기에서 표시됨). 종목 4개로 차트가 너무 낮아지면 고점 라벨은 자동 생략
- 낙폭 구간 색상을 텍스트·차트 라인·면에 공통 적용: **0%(고점 갱신 중) 파랑 / ~−5% 노랑 / −5%~ 주황 / −10%~ 적색** — *정보 강조용이며 매수 신호 아님*
- 헤더에 갱신 시각 · 개장/폐장 · 갱신 중/갱신 실패/지연(개장 중 2시간 초과) 상태 표시

### 앱 화면
- **종목 검색·추가·삭제** (야후 검색 API) — 추가 시 야후가 과거 일봉을 제공하는 종목인지 **자동 검증**(3개월 일봉 20개 미만이면 차단, 예: `^DJUSDIV`)
- 카드형 UI, 시스템 테마 연동(라이트=흰 배경 / 다크=검정 배경), 주황 포인트 색
- 최근 캐시값 확인 + 수동 갱신 버튼

기준이 "기간 내 고점"이라 현재가는 항상 고점 이하이므로, 결과는 **0%(고점 갱신 중) 또는 음수(하락)** 만 나옵니다.

---

## 구조 (서버리스 단일 앱)

서버 없이 **앱이 야후 파이낸스 차트 API를 직접 호출**하고, 기기 내에서 낙폭을 계산합니다.

```
[Yahoo Finance Chart/Search API]
   │  앱이 WorkManager로 주기 호출 (chart: range=3mo&interval=1d / search: 종목 검색 시)
   ▼
[Android App]
   ├─ JSON 파싱(현재가 + 일별 고가·종가)
   ├─ 1개월 고점 추출 + 낙폭 계산, 1개월 종가 시계열(차트용)
   ├─ 추적 종목 목록 관리(검색·검증·저장)
   └─ SharedPreferences 캐싱
   ▼
[홈 화면 위젯 (RemoteViews 4슬롯, 크기에 따라 텍스트/차트 레이아웃)]
```

- 호스팅·상시 구동 서버·비용 불필요 (개인 1인 사용 전제)
- 호출량: 종목당 3개월치 1회 = 갱신당 최대 4회. 30분 주기 자동 갱신 + 위젯 새로고침 버튼
- **폐장 중에는 호출 생략**: 마지막 폐장 이후 데이터를 이미 갖고 있으면 네트워크 호출을 건너뜀(배터리/API 절약). 수동 새로고침은 항상 실제 갱신
- 휴장일은 연도 하드코딩 없이 규칙으로 계산(MLK·성금요일·추수감사절 등 + 조기 폐장 13:00 반영)
- 위젯 헤더에 상태 표시: 갱신 시각 · 개장/폐장 · 갱신 중/갱신 실패/지연(개장 중 2시간 초과) 경고

### 디렉토리

```
us_etf_monitor/
└─ android/        # 실제 빌드/배포 대상 (Kotlin)
   └─ app/src/
      ├─ main/java/com/example/etfdrawdown/
      │  ├─ MainActivity  # 종목 검색·추가·삭제, 최근값, 수동 갱신
      │  ├─ data/     # YahooClient(차트·검색), Drawdown, MarketHours, Repository, PrefsStore
      │  ├─ widget/   # EtfWidgetProvider, WidgetRenderer(4슬롯), ChartRenderer
      │  └─ work/     # UpdateWorker (WorkManager)
      └─ test/        # MarketHours 단위 테스트(서머타임·휴장일·조기폐장)
```

---

## 기술 스택

| 항목 | 사용 |
| :--- | :--- |
| 언어 | Kotlin |
| 위젯 | RemoteViews + AppWidgetProvider (Compose/Glance 미사용) |
| 백그라운드 | WorkManager (CoroutineWorker, 30분 주기) |
| 네트워크 | 내장 `HttpURLConnection` |
| JSON | 내장 `org.json` |
| 저장 | `SharedPreferences` |
| 빌드 | AGP 9.2.1 / Gradle 9.4.1 / compileSdk 36 / minSdk 26 / JDK 21 |

추가 의존성은 `androidx.work:work-runtime-ktx` 하나뿐입니다.

---

## 빌드

### 사전 요구사항
- **Android Studio** (SDK 포함) 또는 Android SDK + JDK 21
- **Windows**: [Visual C++ 재배포 패키지(x64)](https://aka.ms/vs/17/release/vc_redist.x64.exe) — 없으면 AAPT2 빌드가 실패합니다.

### 명령
```bash
cd android
./gradlew assembleDebug      # 디버그: app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # 릴리스: app/build/outputs/apk/release/app-release.apk
```

(Windows PowerShell: `.\gradlew.bat assembleDebug`)

> 릴리스 서명 정보는 gitignore된 `android/keystore.properties`에서 읽습니다(`storeFile`/`storePassword`/`keyAlias`/`keyPassword`).

---

## 폰에 설치

1. APK를 갤럭시로 전송하거나, USB 디버깅으로 `adb install -r` 사용
2. 설치 후 설정 → 애플리케이션 → ETF Drawdown에서 **버전이 최신인지 확인** (구버전 파일 재설치 사고 방지)
3. 홈 화면을 길게 눌러 위젯 목록에서 **"1M 고점 대비 낙폭"** 추가
4. 위젯은 30분마다 자동 갱신, 새로고침 버튼으로 즉시 갱신. 안정적 백그라운드 갱신을 위해 설정 → 배터리에서 이 앱을 "제한 없음"으로 두는 것을 권장

> 최근 갤럭시(One UI 6.1+)는 **자동 차단(Auto Blocker)** 기능이 사이드로딩을 막을 수 있습니다. 설치가 안 되면 USB 디버깅(adb) 설치 또는 Auto Blocker 해제를 사용하세요.

---

## 면책

표시되는 낙폭은 약 15분 지연된 야후 파이낸스 시세 기반의 **참고 정보**이며, 투자 판단의 근거가 아닙니다. 야후의 비공식 엔드포인트는 예고 없이 변경·중단될 수 있습니다.
