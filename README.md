# Bybit Futures Analyzer 2.1 beta — Smart Trade Plan

**Source project, not a prebuilt APK.** Android application package `com.btc.analyzer`.

Features:
- Bybit live ticker / 1-level book / 5m, 15m, 1h technical analysis (from v2 guardian beta).
- Search exchange-listed linear/inverse futures instruments.
- **New: chart-based trade plan** (select symbol, LONG/SHORT, interval, entry price, fee %): fetches 200 Bybit historical kline records, excludes the last unfinished candle, locates 5-bar confirmed pivot highs/lows, ATR(14), structural stop and first two chart-based targets, fee-adjusted R:R.
- **New: annotated price chart** with entry, support/resistance, TP1, TP2 and stop lines. Manual tap to recalculate; not streaming on this screen.
- **New: optional watch** price threshold crossing TP/SL via guardian foreground service, checking ~30 seconds, in addition to guardian's 3-timeframe trend alerts. Price check is based on last traded price, NOT mark price or order trigger, so exchange execution may differ.

Not implemented: broker account portfolio sync, auto orders, proven directional accuracy, multi-symbol active simultaneous guardians, advanced CVD/OI scanner and statistical backtest. The app cannot guarantee notifications, particularly on Android 15 background foreground-service limits. ALWAYS put a stop-loss order at the exchange; never rely on tablet alerts as loss protection.

**Data freshness:** main screen live ticker uses WebSocket; chart plan uses a snapshot of closed candles; guardian checks via periodic REST, not tick-by-tick. Internet and Bybit API access are necessary.

**How to build APK:** Upload the contents of this ZIP into the ROOT of a new GitHub repository (not an extra subfolder), commit and open Actions → `Build Bybit Futures APK`; after a successful run download `bybit-futures-analyzer-debug-apk` artifact and extract `app-debug.apk`. Alternatively use Codespaces terminal once ZIP is uploaded: `unzip -o bybit-futures-analyzer-v2.1-smart-plan.zip && git add . && git commit -m "Add Smart Trade Plan" && git push`. GitHub Actions will build debug APK. Builds have not been verified in this environment.

**Technical:** Gradle 8.11.1 via GitHub Actions, Java 17, Android SDK 35, OkHttp 4.12.0. No Bybit secret/API keys needed.

References: https://bybit-exchange.github.io/docs/v5/market/kline and https://bybit-exchange.github.io/docs/v5/market/tickers


## 2.2 메인 차트 및 24시간 변동률
- 메인 홈에 OHLC 캔들 + 거래량 자체 차트 표시 (최근 최대 80개 봉, 단순 스크롤 불가)
- 선택 주기: 1/3/5/15/30분, 1/2/4/6/12시간, 일/주/월
- 주기를 바꾸면 Bybit V5 kline 데이터 로드, 선택된 주기의 WebSocket kline 구독
- 선물 종목 검색시 변동률(현재 시각 기준 직전 24시간)을 양수/음수 색상으로 표시. 실시간 선택 종목 ticker 갱신.
- **바이비트 거래소 차트 화면을 복제한 것이 아님**: 그리기 도구, 줌, MA 오버레이, 차트 드래그, 체결량 전용 패널은 미구현
- 읽기 전용 분석 앱, 매매 실행 없음. 연결 오류 및 지역 API 제한 가능.

## 2.4 뉴스 모니터 베타 (이번 추가)
- 메인 오른쪽 패널: BTC 관련 최신 뉴스 제목 3건 미리보기. 약 5분 주기로 새로 조회하며 앱을 켰을 때도 조회합니다.
- 하단 `뉴스` 또는 `뉴스 전체 보기 · 원문`: Google News 검색 RSS(최근 2일, 영문 뉴스) 최대 30건, 기사 발행 시각·출처·원문 링크 및 수동 새로고침.
- 제목의 정해진 키워드 기반으로 `긍정 관련 키워드`, `위험 관련 키워드`, `거시경제 · 변동성 주의` 등을 표시합니다. **이는 AI 기사 내용 분석도, BTC 상승/하락 예측도, 실제 시장 반응 측정도 아닙니다.**
- 무료 RSS의 지연·가용성·검색 결과 누락 가능성이 있으며, API 연결이 차단되면 실패 문구를 표시합니다. 주요 뉴스 즉시 푸시, 경제 캘린더, 한국어 자동 번역, 뉴스-가격 영향 분석은 추후 개발 항목입니다.
- APK 검증 미완료: GitHub Actions 실제 빌드 및 레노버 Y700 실기기 실행 확인 필요.


## 2.5 다중 분석 합의 엔진 (추가)
- 1시간 추세, 15분 추세, 5분 MACD 모멘텀, 15분 RSI 강도, 5분 거래량 확인의 **5가지 독립 규칙**을 표시합니다.
- 4개 이상 일치, 반대 신호 0, 장단기 추세/거래량 동조가 있을 때만 조건부 LONG/SHORT 우위를 표시합니다. 나머지는 관망입니다.
- RSS의 최근 90분 거시경제 관련 헤드라인이 감지되면 보수적으로 `주요 뉴스 주의 · 관망` 처리합니다. 제목만 분류하므로 실제 가격 영향이나 중요도를 입증한 것은 아닙니다.
- 시세 스트림이 30초 넘게 지연되거나 계산 데이터가 부족할 경우 `관망` 처리합니다.
- **여러 LLM 서비스(GPT/Gemini 등)를 동시에 호출하는 AI 모델이 아닙니다. 과거 투자 대결 실적이나 우월한 승률을 주장하지 않습니다.**
- 아직 백테스트, 실전 승률 검증, AI 뉴스 본문 이해, 뉴스 후 가격반응 측정, 자동 주문, OI/CVD 정밀 분석은 구현하지 않았습니다.

### 뉴스 발표 시점 가격반응 (실험적)
- 최근 4시간 내 가장 최근 기사 1건의 **RSS 발행 시각**과 바이비트 BTCUSDT 1분봉을 연결합니다. 1분/5분/15분 후 완료 캔들의 종가와 기사 발행 시각 해당 봉 시가를 비교합니다.
- 발행 시각은 실제 최초 공개 시각 또는 시장에 정보가 알려진 순간과 다를 수 있습니다. 연결 실패, 미래 시점 또는 조회 범위 밖에서는 `대기/조회 범위 밖` 처리합니다.
- 이를 **뉴스 효과, AI 해석, 인과관계, 수익 확률로 해석하면 안 됩니다.**
