package com.capstone.kioskhelper

/** 바뀔 수 있는 설정값을 한 곳에 모아 둔다. */
object Config {
    // 노트북의 내부 IP로 바꾸세요. (Windows: ipconfig, macOS: ifconfig 로 확인)
    const val SERVER_URL = "http://192.168.0.10:8000"

    // 이전 화면과 글자가 이 비율 미만으로 겹치면 '새 화면'으로 판단 (실험으로 조정)
    const val SCREEN_SIMILARITY_THRESHOLD = 0.4

    // 새 화면 판단 전, 몇 프레임 연속으로 같은 결과가 나와야 하는지 (흔들림 방지)
    const val STABLE_FRAMES = 5

    // 목표 글자를 잠깐 놓쳐도 박스를 유지하는 시간 (깜빡임 방지)
    const val BOX_HOLD_MS = 500L
}
