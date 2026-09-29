package com.technavious.om15.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class VibrationResult(val vertical: String, val horizontal: String, val axial: String)
data class ThermographyResult(val maxTemp: String, val minTemp: String)
data class BatteryResult(val measuredIr: String, val voltage: String)
data class RciResult(val temp: String, val rh: String)
data class SingleValueResult(val value: String)

interface MeterReader {
    suspend fun readVibrationDisplay(bitmap: Bitmap, context: Context, axisLabel: String? = null): Any
    suspend fun readThermographyDisplay(bitmap: Bitmap, context: Context): ThermographyResult
    suspend fun readBatteryImpedanceDisplay(bitmap: Bitmap, context: Context): BatteryResult
    suspend fun readEarthContinuityDisplay(bitmap: Bitmap, context: Context): SingleValueResult
    suspend fun readRciDisplay(bitmap: Bitmap, context: Context, isGrouped22U: Boolean): RciResult
    suspend fun readPahuDisplay(bitmap: Bitmap, context: Context, fieldLabel: String): SingleValueResult
    suspend fun readLcaDisplay(bitmap: Bitmap, context: Context, fieldLabel: String): SingleValueResult
    suspend fun readCfmDisplay(bitmap: Bitmap, context: Context): SingleValueResult
    suspend fun readSingleInstrumentDisplay(bitmap: Bitmap, context: Context, fieldLabel: String, unit: String?): SingleValueResult
    fun isModelLoaded(): Boolean
    suspend fun loadModel(context: Context, onProgress: (Float) -> Unit = {})
}

class MLKitMeterReader : MeterReader {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun isModelLoaded(): Boolean = true

    override suspend fun loadModel(context: Context, onProgress: (Float) -> Unit) {
        onProgress(1f)
    }

    private suspend fun extractAllText(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()
        return result.text
    }

