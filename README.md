# US ETF Monitor — 미국 지수 고점 대비 낙폭 위젯

나스닥 100(`^NDX`)·S&P 500(`^GSPC`)의 **설정 기간 내 최고점 대비 현재 낙폭(%)**을 갤럭시 홈 화면 위젯에 표시하는 개인용 안드로이드 앱입니다.

> 매수 신호 같은 투자 판단 기능은 없습니다. **"고점 대비 지금 몇 % 하락했는지" 숫자만** 보여줍니다.

---

## 무엇을 보여주나

- **나스닥 100 / S&P 500 / DJ 배당 100(^DJUSDIV)** 각각에 대해
- **최근 1개월 고점 대비 현재 낙폭(%)** 을 크게 표시
- 위젯을 세로로 늘리면(4셀 이상) **1개월 종가 라인 차트** 표시 (고점 점선 + 면 채움 + 현재가 점)
- 낙폭 구간 색상을 텍스트·차트에 공통 적용 (0% 고점 갱신 중 파랑 / ~−5% 노랑 / −5%~ 주황 / −10%~ 적색) — *정보 강조용이며 매수 신호 아님*

기준이 "기간 내 고점"이라 현재가는 항상 고점 이하이므로, 결과는 **0%(고점 갱신 중) 또는 음수(하락)** 만 나옵니다.

---

## 구조 (서버리스 단일 앱)

서버 없이 **앱이 야후 파이낸스 차트 API를 직접 호출**하고, 기기 내에서 낙폭을 계산합니다.

```
[Yahoo Finance Chart API]
   │  앱이 WorkManager로 주기 호출 (range=3mo&interval=1d)
   ▼
[Android App]
   ├─ JSON 파싱(현재가 + 일별 고가·종가)
   ├─ 1개월 고점 추출 + 낙폭 계산, 1개월 종가 시계열(차트용)
   └─ SharedPreferences 캐싱
   ▼
[홈 화면 위젯 (RemoteViews, 크기에 따라 텍스트/차트 레이아웃)]
```

- 호스팅·상시 구동 서버·비용 불필요 (개인 1인 사용 전제)
- 호출량: 지수당 3개월치 1회 = 갱신당 2회. 30분 주기 자동 갱신 + 위젯 새로고침 버튼
- **폐장 중에는 호출 생략**: 마지막 폐장 이후 데이터를 이미 갖고 있으면 네트워크 호출을 건너뜀(배터리/API 절약). 수동 새로고침은 항상 실제 갱신
- 휴장일은 연도 하드코딩 없이 규칙으로 계산(MLK·성금요일·추수감사절 등 + 조기 폐장 13:00 반영)
- 위젯 헤더에 상태 표시: 갱신 시각 · 개장/폐장 · 갱신 중/갱신 실패/지연(개장 중 2시간 초과) 경고

### 디렉토리

```
us_etf_monitor/
├─ android/        # 실제 빌드/배포 대상 (Kotlin)
│  └─ app/src/
│     ├─ main/java/com/example/etfdrawdown/
│     │  ├─ data/     # YahooClient, Drawdown, MarketHours, Repository, PrefsStore
│     │  ├─ widget/   # EtfWidgetProvider, WidgetRenderer, ChartRenderer
│     │  └─ work/     # UpdateWorker (WorkManager)
│     └─ test/        # MarketHours 단위 테스트(서머타임·휴장일·조기폐장)
└─ 금융지수_낙폭_위젯_개발명세서_v3_personal.md   # 개발 명세서
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
./gradlew assembleDebug
# 산출물: app/build/outputs/apk/debug/app-debug.apk
```

(Windows PowerShell: `.\gradlew.bat assembleDebug`)

---

## 폰에 설치

1. APK(`app-debug.apk`)를 갤럭시로 전송하거나, USB 디버깅으로 `adb install` 사용
2. 홈 화면을 길게 눌러 위젯 목록에서 **"고점 대비 낙폭"** 추가
3. 위젯은 30분마다 자동 갱신, 새로고침 버튼으로 즉시 갱신

> 최근 갤럭시(One UI 6.1+)는 **자동 차단(Auto Blocker)** 기능이 사이드로딩을 막을 수 있습니다. 설치가 안 되면 USB 디버깅(adb) 설치 또는 Auto Blocker 해제를 사용하세요.

---

## 면책

표시되는 낙폭은 약 15분 지연된 야후 파이낸스 시세 기반의 **참고 정보**이며, 투자 판단의 근거가 아닙니다. 야후의 비공식 엔드포인트는 예고 없이 변경·중단될 수 있습니다.
