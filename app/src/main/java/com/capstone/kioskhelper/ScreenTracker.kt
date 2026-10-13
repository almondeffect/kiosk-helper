package com.capstone.kioskhelper

/**
 * 키오스크 화면이 바뀌었는지 판단한다.
 * 방법: 글자 목록을 '집합'으로 보고, 마지막으로 서버에 보낸 화면과 얼마나 겹치는지(자카드 유사도) 계산.
 *   유사도 = 공통 글자 수 / 전체 글자 수 (0~1)
 * 흔들림 때문에 한두 프레임만 달라지는 것을 무시하려고, 여러 프레임 연속으로 달라야 새 화면으로 본다.
 */
class ScreenTracker {

    private var lastSentScreen: Set<String> = emptySet()
    private var changedFrames = 0

    /** 새 화면이면 true. true를 받은 쪽은 서버에 물어본 뒤 markSent()를 불러야 한다. */
    fun isNewScreen(texts: List<String>): Boolean {
        val current = texts.map { normalize(it) }.filter { it.length >= 2 }.toSet()
        if (current.size < 3) return false        // 글자가 너무 적으면 키오스크를 안 비춘 것으로 봄

        if (lastSentScreen.isEmpty()) return true // 처음 비춘 화면

        val similarity = jaccard(current, lastSentScreen)
        changedFrames = if (similarity < Config.SCREEN_SIMILARITY_THRESHOLD) changedFrames + 1 else 0
        return changedFrames >= Config.STABLE_FRAMES
    }

    fun markSent(texts: List<String>) {
        lastSentScreen = texts.map { normalize(it) }.filter { it.length >= 2 }.toSet()
        changedFrames = 0
    }

    fun reset() {
        lastSentScreen = emptySet()
        changedFrames = 0
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        val union = (a union b).size
        return if (union == 0) 1.0 else (a intersect b).size.toDouble() / union
    }

    companion object {
        fun normalize(s: String) = s.replace("\\s".toRegex(), "")
    }
}
