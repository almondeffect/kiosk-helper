"""키오스크 도우미 서버.

앱이 OCR 글자 목록을 보내면, LLM에게 '다음에 누를 버튼 글자'를 물어보고 돌려준다.
- API 키가 .env에 있으면: 실제 Claude API 호출
- API 키가 없으면: 간단한 규칙 기반 '가짜 LLM'(mock)으로 응답 → 키 없이도 앱 연결 테스트 가능
"""

import json
import os
import re

from dotenv import load_dotenv
from fastapi import FastAPI
from pydantic import BaseModel

from prompt import SYSTEM_PROMPT, build_user_prompt

load_dotenv()

API_KEY = os.getenv("ANTHROPIC_API_KEY")
# 빠른 응답이 중요해서 작은 모델을 기본값으로 둠. .env에서 바꿀 수 있음.
MODEL = os.getenv("CLAUDE_MODEL", "claude-haiku-5-5")

app = FastAPI(title="키오스크 도우미 서버")


class GuideRequest(BaseModel):
    user_request: str          # 예: "아이스 아메리카노 한 잔 포장"
    history: list[str] = []    # 지금까지 누른 버튼 글자들 (진행 상태)
    ocr_texts: list[str]       # 현재 화면에서 OCR이 읽은 글자 목록


class GuideResponse(BaseModel):
    target_text: str | None    # 다음에 누를 버튼 글자 (없으면 null)
    message: str               # 음성 안내 문장
    done: bool = False         # 결제 단계 도달 여부
    source: str                # "llm" 또는 "mock" (발표·디버깅용)


@app.get("/health")
def health():
    return {"ok": True, "mode": "llm" if API_KEY else "mock", "model": MODEL}


@app.post("/guide", response_model=GuideResponse)
def guide(req: GuideRequest):
    if API_KEY:
        return ask_llm(req)
    return ask_mock(req)


def ask_llm(req: GuideRequest) -> GuideResponse:
    import anthropic

    client = anthropic.Anthropic(api_key=API_KEY)
    resp = client.messages.create(
        model=MODEL,
        max_tokens=300,
        system=SYSTEM_PROMPT,
        messages=[{"role": "user", "content": build_user_prompt(
            req.user_request, req.history, req.ocr_texts)}],
    )
    text = resp.content[0].text
    data = parse_json(text)

    target = data.get("target_text")
    # LLM이 목록에 없는 글자를 지어내면 앱이 찾을 수 없으므로 검증한다.
    if target and target not in req.ocr_texts:
        target = closest_ocr_text(target, req.ocr_texts)

    return GuideResponse(
        target_text=target,
        message=data.get("message", "화면을 다시 비춰 주세요."),
        done=bool(data.get("done", False)),
        source="llm",
    )


def parse_json(text: str) -> dict:
    """LLM 답에 설명이 섞여 와도 { ... } 부분만 꺼내서 읽는다."""
    match = re.search(r"\{.*\}", text, re.DOTALL)
    if not match:
        return {}
    try:
        return json.loads(match.group(0))
    except json.JSONDecodeError:
        return {}


def normalize(s: str) -> str:
    return re.sub(r"\s+", "", s)


def closest_ocr_text(target: str, ocr_texts: list[str]) -> str | None:
    """공백 차이나 부분 일치까지 허용해서 OCR 목록에서 가장 비슷한 글자를 찾는다."""
    t = normalize(target)
    for o in ocr_texts:
        if normalize(o) == t:
            return o
    for o in ocr_texts:
        if t in normalize(o) or normalize(o) in t:
            return o
    return None


# ---------------- 가짜 LLM (API 키 없이 테스트용) ----------------

PAYMENT_WORDS = ["결제하기", "카드결제", "카드 결제", "결제"]
FLOW_KEYWORDS = ["아메리카노", "라떼", "ICE", "아이스", "HOT", "포장", "테이크아웃",
                 "매장", "담기", "확인", "주문하기", "다음"]


def ask_mock(req: GuideRequest) -> GuideResponse:
    """요구 문장과 OCR 글자를 단순 비교하는 규칙. 실제 LLM 대신 연결 테스트용."""
    request = normalize(req.user_request)
    done_set = {normalize(h) for h in req.history}

    for o in req.ocr_texts:
        if any(normalize(p) == normalize(o) for p in PAYMENT_WORDS):
            return GuideResponse(target_text=None, done=True, source="mock",
                                 message="결제 화면입니다. 카드를 넣어 직접 결제해 주세요.")

    # 1순위: 사용자 요구에 들어 있는 글자, 2순위: 진행용 버튼(담기, 주문하기 등)
    candidates = [o for o in req.ocr_texts if normalize(o) not in done_set]
    for o in candidates:
        n = normalize(o)
        if len(n) >= 2 and (n in request or (n == "ICE" and "아이스" in request)):
            return GuideResponse(target_text=o, source="mock",
                                 message=f"'{o}' 버튼을 눌러 주세요.")
    for o in candidates:
        if any(normalize(k) == normalize(o) for k in ["담기", "확인", "주문하기", "다음"]):
            return GuideResponse(target_text=o, source="mock",
                                 message=f"'{o}' 버튼을 눌러 주세요.")

    return GuideResponse(target_text=None, source="mock",
                         message="눌러야 할 버튼이 보이지 않아요. 화면을 다시 비춰 주세요.")
