package com.technavious.om15.data.schema

/** Green / amber / orange / red colouring of calculated status values, shared by the form and the Excel file. */
enum class StatusTone(val fillArgb: String, val fontArgb: String) {
    GOOD("FFDCFCE7", "FF15803D"),
    WARN("FFFEF3C7", "FF92400E"),
    ORANGE("FFFFEDD5", "FF9A3412"),
    BAD("FFFEE2E2", "FFB91C1C");

    companion object {
        fun of(value: String?): StatusTone? {
            val v = value?.trim()?.lowercase() ?: return null
            return when {
                v.isEmpty() || v == "..." || v == "-" -> null
                v in setOf("good", "pass", "ok", "yes", "✓") || v.startsWith("fit for") -> GOOD
                v in setOf("satisfactory", "warn", "warning") || v.startsWith("degrading") -> WARN
                v == "unsatisfactory" || v.startsWith("approaching") || v.startsWith("reaching") -> ORANGE
                v in setOf("unacceptable", "fail", "no", "✗") || v.startsWith("high resistance") || v == "end-of-life" -> BAD
                else -> null
            }
        }
    }
}
