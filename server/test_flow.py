"""가짜 OCR 화면(mock_screens.json)을 차례로 서버에 보내 안내가 맞는지 확인한다.

사용법 (서버를 켜 둔 상태에서 다른 터미널):
    python test_flow.py
실제 사진 없이도 프롬프트를 고치고 바로 결과를 비교할 수 있다.
"""

import json
from pathlib import Path

from fastapi.testclient import TestClient

from main import app

client = TestClient(app)
data = json.loads(Path(__file__).with_name("mock_screens.json").read_text(encoding="utf-8"))

history: list[str] = []
print(f"요구: {data['user_request']}\n")
for screen in data["screens"]:
    res = client.post("/guide", json={
        "user_request": data["user_request"],
        "history": history,
        "ocr_texts": screen["ocr_texts"],
    }).json()
    print(f"[{screen['name']}] → 목표: {res['target_text']} | 안내: {res['message']} "
          f"| 완료: {res['done']} ({res['source']})")
    if res["target_text"]:
        history.append(res["target_text"])
