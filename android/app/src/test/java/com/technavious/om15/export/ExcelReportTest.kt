package com.technavious.om15.export

import com.technavious.om15.data.model.TestType
import com.technavious.om15.data.schema.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExcelReportTest {

    private fun sampleDoc(type: TestType): ReadingsDoc {
        val doc = ReadingsDoc.parse(null, schemaFor(type))
        doc.schema.fieldCols.forEach { c -> if (c.isInput && doc.fields[c.key].isNullOrEmpty()) doc.fields[c.key] = sample(c, 0) }
        doc.schema.groupSets.forEach { set -> (set.options.ifEmpty { listOf("") }).forEach { doc.addGroup(set, it) } }
        doc.schema.tables.forEach { t ->
            val set = doc.schema.groupSetFor(t.id)
            if (t.canAddRows) {
                val extra = set?.let { s -> doc.groupNames(s).firstOrNull()?.let { mapOf(s.key to it) } }.orEmpty()
                repeat(2) { i -> doc.rows(t.id) += t.newRow(i, doc.fields, extra) }
                if (t.sectionHeaders) doc.rows(t.id).add(0, t.newRow(9, doc.fields, extra + mapOf(ROW_HEADER to "1", t.columns.first().key to "SECTION A")))
            }
            doc.rows(t.id).filter { !it.isSectionHeader() }.forEachIndexed { i, r ->
                t.columns.filter { it.isInput && it.type != ColType.IMAGE }.forEach { c -> if (r[c.key].isNullOrEmpty()) r[c.key] = sample(c, i) }
            }
        }
        doc.recalc()
        return doc
    }

    private fun sample(c: Col, i: Int): String = when (c.type) {
        ColType.NUMBER -> listOf("12.5", "27.4", "3.2")[i % 3]
        ColType.SELECT -> c.options.firstOrNull().orEmpty()
        ColType.DATE -> "2026-09-30"
        ColType.CHECK -> "true"
        ColType.LONGTEXT -> "Line one\nLine two"
        else -> "${c.header} ${i + 1}"
    }

    @Test
    fun everyTestProducesAValidWorkbookWithoutOverlappingMerges() {
        val out = File("build/test-xlsx").apply { mkdirs() }
        TestType.entries.forEach { type ->
            val doc = sampleDoc(type)
            val file = File(out, "${type.name}.xlsx")
            XSSFWorkbook().use { wb ->
                ExcelExporter.Writer(wb, doc, emptyMap()).report(type.displayName, listOf("Project:" to "P", "Client:" to "C", "Colo / Floor Name:" to "L", "DOC NO:" to "D"))
                file.outputStream().use { wb.write(it) }
            }
            XSSFWorkbook(file).use { wb ->
                val sheet = wb.getSheetAt(0)
                val merges = sheet.mergedRegions
                for (i in merges.indices) for (j in i + 1 until merges.size) {
                    assertTrue("${type.name}: merged regions overlap ${merges[i].formatAsString()} / ${merges[j].formatAsString()}", !merges[i].intersects(merges[j]))
                }
                assertTrue("${type.name}: sheet is empty", sheet.lastRowNum > 5)
                println("${type.name}: ${sheet.lastRowNum + 1} rows, ${merges.size} merged regions")
            }
        }
    }

    @Test
    fun formulasMatchTheWebApp() {
        val vib = schemaFor(TestType.VIBRATION_ANALYSIS).table("summary")!!
        val row = vib.newRow(0, mutableMapOf(), mapOf("classDetails" to "Class II", "vertical" to "1.5", "horizontal" to "3.0", "axial" to "0.4"))
        assertEquals("3", row["maxValue"]); assertEquals("Unsatisfactory", row["condition"])

        val bat = schemaFor(TestType.BATTERY_IMPEDANCE).table("readings")!!
        assertEquals("FAIL", bat.newRow(0, mutableMapOf(), mapOf("measuredIr" to "14", "measuredVolt" to "12.5"))["remarks"])
        assertEquals("WARN", bat.newRow(0, mutableMapOf(), mapOf("measuredIr" to "9", "measuredVolt" to "12.5"))["remarks"])
        assertEquals("PASS", bat.newRow(0, mutableMapOf(), mapOf("measuredIr" to "5", "measuredVolt" to "12.5"))["remarks"])

        val rci = schemaFor(TestType.RACK_COOLING_INDEX).table("temperatures")!!
        val hot = rci.newRow(0, mutableMapOf(), mapOf("front15U" to "28", "front22U" to "29", "front35U" to "30", "rear15U" to "40"))
        assertEquals("FAIL", hot["inletTempResult"]); assertEquals("2.00", hot["excessInletTemp"]); assertEquals("11.00", hot["deltaT"])
        val rh = rci.newRow(0, mutableMapOf(), mapOf("rhFront15U" to "40", "rhFront22U" to "45", "rhFront35U" to "50", "rhRear15U" to "30"))
        assertEquals("45.00", rh["avgRhFront"]); assertEquals("30.00", rh["avgRhRear"])

        val pahu = schemaFor(TestType.CAPACITY_ASSESSMENT).table("pahu")!!
        assertEquals("8.6", pahu.newRow(0, mutableMapOf(), mapOf("measuredAirflow" to "10000", "measuredRaTemp" to "24", "measuredSaTemp" to "18.6"))["mTr"])

        val cfm = schemaFor(TestType.CFM_AIRFLOW).table("racks")!!
        assertEquals("2025.74", cfm.newRow(0, mutableMapOf("deltaT" to "13", "multiplier" to "1"), mapOf("lLoadW" to "15000"))["lReq"])

        val earth = schemaFor(TestType.EARTH_STATION).table("readings")!!
        assertEquals("High Resistance - Review Required", earth.newRow(0, mutableMapOf(), mapOf("measuredValue" to "3"))["remarks"])
        assertEquals("EP-1", earth.newRow(0, mutableMapOf())["pitNo"])
    }
}
