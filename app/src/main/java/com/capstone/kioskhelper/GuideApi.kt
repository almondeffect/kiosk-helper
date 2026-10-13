package com.capstone.kioskhelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GuideResult(val targetText: String?, val message: String, val done: Boolean)

/**
 * 서버의 /guide 를 호출한다. (= '느린 처리', 화면이 바뀔 때만)
 * 보내는 것: 사용자 요구, 지금까지 누른 버튼, 현재 화면 글자 목록 → 이미지는 보내지 않는다.
 */
object GuideApi {
    private val client = OkHttpClient.Builder()
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun ask(userRequest: String, history: List<String>, ocrTexts: List<String>): GuideResult =
        withContext(Dispatchers.IO) {   // 네트워크는 화면 스레드에서 하면 안 되므로 IO 스레드에서
            val body = JSONObject()
                .put("user_request", userRequest)
                .put("history", JSONArray(history))
                .put("ocr_texts", JSONArray(ocrTexts))
                .toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder().url("${Config.SERVER_URL}/guide").post(body).build()
            client.newCall(request).execute().use { resp ->
                val json = JSONObject(resp.body!!.string())
                GuideResult(
                    targetText = if (json.isNull("target_text")) null else json.getString("target_text"),
                    message = json.optString("message", ""),
                    done = json.optBoolean("done", false),
                )
            }
        }
}
