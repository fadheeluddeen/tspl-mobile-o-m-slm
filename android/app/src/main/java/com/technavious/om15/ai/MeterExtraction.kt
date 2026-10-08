package com.technavious.om15.ai

import org.json.JSONArray
import org.json.JSONObject

/** One reading transcribed from a display, as defined by meter_reading_schema.json. */
data class ExtractedReading(
    val label: String?,
    val category: String,
    val valueRaw: String?,
    val valueNumeric: Double?,
    val unit: String?,
    val multiplier: String?,
    val confidence: String,
    val alternates: List<String>,
    val notes: String?
) {
    /** Value to put in the form: the characters as displayed when they are a plain number, else the parsed number. */
    val formValue: String
        get() = valueRaw?.trim()?.takeIf { it.toDoubleOrNull() != null }
            ?: valueNumeric?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
            ?: ""

    val display: String get() = listOfNotNull(valueRaw ?: "—", unit, multiplier?.let { "($it)" }).joinToString(" ")
}

data class MeterExtraction(
    val readings: List<ExtractedReading>,
    val imageQuality: String,
    val qualityIssues: List<String>,
    val alarms: List<String>,
    val generalNotes: String?,
    val rawJson: String
) {
    companion object {
        fun parse(json: String): MeterExtraction {
            val o = JSONObject(json)
            fun JSONObject.str(k: String) = if (isNull(k)) null else optString(k).takeIf { it.isNotEmpty() }
            fun JSONArray?.strings() = this?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            val readings = o.optJSONArray("readings")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val r = arr.getJSONObject(i)
                    ExtractedReading(
                        label = r.str("parameter_name_displayed"),
                        category = r.optString("parameter_category", "unknown"),
                        valueRaw = r.str("value_raw"),
                        valueNumeric = if (r.isNull("value_numeric")) null else r.optDouble("value_numeric").takeIf { !it.isNaN() },
                        unit = r.str("unit_displayed"),
                        multiplier = r.str("multiplier_displayed"),
                        confidence = r.optString("confidence", "low"),
                        alternates = r.optJSONArray("alternate_readings").strings(),
                        notes = r.str("notes")
                    )
                }
            }.orEmpty()
            val quality = o.optJSONObject("image_quality")
            return MeterExtraction(
                readings = readings,
                imageQuality = quality?.optString("overall", "acceptable") ?: o.optString("image_quality", "acceptable").ifEmpty { "acceptable" },
                qualityIssues = quality?.optJSONArray("issues").strings().filter { it != "none" },
                alarms = o.optJSONArray("alarms_or_status_text").strings(),
                generalNotes = o.str("general_notes"),
                rawJson = json
            )
        }
    }
}

/** The reading chosen for a form field, plus the reasons (if any) an engineer should double-check it. */
data class ReadingChoice(val reading: ExtractedReading?, val reviewReasons: List<String>) {
    val value: String get() = reading?.formValue.orEmpty()
}

object ReadingMatcher {

    fun normalizeUnit(u: String?): String? = u?.trim()?.lowercase()
        ?.replace("℃", "°c")?.replace("²", "2")?.replace("µ", "u")?.replace("μ", "u")
        ?.replace("ohm", "ω")?.replace(" ", "")?.removePrefix("°")?.let { if (it == "c") "°c" else it }
        ?.takeIf { it.isNotEmpty() }

    private fun tokens(s: String?) = s.orEmpty().lowercase().split(Regex("[^a-z0-9%]+")).filter { it.length > 1 }.toSet()

    /** Picks the reading that best matches the field being filled (by unit, then label), and flags anything needing review. */
    fun choose(x: MeterExtraction, fieldLabel: String, fieldUnit: String?): ReadingChoice {
        val wantUnit = normalizeUnit(fieldUnit)
        val fieldTokens = tokens(fieldLabel)
        val candidates = x.readings.filter { it.valueRaw != null || it.valueNumeric != null }
        val best = candidates.maxByOrNull { r ->
            var score = 0.0
            if (wantUnit != null && normalizeUnit(r.unit) == wantUnit) score += 4
            if (tokens(r.label).intersect(fieldTokens).isNotEmpty()) score += 2
            if (wantUnit == "°c" && r.category == "temperature") score += 1
            if (wantUnit == "%" && fieldTokens.contains("rh") && r.category == "relative_humidity") score += 1
            score += when (r.confidence) { "high" -> 1.0; "medium" -> 0.5; else -> 0.0 }
            score
        }

        val reasons = mutableListOf<String>()
        if (best == null) reasons += "No readable value found on the display."
        best?.let { r ->
            when (r.confidence) {
                "low" -> reasons += "Low confidence — check the digits and decimal point."
                "unreadable" -> reasons += "Value could not be read${r.notes?.let { ": $it" } ?: "."}"
                "medium" -> reasons += "Medium confidence${r.notes?.let { " — $it" } ?: ""}."
            }
            if (r.alternates.isNotEmpty()) reasons += "Could also be: ${r.alternates.joinToString(", ")}."
            val shown = normalizeUnit(r.unit)
            if (wantUnit != null && shown != null && shown != wantUnit) reasons += "Display shows ${r.unit}, but this field expects $fieldUnit."
            r.multiplier?.let { reasons += "Display shows multiplier \"$it\" — it has NOT been applied." }
        }
        if (x.imageQuality == "poor" || x.imageQuality == "unusable") {
            reasons += "Image quality ${x.imageQuality}${x.qualityIssues.takeIf { it.isNotEmpty() }?.let { " (${it.joinToString(", ") { s -> s.replace('_', ' ') }})" } ?: ""}."
        }
        if (x.alarms.isNotEmpty()) reasons += "Display shows: ${x.alarms.joinToString("; ")}."
        return ReadingChoice(best, reasons)
    }
}
