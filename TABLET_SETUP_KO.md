# 레노버 Y700: Bybit Futures Analyzer 2.5 설치

이 ZIP은 APK가 아닌 소스코드입니다. Android APK 빌드 및 실제 Y700 실행은 아직 검증되지 않았습니다.

## 터치만으로 GitHub에 올리는 방법 (Codespaces 파일 업로드 메뉴 사용 안 함)

1. 태블릿에서 `bybit-futures-analyzer-v2.5-consensus-news-beta.zip` 다운로드.
2. GitHub 웹페이지에서 본인 `Bybit-Futures-Pro` 저장소로 이동. Codespaces가 아닌 GitHub 저장소의 `Code` 파일 목록 페이지입니다.
3. 파일 목록 상단 `Add file` → `Upload files` (버튼이 안 보이면 파일 목록 위 작은 `+` → Upload files)를 선택해 ZIP 파일 하나만 업로드하고 `Commit changes`로 저장.
4. 이미 실행 중인 Codespaces 탭으로 돌아와 아래 터미널에서 순서대로 실행:

```bash
cd /workspaces/Bybit-Futures-Pro
git pull
unzip -o bybit-futures-analyzer-v2.5-consensus-news-beta.zip
ls -la .github/workflows
ls app/src/main/java/com/btc/analyzer
```

5. 이어서 ZIP을 git에서 제거하고 소스 파일 저장:

```bash
git rm bybit-futures-analyzer-v2.5-consensus-news-beta.zip
git add .
git commit -m "Install Bybit Futures Analyzer 2.5"
git push
```

6. GitHub 저장소의 Actions → `Build Bybit Futures APK` → 성공한 실행 → Artifacts → `bybit-futures-analyzer-debug-apk` 다운로드. ZIP을 풀면 `app-debug.apk`가 있습니다.
7. `app-debug.apk`를 Y700에서 열어 설치. Android가 요청하면 이 파일을 여는 앱의 `알 수 없는 앱 설치` 권한을 승인합니다. 충돌하는 기존 버전은 기존 데이터 백업 후 제거해야 할 수 있습니다.

## 주의

- 터미널 경로가 다른 경우 `pwd` 및 `ls`로 현재 경로를 확인하세요.
- Actions 빌드 실패 시 로그를 확인해야 합니다. 파일이 준비됐다는 이유만으로 APK 실행을 보장할 수 없습니다.
- 자동 매매 기능이 아니며, 위험 알림은 정확한 예측이나 손절 주문을 보장하지 않습니다. 거래소에서 손절 주문을 따로 설정하세요.
- 앱이 백그라운드에 있어도 Android 제한으로 지속 감시가 중단될 수 있습니다.
