package com.technavious.om15.data.schema

import java.util.Locale

enum class ColType { TEXT, NUMBER, SELECT, CALC, DATE, LONGTEXT, CHECK, IMAGE }

data class Col(
    val key: String,
    val header: String,
    val type: ColType = ColType.NUMBER,
    val unit: String? = null,
    val default: String = "",
    val options: List<String> = emptyList(),
    val readOnly: Boolean = false
) {
    val isInput: Boolean get() = type != ColType.CALC && !readOnly
    val label: String get() = if (unit != null) "$header ($unit)" else header
}

typealias Row = MutableMap<String, String>
typealias Fields = MutableMap<String, String>

const val ROW_ID = "_id"
const val ROW_FIXED = "_fixed"
const val ROW_HEADER = "_hdr"

fun Row.isFixed() = this[ROW_FIXED] == "1"
fun Row.isSectionHeader() = this[ROW_HEADER] == "1"

/** One header cell of a (possibly multi-row, merged) Excel header, as the web app draws it. */
data class HCell(val text: String, val span: Int = 1, val rows: Int = 1)

class Section(
    val title: String?,
    val keys: List<String>,
    val headerRows: List<List<HCell>>? = null,
    val serial: String? = null,
    val totals: ((List<Row>, Fields) -> List<List<HCell>>)? = null
)

class TableSchema(
    val id: String,
    val title: String,
    val columns: List<Col>,
    val serial: String? = null,
    val headerRows: List<List<HCell>>? = null,
    sections: List<Section>? = null,
    val canAddRows: Boolean = true,
    val sectionHeaders: Boolean = false,
    val subGroupCol: String? = null,
    val transposed: Boolean = false,
    val initialRows: List<Map<String, String>> = emptyList(),
    val presetRows: List<Map<String, String>> = emptyList(),
    val totals: ((List<Row>, Fields) -> List<List<HCell>>)? = null,
    val calc: (Row, Fields) -> Unit = { _, _ -> }
) {
    val sections: List<Section> = sections ?: listOf(Section(null, columns.map { it.key }, headerRows, serial, totals))

    fun col(key: String) = columns.firstOrNull { it.key == key }

    fun newRow(index: Int, fields: Fields, extra: Map<String, String> = emptyMap()): Row = mutableMapOf<String, String>().apply {
        put(ROW_ID, "r${System.nanoTime()}_$index")
        columns.forEach { if (it.default.isNotEmpty()) put(it.key, it.default.withIndex(index)) }
        putAll(extra)
        calc(this, fields)
    }
}

/** Tables that repeat per named group (UPS, CAC, DG unit, location, panel...), added together with one button. */
class GroupSet(
    val key: String,
    val label: String,
    val defaultName: String,
    val tables: List<String>,
    val options: List<String> = emptyList(),
    val template: (tableId: String, option: String) -> List<Map<String, String>> = { _, _ -> listOf(emptyMap()) }
)

sealed interface Block
class FieldsBlock(val title: String, val cols: List<Col>) : Block
class TableBlock(val table: TableSchema) : Block
class SummaryBlock(val title: String, val headers: List<String>, val build: (ReadingsDoc) -> List<List<String>>) : Block

class TestSchema(
    val blocks: List<Block>,
    val groupSets: List<GroupSet> = emptyList(),
    val fieldCalc: (ReadingsDoc) -> Unit = {},
    val fileName: (ReadingsDoc, String) -> String
) {
    val tables: List<TableSchema> get() = blocks.filterIsInstance<TableBlock>().map { it.table }
    val fieldCols: List<Col> get() = blocks.filterIsInstance<FieldsBlock>().flatMap { it.cols }
    fun table(id: String) = tables.firstOrNull { it.id == id }
    fun groupSetFor(tableId: String) = groupSets.firstOrNull { tableId in it.tables }
}

fun String.withIndex(i: Int): String =
    replace("{nn}", String.format(Locale.US, "%02d", i + 1)).replace("{n}", (i + 1).toString())

// ---------- number helpers & formulas (ported 1:1 from the web app) ----------

fun n(s: String?): Double? = s?.replace(",", "")?.trim()?.toDoubleOrNull()
fun n0(s: String?): Double = n(s) ?: 0.0
fun fixed(d: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", d)
fun jsNum(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
fun roundStr(d: Double): String = Math.round(d).toString()
fun cfmFmt(x: Double): String = if (x % 1.0 == 0.0) fixed(x, 0) else fixed(x, 2)

fun coolingTr(cfm: String?, ra: String?, sa: String?): Double {
    val c = n(cfm); val r = n(ra); val s = n(sa)
    if (c == null || r == null || s == null) return 0.0
    return (1.06 * c * (r - s) * 1.8) / 12000
}

fun cfmRequired(loadW: String?, deltaT: String?, multiplier: String?): Double {
    val w = n0(loadW); val dt = n0(deltaT); val m = n(multiplier)?.takeIf { it != 0.0 } ?: 1.0
    if (w == 0.0 || dt == 0.0) return 0.0
    return (w * 3.412969 * m) / (1.8 * 1.08 * dt)
}

private val vibrationLimits = mapOf(
    "Class I" to doubleArrayOf(0.71, 1.8, 4.5),
    "Class II" to doubleArrayOf(1.12, 2.8, 7.1),
    "Class III" to doubleArrayOf(1.8, 4.5, 11.2),
    "Class IV" to doubleArrayOf(2.8, 7.1, 18.0)
)

fun vibrationCondition(classDetails: String?, max: String?): String {
    val m = n(max) ?: return ""
    val l = vibrationLimits[classDetails] ?: return ""
    return when {
        m <= l[0] -> "GOOD"
        m <= l[1] -> "Satisfactory"
        m <= l[2] -> "Unsatisfactory"
        else -> "Unacceptable"
    }
}

fun avgOf(vararg values: String?): Double? {
    val nums = values.mapNotNull { n(it) }
    return if (nums.isEmpty()) null else nums.average()
}

fun rulStatus(rulPct: Double, al: Double): String = when {
    al <= 0 -> ""
    rulPct > 50 -> "Fit for Service"
    rulPct >= 25 -> "Degrading Stage"
    rulPct >= 10 -> "Approaching Replacement"
    rulPct > 0 -> "Reaching End-of-Life"
    else -> "End-of-Life"
}
