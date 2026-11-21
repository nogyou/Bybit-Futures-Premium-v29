# Bybit Futures Analyzer v2.6 Premium Beta

v2.5 원본 소스에 기반한 안드로이드 프로젝트입니다.

## 변경사항
- 프리미엄 다크 네이비/퍼플 그라데이션 카드 및 버튼, 제목 v2.6
- 종목 선택창 정렬: 24h 등락률 오름/내림, 거래대금 오름/내림, 거래량, 24h 고저 변동폭, 이름 오름/내림
- 종목별 24시간 거래대금 표시 (Bybit tickers API)
- 롱/숏 점수 상세 버튼: 5분/15분/1시간봉의 EMA/RSI/MACD 가중치 및 점수 상세
- 기존 차트, 뉴스, 포지션 감시, TP/SL 유지

## 설치
1. ZIP 압축을 **풀고**, 내부의 `.github`, `app`, Gradle 설정 파일을 GitHub 저장소 최상위에 업로드하세요. ZIP 파일 자체만 업로드하면 Actions가 소스 코드를 인식하지 못합니다.
2. GitHub Actions > Build Bybit Futures APK 워크플로 실행 또는 main 브랜치 커밋으로 자동 실행.
3. 녹색 성공 표시의 실행 상세 > Artifacts > APK ZIP 다운로드 후 압축 해제.
4. `app-debug.apk`를 태블릿에서 설치. 기존 앱과 동일한 applicationId이며 versionCode가 더 높아 업데이트 설치가 가능합니다.

참고: 시가총액은 Bybit 선물 tickers API가 직접 제공하지 않아 정렬 대상에서 제외했습니다. 등락폭은 24h 고가/저가 기반 상대 폭입니다. 실시간 API 접근/빌드는 GitHub Actions에서 확인해야 합니다. 매매 판단 및 수익을 보장하지 않습니다.