    private suspend fun extractTextBlocks(bitmap: Bitmap): List<String> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()
        return result.textBlocks.map { it.text }
    }

    private fun extractNumbers(text: String): List<String> {
        val cleaned = text
            .replace(",", ".")
            .replace(Regex("""(\d)\s+(\d)"""), "$1$2")
            .replace(Regex("""(\d)\s*\.\s*(\d)"""), "$1.$2")
        val pattern = Regex("""-?\d+\.?\d*""")
        return pattern.findAll(cleaned).map { it.value }.toList()
    }

    private fun extractLargestNumber(text: String): String {
        val numbers = extractNumbers(text)
        return numbers.maxByOrNull { it.replace("-", "").toDoubleOrNull() ?: 0.0 } ?: ""
    }

    private fun findPrimaryReading(text: String): String {
        val numbers = extractNumbers(text)
        if (numbers.isEmpty()) return ""
        val withDecimals = numbers.filter { it.contains(".") }
        if (withDecimals.isNotEmpty()) return withDecimals.first()
        return numbers.first()
    }

    private fun extractNumberNear(text: String, keyword: String): String {
        val lines = text.lines()
        for (line in lines) {
            if (line.contains(keyword, ignoreCase = true)) {
                val numbers = extractNumbers(line)
                if (numbers.isNotEmpty()) return numbers.first()
            }
        }
        return ""
    }

    override suspend fun readVibrationDisplay(bitmap: Bitmap, context: Context, axisLabel: String?): Any {
        val text = extractAllText(bitmap)
        val numbers = extractNumbers(text)

        return if (axisLabel != null) {
            val value = extractNumberNear(text, axisLabel).ifEmpty { numbers.firstOrNull() ?: "" }
            SingleValueResult(value)
        } else {
            VibrationResult(
                vertical = numbers.getOrElse(0) { "" },
                horizontal = numbers.getOrElse(1) { "" },
                axial = numbers.getOrElse(2) { "" }
            )
        }
    }

    override suspend fun readThermographyDisplay(bitmap: Bitmap, context: Context): ThermographyResult {
        val text = extractAllText(bitmap)
        val maxTemp = extractNumberNear(text, "max").ifEmpty { extractNumberNear(text, "MAX") }
        val minTemp = extractNumberNear(text, "min").ifEmpty { extractNumberNear(text, "MIN") }
        val numbers = extractNumbers(text)
        return ThermographyResult(
            maxTemp = maxTemp.ifEmpty { numbers.getOrElse(0) { "" } },
            minTemp = minTemp.ifEmpty { numbers.getOrElse(1) { "" } }
        )
    }

    override suspend fun readBatteryImpedanceDisplay(bitmap: Bitmap, context: Context): BatteryResult {
        val text = extractAllText(bitmap)
        val irValue = extractNumberNear(text, "mΩ")
            .ifEmpty { extractNumberNear(text, "mohm") }
        val voltageValue = extractNumberNear(text, "V")
            .ifEmpty { extractNumberNear(text, "volt") }
        val numbers = extractNumbers(text)
        return BatteryResult(
            measuredIr = irValue.ifEmpty { numbers.getOrElse(0) { "" } },
            voltage = voltageValue.ifEmpty { numbers.getOrElse(1) { "" } }
        )
    }

    override suspend fun readEarthContinuityDisplay(bitmap: Bitmap, context: Context): SingleValueResult {
        val text = extractAllText(bitmap)
        val value = extractNumberNear(text, "Ω")
            .ifEmpty { extractNumberNear(text, "ohm") }
            .ifEmpty { extractLargestNumber(text) }
        return SingleValueResult(value)
    }

    override suspend fun readRciDisplay(bitmap: Bitmap, context: Context, isGrouped22U: Boolean): RciResult {
        val text = extractAllText(bitmap)
        return if (isGrouped22U) {
            val temp = extractNumberNear(text, "°C")
                .ifEmpty { extractNumberNear(text, "temp") }
            val rh = extractNumberNear(text, "%RH")
                .ifEmpty { extractNumberNear(text, "RH") }
                .ifEmpty { extractNumberNear(text, "%") }
            val numbers = extractNumbers(text)
            RciResult(
                temp = temp.ifEmpty { numbers.getOrElse(0) { "" } },
                rh = rh.ifEmpty { numbers.getOrElse(1) { "" } }
            )
        } else {
            val temp = extractNumberNear(text, "°C")
                .ifEmpty { extractNumberNear(text, "temp") }
                .ifEmpty { extractLargestNumber(text) }
            RciResult(temp = temp, rh = "")
        }
    }

    override suspend fun readPahuDisplay(bitmap: Bitmap, context: Context, fieldLabel: String): SingleValueResult {
        val text = extractAllText(bitmap)
        val value = extractNumberNear(text, fieldLabel).ifEmpty { extractLargestNumber(text) }
        return SingleValueResult(value)
    }

    override suspend fun readLcaDisplay(bitmap: Bitmap, context: Context, fieldLabel: String): SingleValueResult {
        val text = extractAllText(bitmap)
        val value = extractNumberNear(text, fieldLabel).ifEmpty { extractLargestNumber(text) }
        return SingleValueResult(value)
    }

    override suspend fun readCfmDisplay(bitmap: Bitmap, context: Context): SingleValueResult {
        val text = extractAllText(bitmap)
        val value = extractNumberNear(text, "CFM")
            .ifEmpty { extractNumberNear(text, "cfm") }
            .ifEmpty { extractLargestNumber(text) }
        return SingleValueResult(value)
    }

    override suspend fun readSingleInstrumentDisplay(bitmap: Bitmap, context: Context, fieldLabel: String, unit: String?): SingleValueResult {
        val text = extractAllText(bitmap)
        val value = if (unit != null) {
            extractNumberNear(text, unit).ifEmpty { extractNumberNear(text, fieldLabel) }
        } else {
            extractNumberNear(text, fieldLabel)
        }.ifEmpty { findPrimaryReading(text) }
        return SingleValueResult(value)
    }
}
