package com.technavious.om15.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** What the engineer is filling in — passed to the model as reference context only (prompt Part B). */
data class ReadingRequest(
    val fieldLabel: String,
    val fieldUnit: String?,
    val site: String = "",
    val equipmentTag: String = "",
    val testName: String = ""
)

/**
 * Online meter transcription with Gemini, using the bundled system prompt and JSON schema
 * (assets/meter_reading_system_prompt.txt, assets/meter_reading_schema.json).
 */
class GeminiMeterReader(private val context: Context, private val apiKey: String) {

    fun extract(bitmap: Bitmap, request: ReadingRequest): MeterExtraction {
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt(context)))))
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray()
                .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", toBase64(bitmap))))
                .put(JSONObject().put("text", userMessage(request))))))
            .put("generationConfig", JSONObject()
                .put("responseMimeType", "application/json")
                .put("responseJsonSchema", responseSchema(context))
                .put("temperature", 0)
                // JSON mode can occasionally repeat array items until the token limit; cap it so a reply never takes minutes.
                .put("maxOutputTokens", 400)
                .put("thinkingConfig", JSONObject().put("thinkingLevel", "low")))
        return MeterExtraction.parse(callWithFallback(body.toString()))
    }

    /** Convenience for callers that only need the chosen value. */
    fun read(bitmap: Bitmap, request: ReadingRequest): Pair<MeterExtraction, ReadingChoice> {
        val x = extract(bitmap, request)
        return x to ReadingMatcher.choose(x, request.fieldLabel, request.fieldUnit)
    }

    private fun userMessage(r: ReadingRequest): String {
        val field = r.fieldLabel + (r.fieldUnit?.let { " ($it)" } ?: "")
        return "Transcribe the numeric readings in the attached image according to your instructions and the JSON schema.\n" +
            "Context (for reference only — do not use it to fill in values that are not visible): " +
            listOf(r.testName, field, r.equipmentTag.takeIf { it.isNotBlank() }?.let { "equipment $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun toBase64(bitmap: Bitmap): String {
        val scale = minOf(1f, UPLOAD_MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height))
        val sized = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        val stream = ByteArrayOutputStream()
        sized.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        if (sized !== bitmap) sized.recycle()
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Gemini's free tier regularly answers 503 "overloaded" (and 429 when rate-limited), often for several models at
     * once. Try the fast models first, back off briefly between rounds, and give up within [TOTAL_BUDGET_MS] so the
     * caller can fall back to the offline reader.
     */
    private fun callWithFallback(body: String): String {
        val deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS
        var lastStatus = ""
        for (round in 0 until ROUNDS) {
            for (model in MODELS) {
                if (System.currentTimeMillis() > deadline) break
                val start = System.currentTimeMillis()
                try {
                    return callModel(model, body).also { Log.i(TAG, "$model answered in ${System.currentTimeMillis() - start} ms") }
                } catch (e: RetryableException) {
                    Log.w(TAG, "$model failed after ${System.currentTimeMillis() - start} ms: ${e.message}")
                    lastStatus = e.status
                } catch (e: java.io.IOException) {
                    Log.w(TAG, "$model network error: ${e.javaClass.simpleName} ${e.message}")
                    lastStatus = if (e is java.net.UnknownHostException) "no internet" else "network timeout"
                }
            }
            if (round < ROUNDS - 1 && System.currentTimeMillis() + BACKOFF_MS[round] < deadline) Thread.sleep(BACKOFF_MS[round])
        }
        throw GeminiUnavailableException(when (lastStatus) {
            "503" -> "Online AI is busy (Google servers overloaded, error 503)"
            "429" -> "Online AI rate limit reached (error 429) — too many readings per minute on this API key"
            "no internet" -> "No internet connection"
            "" -> "Online AI did not respond"
            else -> "Online AI unavailable ($lastStatus)"
        })
    }

    private class RetryableException(val status: String, msg: String) : Exception(msg)

    /** Thrown when no Gemini model answered; the message is safe to show to engineers. */
    class GeminiUnavailableException(message: String) : Exception(message)

    private fun callModel(model: String, body: String): String {
        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("x-goog-api-key", apiKey)
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 25000
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        if (code != 200) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
            val msg = "Gemini $model error ($code): ${err.take(200)}"
            if (code == 404 || code == 429 || code >= 500) throw RetryableException(code.toString(), msg)
            throw Exception(msg)
        }
        return JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getString("text").trim()
    }

    companion object {
        private const val TAG = "GeminiMeterReader"
        private const val UPLOAD_MAX_SIDE = 1280
        // Measured on a cropped meter photo: the lite models answer in ~3 s with the same digits as the full models (~12 s).
        private val MODELS = listOf("gemini-3.5-flash-lite", "gemini-flash-lite-latest", "gemini-3.5-flash", "gemini-3.8-flash")
        private const val ROUNDS = 2
        private val BACKOFF_MS = longArrayOf(1500)
        private const val TOTAL_BUDGET_MS = 45_000L
        private val DEFAULT_API_KEY = com.technavious.om15.BuildConfig.GEMINI_API_KEY

        @Volatile private var cachedPrompt: String? = null
        @Volatile private var cachedSchema: String? = null

        private fun systemPrompt(context: Context): String = cachedPrompt
            ?: context.assets.open("meter_reading_system_prompt.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }.also { cachedPrompt = it }

        /** The schema file without its JSON-Schema meta keys, which Gemini's responseJsonSchema does not need. */
        private fun responseSchema(context: Context): JSONObject {
            val text = cachedSchema ?: context.assets.open("meter_reading_schema.json").bufferedReader(Charsets.UTF_8).use { it.readText() }.also { cachedSchema = it }
            return JSONObject(text).apply { remove("\$schema"); remove("title"); remove("description") }
        }

        fun getApiKey(context: Context): String {
            val saved = context.getSharedPreferences("om15_settings", Context.MODE_PRIVATE).getString("gemini_api_key", null)
            return if (!saved.isNullOrBlank()) saved else DEFAULT_API_KEY
        }

        fun saveApiKey(context: Context, key: String) {
            context.getSharedPreferences("om15_settings", Context.MODE_PRIVATE).edit().putString("gemini_api_key", key).apply()
        }

        fun useGemini(context: Context): Boolean =
            context.getSharedPreferences("om15_settings", Context.MODE_PRIVATE).getBoolean("use_gemini", true)

        fun setUseGemini(context: Context, use: Boolean) {
            context.getSharedPreferences("om15_settings", Context.MODE_PRIVATE).edit().putBoolean("use_gemini", use).apply()
        }
    }
}
