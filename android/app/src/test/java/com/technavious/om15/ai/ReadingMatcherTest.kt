package com.technavious.om15.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingMatcherTest {

    // Real Gemini 3.5 Flash output for a vibration meter photo (27.0 m/s² and 25.0 °C on screen).
    private val sample = """
        {"schema_version":"0.1","device_category":"test_instrument","device_type":"other",
         "make_model_displayed":"908 VIBRATION METER","equipment_tag_displayed":"PAHU 01","display_timestamp_raw":null,
         "display_page_label":null,"image_quality":{"overall":"acceptable","issues":["blur","reflection"]},
         "readings":[
          {"reading_no":1,"parameter_name_displayed":"1KHz","parameter_category":"other","phase_label_displayed":null,
           "phase":"not_applicable","value_raw":"27.0","value_numeric":27.0,"unit_displayed":"m/s²","multiplier_displayed":null,
           "reading_method":"digital_display","analogue_scale":null,"confidence":"high","alternate_readings":[],"notes":null},
          {"reading_no":2,"parameter_name_displayed":null,"parameter_category":"temperature","phase_label_displayed":null,
           "phase":"not_applicable","value_raw":"25.0","value_numeric":25.0,"unit_displayed":"℃","multiplier_displayed":null,
           "reading_method":"digital_display","analogue_scale":null,"confidence":"high","alternate_readings":[],"notes":null}],
         "alarms_or_status_text":[],"general_notes":null}
    """.trimIndent()

    @Test
    fun picksReadingByUnitAndFlagsMismatch() {
        val x = MeterExtraction.parse(sample)
        assertEquals(2, x.readings.size)

        val temp = ReadingMatcher.choose(x, "Surface Temp", "°C")
        assertEquals("25.0", temp.value)
        assertTrue(temp.reviewReasons.isEmpty())

        val vib = ReadingMatcher.choose(x, "Vertical", "mm/s")
        assertEquals("27.0", vib.value)
        assertTrue(vib.reviewReasons.any { it.contains("m/s²") && it.contains("mm/s") })
    }

    @Test
    fun parsesCompactNumberOnlyAnswer() {
        // Real gemini-3.5-flash-lite answer for the same display cropped to the alignment box.
        val json = """{"readings":[{"value_raw":"27.0","unit_displayed":"m/s²","confidence":"high","alternate_readings":[]},
            {"value_raw":"25.0","unit_displayed":"°C","confidence":"high","alternate_readings":[]}],"image_quality":"good"}"""
        val x = MeterExtraction.parse(json)
        assertEquals("good", x.imageQuality)
        assertEquals("25.0", ReadingMatcher.choose(x, "Max Temp", "°C").value)
        assertEquals("27.0", ReadingMatcher.choose(x, "Vertical", "mm/s").value)
    }

    @Test
    fun flagsLowConfidenceAlternatesAndMultiplier() {
        val json = """
            {"image_quality":{"overall":"poor","issues":["glare"]},"alarms_or_status_text":["LOW BATT"],
             "readings":[{"parameter_name_displayed":"kWh","parameter_category":"active_energy","value_raw":"0415.2","value_numeric":415.2,
              "unit_displayed":"kWh","multiplier_displayed":"x10","confidence":"low","alternate_readings":["0475.2"],"notes":"faint segment"}]}
        """.trimIndent()
        val c = ReadingMatcher.choose(MeterExtraction.parse(json), "Energy", "kWh")
        assertEquals("0415.2", c.value)
        assertTrue(c.reviewReasons.any { it.startsWith("Low confidence") })
        assertTrue(c.reviewReasons.any { it.contains("0475.2") })
        assertTrue(c.reviewReasons.any { it.contains("x10") })
        assertTrue(c.reviewReasons.any { it.contains("poor") })
        assertTrue(c.reviewReasons.any { it.contains("LOW BATT") })
    }
}
