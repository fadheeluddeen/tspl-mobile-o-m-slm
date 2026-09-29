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

class GeminiMeterReader(private val apiKey: String) {

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val scale = minOf(1f, UPLOAD_MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height))
        val sized = if (scale < 1f)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        else bitmap
        val stream = ByteArrayOutputStream()
        sized.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        if (sized !== bitmap) sized.recycle()
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    // Newest Gemini models frequently return 503 under load, so fall through a list.
    private fun callGemini(bitmap: Bitmap, prompt: String): String {
        val b64 = bitmapToBase64(bitmap)
        var lastError: Exception? = null
        for (model in MODELS) {
            val start = System.currentTimeMillis()
            try {
                val text = callModel(model, b64, prompt)
                Log.i(TAG, "$model answered in ${System.currentTimeMillis() - start} ms: $text")
                return text
            } catch (e: RetryableException) {
                Log.w(TAG, "$model failed after ${System.currentTimeMillis() - start} ms: ${e.message}")
                lastError = e
            } catch (e: java.net.SocketTimeoutException) {
                Log.w(TAG, "$model timed out")
                lastError = e
            }
        }
        throw lastError ?: Exception("No Gemini model available")
    }

    private class RetryableException(msg: String) : Exception(msg)

    private fun callModel(model: String, b64: String, prompt: String): String {
        val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

        val inlineData = JSONObject().apply {
            put("mime_type", "image/jpeg")
            put("data", b64)
        }
        val imagePart = JSONObject().put("inline_data", inlineData)
        val textPart = JSONObject().put("text", prompt)

        val parts = JSONArray().apply {
            put(imagePart)
            put(textPart)
        }
        val content = JSONObject().put("parts", parts)
        val contents = JSONArray().put(content)
        val body = JSONObject().put("contents", contents)
            .put("generationConfig", JSONObject()
                .put("thinkingConfig", JSONObject().put("thinkingLevel", "low")))

        val conn = URL(baseUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-goog-api-key", apiKey)
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 40000

        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        if (code != 200) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
            val msg = "Gemini $model error ($code): ${err.take(200)}"
            if (code == 404 || code == 429 || code >= 500) throw RetryableException(msg)
            throw Exception(msg)
        }

        val resp = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(resp)
        return json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
    }

    suspend fun readMeterImage(bitmap: Bitmap, fieldLabel: String, unit: String?): String {
        val prompt = buildPrompt(fieldLabel, unit)
        val result = callGemini(bitmap, prompt)
        return parseReading(result)
    }

    suspend fun readVibrationImage(bitmap: Bitmap, axisLabel: String?): Map<String, String> {
        val prompt = if (axisLabel != null) {
            """Task: Read the numeric value from an industrial vibration analyzer LCD screen.

This is a photo of a vibration meter used in power plant O&M. The screen shows the $axisLabel axis reading in mm/s RMS.

Step-by-step:
1. Locate the LCD/LED numeric display area on the instrument.
2. Read each digit left to right. LCD segment digits: 0-9.
3. DECIMAL POINT — this is the most critical step. LCD decimal points are tiny dots sitting at the bottom-right of a digit, between two digit positions. Zoom into the space between every pair of adjacent digits. If there is a small illuminated dot, that is the decimal point.
4. Typical vibration readings range from 0.01 to 45.00 mm/s. A 4-digit display reading "1999" almost always means 19.99 mm/s, not 1999 mm/s.
5. If the display shows units (mm/s, in/s, g), note them but return only the number.

Output: ONLY the numeric value with the decimal point in the correct position. No units, no text, no explanation.
Example: 2.45"""
        } else {
            """Task: Read vibration readings from an industrial vibration analyzer LCD screen.

This is a photo of a vibration meter used in power plant O&M. The screen may show readings for three measurement axes: vertical (V), horizontal (H), and axial (A). Values are in mm/s RMS.

Step-by-step for EACH reading:
1. Locate each numeric display on the screen.
2. Read each digit left to right. LCD segment digits: 0-9.
3. DECIMAL POINT — critical step. LCD decimal points are tiny dots at the bottom-right of a digit, between two digit positions. Check the space between every pair of adjacent digits carefully.
4. Typical vibration readings range from 0.01 to 45.00 mm/s. "1999" on display = 19.99 mm/s.
5. Match each reading to its axis label (V/Vertical, H/Horizontal, A/Axial).

Output: ONLY three comma-separated numbers: vertical,horizontal,axial
Example: 2.45,1.23,0.89"""
        }

        val text = callGemini(bitmap, prompt)
        return if (axisLabel != null) {
            mapOf(axisLabel to parseReading(text))
        } else {
            val parts = text.split(",").map { it.trim() }
            mapOf(
                "vertical" to parseReading(parts.getOrElse(0) { "" }),
                "horizontal" to parseReading(parts.getOrElse(1) { "" }),
                "axial" to parseReading(parts.getOrElse(2) { "" })
            )
        }
    }

    suspend fun readThermographyImage(bitmap: Bitmap): Map<String, String> {
        val prompt = """Task: Read temperature values from a thermal imaging camera display.

This is a photo of a thermography/infrared camera screen used for equipment inspection in power plant O&M. The display shows a thermal image with temperature readings overlaid.

Step-by-step:
1. Look for the maximum temperature value — usually labeled "Max", "Sp1", or shown with a crosshair/hotspot marker. It may appear in the top corner, bottom bar, or overlaid on the image.
2. Look for the minimum temperature value — usually labeled "Min" or shown separately.
3. Read each temperature carefully including decimal points. Thermal cameras typically show one decimal place (e.g., 45.2°C).
4. If you see range indicators or a color scale bar with numbers, the max and min from the measurement cursor/spot take priority over the scale endpoints.
5. Temperatures are in °C.

Output: ONLY two comma-separated numbers: maxTemp,minTemp
Example: 45.2,23.1"""

        val text = callGemini(bitmap, prompt)
        val parts = text.split(",").map { it.trim() }
        return mapOf(
            "maxTemp" to parseReading(parts.getOrElse(0) { "" }),
            "minTemp" to parseReading(parts.getOrElse(1) { "" })
        )
    }

    suspend fun readBatteryImage(bitmap: Bitmap): Map<String, String> {
        val prompt = """Task: Read values from a battery impedance/resistance tester display.

This is a photo of a battery internal resistance tester used for UPS/battery bank maintenance in power plant O&M. The display shows test results.

Step-by-step:
1. Locate the internal resistance reading — labeled "IR", "Resistance", "R", or "mΩ". This is in milliohms (mΩ). Typical range: 0.50 to 50.00 mΩ for healthy batteries. Read the decimal point carefully.
2. Locate the DC voltage reading — labeled "V", "Voltage", or "DCV". This is in volts (V). Typical range: 1.80 to 13.80 V per cell depending on battery type (2V cells or 12V blocks).
3. LCD decimal points are tiny dots between digit positions — check between every pair of digits.
4. If "4520" appears for resistance, it likely means 4.520 mΩ or 45.20 mΩ depending on the decimal point position.

Output: ONLY two comma-separated numbers: resistance,voltage
Example: 4.52,12.6"""

        val text = callGemini(bitmap, prompt)
        val parts = text.split(",").map { it.trim() }
        return mapOf(
            "measuredIr" to parseReading(parts.getOrElse(0) { "" }),
            "voltage" to parseReading(parts.getOrElse(1) { "" })
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun buildPrompt(fieldLabel: String, unit: String?): String =
        "What number is shown on the main display of this meter? " +
        "Copy the digits exactly as they appear, including the decimal point and any minus sign. " +
        "Ignore model numbers, labels and smaller secondary readings. Reply with only the number."

    private fun parseReading(text: String): String {
        val cleaned = text.trim()
            .replace(Regex("[^0-9.\\-]"), " ")
            .trim()
        val match = Regex("""-?\d+\.?\d*""").find(cleaned)
        return match?.value ?: cleaned
    }

    companion object {
        private const val TAG = "GeminiMeterReader"
        private const val UPLOAD_MAX_SIDE = 1280
        private val MODELS = listOf("gemini-flash-latest", "gemini-3.8-flash", "gemini-3.5-flash")
        private val DEFAULT_API_KEY = com.technavious.om15.BuildConfig.GEMINI_API_KEY

        fun isAvailable(context: Context): Boolean {
            return getApiKey(context).isNotBlank()
        }

        fun getApiKey(context: Context): String {
            val prefs = context.getSharedPreferences("om15_settings", Context.MODE_PRIVATE)
            val saved = prefs.getString("gemini_api_key", null)
            return if (!saved.isNullOrBlank()) saved else DEFAULT_API_KEY
        }

        fun saveApiKey(context: Context, key: String) {
            context.getSharedPreferences("om15_settings", Context.MODE_PRIVATE)
                .edit().putString("gemini_api_key", key).apply()
        }

        fun useGemini(context: Context): Boolean {
            val prefs = context.getSharedPreferences("om15_settings", Context.MODE_PRIVATE)
            return prefs.getBoolean("use_gemini", true)
        }

        fun setUseGemini(context: Context, use: Boolean) {
            context.getSharedPreferences("om15_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("use_gemini", use).apply()
        }
    }
}
