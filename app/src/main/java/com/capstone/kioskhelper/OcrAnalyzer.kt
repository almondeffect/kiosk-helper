package com.capstone.kioskhelper

import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions

/** OCR로 찾은 글자 한 줄과 그 위치 */
data class OcrLine(val text: String, val box: Rect)

/**
 * 카메라 프레임을 하나씩 받아 ML Kit로 글자를 읽는다. (= '빠른 처리', 기기 안에서 매 프레임)
 * 결과는 onResult로 넘기고, 화면 크기 변환에 필요한 이미지 크기도 함께 보낸다.
 */
class OcrAnalyzer(
    private val onResult: (lines: List<OcrLine>, imageWidth: Int, imageHeight: Int) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(mediaImage, rotation)

        // 세로 화면에서는 이미지가 90도 돌아가 있어서 가로/세로를 바꿔 준다.
        val (w, h) = if (rotation == 90 || rotation == 270)
            imageProxy.height to imageProxy.width else imageProxy.width to imageProxy.height

        recognizer.process(input)
            .addOnSuccessListener { result ->
                // 버튼 글자는 보통 한 줄이라 '줄(line)' 단위로 사용한다.
                val lines = result.textBlocks.flatMap { it.lines }
                    .mapNotNull { line -> line.boundingBox?.let { OcrLine(line.text, it) } }
                onResult(lines, w, h)
            }
            // close()를 해야 다음 프레임이 들어온다. 안 하면 카메라 분석이 멈춘다.
            .addOnCompleteListener { imageProxy.close() }
    }
}
