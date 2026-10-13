package com.capstone.kioskhelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * 카메라 화면 위에 박스를 그리는 투명한 뷰. (2D 오버레이, ARCore 미사용)
 * OCR 좌표는 '카메라 이미지 기준'이고 화면은 크기가 달라서, 좌표 변환이 필요하다.
 */
class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var target: Rect? = null
    private var allBoxes: List<Rect> = emptyList()
    private var imageW = 1
    private var imageH = 1

    var showAllBoxes = true   // 개발 중 디버깅용: OCR이 찾은 모든 글자를 얇게 표시

    private val targetPaint = Paint().apply {
        color = Color.parseColor("#FF3B30"); style = Paint.Style.STROKE; strokeWidth = 14f
    }
    private val debugPaint = Paint().apply {
        color = Color.parseColor("#8034C759"); style = Paint.Style.STROKE; strokeWidth = 3f
    }

    fun update(target: Rect?, all: List<Rect>, imageWidth: Int, imageHeight: Int) {
        this.target = target
        this.allBoxes = all
        this.imageW = imageWidth
        this.imageH = imageHeight
        postInvalidate()   // 다른 스레드에서 불려도 안전하게 다시 그리기 요청
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (showAllBoxes) allBoxes.forEach { canvas.drawRect(map(it), debugPaint) }
        target?.let { canvas.drawRoundRect(map(it, pad = 20f), 24f, 24f, targetPaint) }
    }

    /**
     * PreviewView 기본 설정(FILL_CENTER)은 이미지를 화면에 꽉 차게 확대하고 가운데를 맞춘다.
     * 같은 방식으로 확대 비율(scale)과 잘린 만큼의 이동(offset)을 계산해 좌표를 맞춘다.
     */
    private fun map(r: Rect, pad: Float = 0f): RectF {
        val scale = max(width.toFloat() / imageW, height.toFloat() / imageH)
        val offsetX = (width - imageW * scale) / 2f
        val offsetY = (height - imageH * scale) / 2f
        return RectF(
            r.left * scale + offsetX - pad,
            r.top * scale + offsetY - pad,
            r.right * scale + offsetX + pad,
            r.bottom * scale + offsetY + pad,
        )
    }
}
