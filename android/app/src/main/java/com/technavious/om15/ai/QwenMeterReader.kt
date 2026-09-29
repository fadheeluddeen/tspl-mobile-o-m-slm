package com.technavious.om15.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class QwenMeterReader private constructor() {

    private val engine = LlamaEngine()
    private var modelLoaded = false

    fun isLoaded(): Boolean = modelLoaded && engine.isLoaded()

    @Synchronized
    fun loadModel(context: Context): Boolean {
        if (modelLoaded && engine.isLoaded()) return true

        val modelPath = LlamaEngine.getModelPath(context)
        val mmProjPath = LlamaEngine.getMmProjPath(context)

        if (!File(modelPath).exists() || !File(mmProjPath).exists()) {
            Log.e(TAG, "Model files not found at: $modelPath")
            return false
        }

        val start = System.currentTimeMillis()
        modelLoaded = engine.loadModel(modelPath, mmProjPath)
        Log.i(TAG, "Model load result: $modelLoaded in ${System.currentTimeMillis() - start} ms")
        return modelLoaded
    }

    fun readMeterImage(imagePath: String, fieldLabel: String, unit: String?): String {
        if (!isLoaded()) return ""
        val result = infer(imagePath, COMMON_PROMPT)
        return parseReading(result)
    }

    private fun infer(imagePath: String, prompt: String): String {
        val start = System.currentTimeMillis()
        val prepared = prepareImage(imagePath)
        return try {
            engine.runInference(prepared.absolutePath, prompt).also {
                Log.i(TAG, "Inference in ${System.currentTimeMillis() - start} ms: $it")
            }
        } finally {
            if (prepared.absolutePath != imagePath) prepared.delete()
        }
    }

    // Full-res photos produce more image tokens than the context holds and stb_image ignores EXIF rotation.
    private fun prepareImage(imagePath: String): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeFile(imagePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return File(imagePath)

        val rotation = when (ExifInterface(imagePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        val file = File(File(imagePath).parentFile, "qwen_in_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (out !== decoded) decoded.recycle()
        out.recycle()
        return file
    }

    fun readVibrationImage(imagePath: String, axisLabel: String?): Map<String, String> {
        if (!isLoaded()) return emptyMap()
        val prompt = if (axisLabel != null) {
            """Read the numeric value from this vibration meter LCD display.
This shows the $axisLabel axis reading in mm/s.
LCD decimal points are tiny dots between digits. Vibration readings are typically 0.01 to 45.00 mm/s.
"1999" on display means 19.99 mm/s. Check for decimal points carefully.
Return ONLY the numeric value. Example: 2.45"""
        } else {
            """Read vibration readings from this vibration meter LCD display.
Read three axes: vertical, horizontal, axial. Values in mm/s.
LCD decimal points are tiny dots between digits. Range: 0.01 to 45.00 mm/s.
Return ONLY three comma-separated numbers: vertical,horizontal,axial
Example: 2.45,1.23,0.89"""
        }

        val text = infer(imagePath, prompt)
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

    fun readThermographyImage(imagePath: String): Map<String, String> {
        if (!isLoaded()) return emptyMap()
        val prompt = """Read temperature values from this thermal camera display.
Find the maximum and minimum temperatures in °C.
Look for labels: Max, Min, Sp1, or crosshair markers.
Return ONLY two comma-separated numbers: maxTemp,minTemp
Example: 45.2,23.1"""

        val text = infer(imagePath, prompt)
        val parts = text.split(",").map { it.trim() }
        return mapOf(
            "maxTemp" to parseReading(parts.getOrElse(0) { "" }),
            "minTemp" to parseReading(parts.getOrElse(1) { "" })
        )
    }

    fun readBatteryImage(imagePath: String): Map<String, String> {
        if (!isLoaded()) return emptyMap()
        val prompt = """Read values from this battery impedance tester display.
Find internal resistance (mΩ) and DC voltage (V).
LCD decimal points are tiny dots between digits.
Resistance range: 0.50-50.00 mΩ. Voltage range: 1.80-13.80 V.
Return ONLY two comma-separated numbers: resistance,voltage
Example: 4.52,12.6"""

        val text = infer(imagePath, prompt)
        val parts = text.split(",").map { it.trim() }
        return mapOf(
            "measuredIr" to parseReading(parts.getOrElse(0) { "" }),
            "voltage" to parseReading(parts.getOrElse(1) { "" })
        )
    }

    fun freeModel() {
        engine.freeModel()
        modelLoaded = false
    }


    private fun parseReading(text: String): String {
        val cleaned = text.trim()
            .replace(Regex("[^0-9.\\-]"), " ")
            .trim()
        val match = Regex("""-?\d+\.?\d*""").find(cleaned)
        return match?.value ?: cleaned
    }

    companion object {
        private const val TAG = "QwenMeterReader"
        private const val MAX_SIDE = 672
        private const val COMMON_PROMPT =
            "What number is shown on the main display of this meter? " +
            "Copy the digits exactly as they appear, including the decimal point and any minus sign. " +
            "Reply with only the number."

        @Volatile
        private var instance: QwenMeterReader? = null

        fun getInstance(): QwenMeterReader {
            return instance ?: synchronized(this) {
                instance ?: QwenMeterReader().also { instance = it }
            }
        }

        fun saveBitmapToTemp(context: Context, bitmap: Bitmap): String {
            val file = File(context.cacheDir, "qwen_temp_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            return file.absolutePath
        }

        fun modelsPresent(context: Context): Boolean = LlamaEngine.areModelsPresent(context)
    }
}
