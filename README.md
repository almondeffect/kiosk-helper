# 키오스크 도우미

키오스크 사용이 어려운 분을 위해, 휴대폰 카메라로 키오스크를 비추면 눌러야 할 버튼을 실시간으로 표시하고 음성으로 안내하는 Android 앱입니다.

## 구조

```
[휴대폰]  카메라 프레임 → ML Kit OCR(매 프레임) → 목표 글자 찾기 → 박스 표시
                              │ 화면이 바뀌었을 때만
                              ▼
[노트북 서버]  OCR 글자 목록 + 요구 + 진행 상태 → LLM → "다음 목표 글자" + 안내 문장
```

| 폴더/파일 | 역할 |
|---|---|
| `server/main.py` | FastAPI 서버. `/guide`로 다음 목표 글자를 돌려줌. API 키가 없으면 가짜 LLM(mock)으로 동작 |
| `server/prompt.py` | LLM 프롬프트 (안내 품질은 주로 여기서 고침) |
| `server/mock_screens.json` | 카페 키오스크 5개 화면의 가짜 OCR 데이터 |
| `server/test_flow.py` | 가짜 화면을 차례로 보내 안내 결과를 출력 |
| `app/.../OcrAnalyzer.kt` | 카메라 프레임 → 글자와 위치 |
| `app/.../OverlayView.kt` | 카메라 화면 위에 박스 그리기 (이미지 좌표 → 화면 좌표 변환) |
| `app/.../ScreenTracker.kt` | 글자 목록 유사도로 화면 전환 감지 |
| `app/.../GuideApi.kt` | 서버 호출 |
| `app/.../MainActivity.kt` | 전체 흐름 연결, 음성 안내(TTS) |
| `app/.../Config.kt` | 서버 주소, 화면 전환 기준값 등 설정 |

## 서버 실행

```
cd server
python -m venv .venv
.venv\Scripts\activate          # macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
copy .env.example .env          # macOS/Linux: cp .env.example .env  → 키 입력 (비워 두면 mock 모드)
python test_flow.py             # 서버 없이 안내 흐름 확인
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

브라우저에서 `http://localhost:8000/docs` 를 열면 `/guide` 를 직접 시험해 볼 수 있습니다.

## 앱 실행

1. Android Studio에서 `kiosk-helper` 폴더(이 README가 있는 폴더)를 엽니다. Gradle 동기화가 끝날 때까지 기다립니다.
2. `Config.kt`의 `SERVER_URL`을 노트북 IP로 바꿉니다.
3. 폰을 USB로 연결하고 ▶ 실행.
4. 주문을 입력하고 [시작] → 키오스크(또는 모니터에 띄운 키오스크 사진)를 비춥니다.

초록 얇은 박스는 OCR이 찾은 모든 글자(디버깅용, `OverlayView.showAllBoxes`), 빨간 굵은 박스가 눌러야 할 버튼입니다.

## 알려진 한계 (다음 작업)

- 같은 화면 안에서 진행하는 단계(예: ICE 선택 → 담기)는 화면 변화가 작아 자동 감지가 안 될 수 있음 → 지금은 [눌렀어요] 버튼으로 다음 단계 진행
- 음성 입력 미구현 (텍스트 입력만)
- 글자 없는 아이콘 버튼은 찾지 못함
- 화면 전환 기준값(`SCREEN_SIMILARITY_THRESHOLD`, `STABLE_FRAMES`)은 실제 키오스크로 조정 필요
