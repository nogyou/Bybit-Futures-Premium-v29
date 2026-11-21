# Bybit Futures Premium v2.7 — 하이브리드 설계 및 상태

## 실제 구현 상태
- v2.6 기존 Android 앱 소스 보존, 버전명 v2.7 베타로 변경.
- GitHub Actions 워크플로를 정상 YAML 구조로 교체하고 Android SDK 설치 단계를 정리함.
- `HybridDecisionEngine.java` 추가: 기술 점수와 외부 AI 의견의 일치 여부, WAIT 안전 차단, 손절폭 기반 최대 포지션 명목가 계산.
- **중요: AI 모델/API 연결, 기술 점수 산출 연결, v2.7 신규 UI 연결은 아직 구현되지 않았음.** 기존 화면은 v2.6 기반임.
- AI 응답이 없으면 WAIT. AI 판단을 임의로 생성하거나 기술 지표를 AI로 부르지 않음.
- 75점은 성공 확률이 아닌 임의 설계 임계값. 모의거래와 백테스트 전 실거래 사용 금지.

## 다음 구현 단계
1. 기술 지표 점수 계산을 기존 ConsensusEngine에 연결하고, 실시간 데이터 신선도 검증.
2. 사용자 동의 하에 별도 AI 제공자 API를 연결하되 API 키를 APK에 하드코딩하지 않고 서버에 보관.
3. 양측 판단과 WAIT 사유를 화면에 표시.
4. ATR 기반 손절/익절, 수수료·슬리피지·일일 손실 제한, 포지션 관리.
5. 과거 데이터 백테스트/워크포워드/모의거래 및 GitHub Actions 실제 빌드 검증.

## 업로드 안내
새 빈 GitHub 저장소에 ZIP **압축을 풀어** 내부 파일 및 폴더를 저장소 루트에 올리세요. 기존 README 초기 커밋과 병합할 때 충돌을 피하려면 웹 UI에서 새 파일을 직접 업로드하거나 새 저장소를 빈 상태로 만드세요.
GitHub Actions > Build Bybit Futures APK > Run workflow 또는 main push 후 빌드 결과 확인. 성공 시 Artifacts의 debug APK 다운로드.
