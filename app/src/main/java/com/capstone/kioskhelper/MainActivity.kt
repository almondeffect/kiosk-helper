package com.capstone.kioskhelper

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.capstone.kioskhelper.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 전체 흐름을 연결하는 곳.
 *  매 프레임: OCR → 목표 글자 찾기 → 박스 그리기
 *  화면이 바뀌면: 서버에 '다음 목표 글자' 요청 → 안내 문장 표시 + 음성 출력
 */
class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val screenTracker = ScreenTracker()
    private var tts: TextToSpeech? = null

    // 진행 상태
    private var userRequest: String? = null        // null이면 아직 시작 전
    private val history = mutableListOf<String>()  // 지금까지 누른 버튼
    @Volatile private var targetText: String? = null
    @Volatile private var waitingServer = false
    private var lastTargetBox: Rect? = null
    private var lastSeenAt = 0L
    private var latestTexts: List<String> = emptyList()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else binding.guideText.text = "카메라 권한이 필요합니다"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.KOREAN
        }

        binding.startButton.setOnClickListener {
            val text = binding.requestInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            userRequest = text
            history.clear()
            targetText = null
            screenTracker.reset()   // 다음 프레임에서 첫 화면으로 보고 서버에 물어본다
            show("키오스크 화면을 비춰 주세요")
        }

        // 화면이 거의 안 바뀌는 단계(예: 같은 화면에서 ICE → 담기)를 위한 수동 '다음' 버튼
        binding.nextButton.setOnClickListener {
            targetText?.let { history.add(it) }
            targetText = null
            askServer(latestTexts)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) startCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            // KEEP_ONLY_LATEST: OCR이 느려도 밀린 프레임은 버리고 항상 최신 프레임만 처리
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analysisExecutor, OcrAnalyzer(::onOcrResult)) }

            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    /** 매 프레임 호출된다. */
    private fun onOcrResult(lines: List<OcrLine>, imageW: Int, imageH: Int) {
        val texts = lines.map { it.text }
        latestTexts = texts

        // 1) 목표 글자 찾기 (공백 무시, 부분 일치 허용)
        val now = System.currentTimeMillis()
        val target = targetText?.let { t ->
            val nt = ScreenTracker.normalize(t)
            lines.firstOrNull { ScreenTracker.normalize(it.text) == nt }
                ?: lines.firstOrNull { ScreenTracker.normalize(it.text).contains(nt) }
        }
        if (target != null) { lastTargetBox = target.box; lastSeenAt = now }
        // 잠깐 놓쳐도 0.5초 동안은 이전 박스를 유지 (깜빡임 방지)
        val boxToDraw = target?.box ?: lastTargetBox?.takeIf { now - lastSeenAt < Config.BOX_HOLD_MS }
        binding.overlayView.update(boxToDraw, lines.map { it.box }, imageW, imageH)

        // 2) 화면이 바뀌었으면 서버에 다음 목표를 물어본다
        if (userRequest != null && !waitingServer && screenTracker.isNewScreen(texts)) {
            targetText?.let { history.add(it) }   // 화면이 바뀌었다 = 이전 목표를 눌렀다
            askServer(texts)
        }
    }

    private fun askServer(texts: List<String>) {
        val request = userRequest ?: return
        if (texts.isEmpty()) return
        waitingServer = true
        screenTracker.markSent(texts)
        runOnUiThread { binding.guideText.text = "확인하는 중..." }

        lifecycleScope.launch {
            try {
                val result = GuideApi.ask(request, history.toList(), texts)
                targetText = result.targetText
                show(result.message)
                if (result.done) userRequest = null   // 결제 단계: 안내 종료
            } catch (e: Exception) {
                Log.e("KioskHelper", "서버 호출 실패", e)
                show("서버에 연결할 수 없어요")
            } finally {
                waitingServer = false
            }
        }
    }

    private fun show(message: String) {
        runOnUiThread { binding.guideText.text = message }
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "guide")
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
        tts?.shutdown()
    }
}
