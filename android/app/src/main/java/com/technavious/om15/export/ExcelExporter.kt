package com.technavious.om15.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.technavious.om15.data.model.TestType
import com.technavious.om15.data.repository.TestRepository
import com.technavious.om15.data.schema.*
import com.technavious.om15.data.schema.Row
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.ss.util.WorkbookUtil
import org.apache.poi.xssf.usermodel.*
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Writes one test as an Excel report laid out exactly like the web app's test screen:
 * personnel, tools, every reading table (with its merged multi-row headers, groups, totals) and the summary tables, in screen order.
 */
class ExcelExporter(private val context: Context) {

    suspend fun export(assignmentId: String, repository: TestRepository): SavedFile? = withContext(Dispatchers.IO) {
        val a = repository.getAssignment(assignmentId) ?: return@withContext null
        val type = runCatching { TestType.valueOf(a.testType) }.getOrNull() ?: return@withContext null
        val project = repository.getProject(a.projectId)
        val schema = schemaFor(type)
        val doc = ReadingsDoc.parse(a.readingsJson, schema)
        val photos = repository.getPhotosByAssignment(assignmentId).first()
            .filter { File(it.filePath).exists() }
            .associateBy({ it.fieldKey }, { it.filePath })

        val wb = XSSFWorkbook()
        try {
            val w = Writer(wb, doc, photos)
            w.report(
                title = type.displayName,
                info = listOf(
                    "Project:" to (project?.name ?: a.projectName), "Client:" to (project?.clientName ?: a.clientName),
                    "Colo / Floor Name:" to a.location, "DOC NO:" to a.docNo
                )
            )
            w.photoSheet()
            val fileName = "${DownloadSaver.safeName(schema.fileName(doc, a.location.trim()))}.xlsx"
            DownloadSaver.saveXlsx(context, fileName) { wb.write(it) }
        } finally {
            wb.close()
        }
    }

    internal class Writer(val wb: XSSFWorkbook, val doc: ReadingsDoc, val photos: Map<String, String>) {
        private val s = Styler(wb)
        private val schema = doc.schema
        private lateinit var sh: XSSFSheet
        private var r = 0
        private val width: Int = maxOf(8, schema.tables.maxOfOrNull { t -> t.sections.maxOf { sectionWidth(t, it) } } ?: 8)

        fun report(title: String, info: List<Pair<String, String>>) {
            sh = wb.createSheet(WorkbookUtil.createSafeSheetName(title.take(31)))
            for (c in 0 until width) sh.setColumnWidth(c, 18 * 256)
            bar(title.uppercase(), "FFF97316", 12, 30f)
            info.chunked(width / 2).forEach { chunk -> pairsRow(chunk) }
            r++

            val renderedSets = HashSet<GroupSet>()
            schema.blocks.forEach { block ->
                when (block) {
                    is FieldsBlock -> fieldsBlock(block)
                    is SummaryBlock -> summary(block)
                    is TableBlock -> {
                        val set = schema.groupSetFor(block.table.id)
                        if (set == null) table(block.table, doc.rows(block.table.id))
                        else if (renderedSets.add(set)) groupSet(set)
                    }
                }
            }
        }

        // ---------- blocks ----------

        private fun fieldsBlock(b: FieldsBlock) {
            bar(b.title.uppercase(), "FF1E293B", 11, 24f)
            val short = b.cols.filter { it.type != ColType.LONGTEXT }
            short.chunked(width / 2).forEach { chunk -> pairsRow(chunk.map { it.label + ":" to display(it, doc.fields[it.key]) }) }
            b.cols.filter { it.type == ColType.LONGTEXT }.forEach { c ->
                val text = doc.fields[c.key].orEmpty()
                val xr = row(maxOf(40f, 15f * (text.lines().size + 1)))
                merge(r, r, 0, 1, c.label, s.header("FFF1F5F9", white = false))
                merge(r, r, 2, width - 1, text, s.cell(align = HorizontalAlignment.LEFT, wrap = true))
                xr.heightInPoints = maxOf(40f, 15f * (text.lines().size + 1)); r++
            }
            r++
        }

        private fun groupSet(set: GroupSet) {
            val names = doc.groupNames(set)
            if (names.isEmpty()) {
                set.tables.mapNotNull { schema.table(it) }.forEach { table(it, emptyList()) }
                return
            }
            names.forEach { name ->
                bar("${set.label.uppercase()}: ${name.uppercase()}", "FFE04F22", 11, 26f)
                set.tables.mapNotNull { schema.table(it) }.forEach { t -> table(t, doc.rows(t.id).filter { it[set.key] == name }) }
            }
        }

        private fun table(t: TableSchema, rows: List<Row>) {
            bar(t.title.uppercase(), "FFF97316", 11, 24f)
            if (t.transposed) { transposed(t, rows); r++; return }
            t.sections.forEach { section ->
                section.title?.let { bar(it, "FF334155", 10, 22f) }
                val sub = t.subGroupCol
                if (sub != null && rows.isNotEmpty()) {
                    rows.groupBy { it[sub].orEmpty() }.forEach { (value, subRows) ->
                        bar("${(t.col(sub)?.header ?: sub).uppercase()}: ${value.uppercase()}", "FF475569", 10, 22f)
                        sectionBody(t, section, subRows)
                    }
                } else sectionBody(t, section, rows)
            }
            r++
        }

        private fun sectionBody(t: TableSchema, section: Section, rows: List<Row>) {
            header(t, section)
            val w = sectionWidth(t, section)
            if (rows.isEmpty()) {
                merge(r, r, 0, w - 1, "No data entered", s.cell(italic = true, fontColor = "FF94A3B8")); row(20f); r++
            }
            var serial = 0
            rows.forEach { data ->
                if (data.isSectionHeader()) {
                    val text = t.columns.take(2).mapNotNull { data[it.key]?.takeIf { v -> v.isNotBlank() } }.joinToString("  ")
                    merge(r, r, 0, w - 1, text.uppercase(), s.cell(bold = true, fill = "FFF1F5F9", align = HorizontalAlignment.LEFT))
                    row(22f); r++
                    return@forEach
                }
                serial++
                val xr = row(22f)
                var c = 0
                if (section.serial != null) put(xr, c++, serial.toString(), s.cell(bold = true))
                section.keys.forEach { key ->
                    val col = t.col(key)
                    val value = data[key].orEmpty()
                    when (col?.type) {
                        ColType.IMAGE -> {
                            val path = photos[ReadingsDoc.cellKey(t.id, data[ROW_ID].orEmpty(), key)]
                            put(xr, c, if (path == null) "No Image" else "", s.cell())
                            if (path != null && picture(path, c, r)) xr.heightInPoints = 90f
                        }
                        ColType.CHECK -> put(xr, c, if (value == "true") "✓" else "✗", toneStyle(if (value == "true") "✓" else "✗"))
                        ColType.CALC, ColType.SELECT -> num(xr, c, value, toneStyle(value))
                        ColType.LONGTEXT -> { put(xr, c, value, s.cell(align = HorizontalAlignment.LEFT, wrap = true)); xr.heightInPoints = maxOf(22f, 15f * (value.lines().size + 1)) }
                        else -> num(xr, c, value, s.cell(bold = col?.readOnly == true, align = if (col?.type == ColType.TEXT && value.length > 24) HorizontalAlignment.LEFT else HorizontalAlignment.CENTER, wrap = true))
                    }
                    c++
                }
                r++
            }
            section.totals?.invoke(rows.filter { !it.isSectionHeader() }.toList(), doc.fields)?.forEach { cells ->
                row(22f)
                var c = 0
                cells.forEach { hc ->
                    merge(r, r, c, c + hc.span - 1, hc.text, s.cell(bold = true, fill = "FFF1F5F9"))
                    c += hc.span
                }
                r++
            }
        }

        private fun header(t: TableSchema, section: Section) {
            val rowsSpec = section.headerRows
                ?: listOf((listOfNotNull(section.serial) + section.keys.map { t.col(it)?.label ?: it }).map { HCell(it) })
            val taken = HashSet<Pair<Int, Int>>()
            rowsSpec.forEachIndexed { i, spec ->
                rowAt(r + i).heightInPoints = if (rowsSpec.size > 1) 24f else 28f
                var c = 0
                spec.forEach { hc ->
                    while ((r + i) to c in taken) c++
                    val bg = if (i == 0) "FF1E293B" else "FF334155"
                    merge(r + i, r + i + hc.rows - 1, c, c + hc.span - 1, hc.text, s.header(bg))
                    for (rr in r + i until r + i + hc.rows) for (cc in c until c + hc.span) taken += rr to cc
                    c += hc.span
                }
            }
            r += rowsSpec.size
        }

        private fun transposed(t: TableSchema, rows: List<Row>) {
            val headerKey = t.columns.first().key
            val xr = row(26f)
            put(xr, 0, "S.NO.", s.header("FF1E293B")); put(xr, 1, "PARAMETERS", s.header("FF1E293B"))
            rows.forEachIndexed { i, data -> put(xr, 2 + i, data[headerKey].orEmpty(), s.header("FF1E293B")) }
            r++
            t.columns.drop(1).forEachIndexed { pi, col ->
                val pr = row(22f)
                put(pr, 0, (pi + 1).toString(), s.cell(bold = true))
                put(pr, 1, col.header, s.cell(bold = true, align = HorizontalAlignment.LEFT))
                rows.forEachIndexed { i, data -> num(pr, 2 + i, data[col.key], s.cell()) }
                r++
            }
        }

        private fun summary(b: SummaryBlock) {
            val rows = b.build(doc)
            val headers = b.headers.ifEmpty { rows.firstOrNull().orEmpty() }
            val body = if (b.headers.isEmpty()) rows.drop(1) else rows
            bar(b.title.uppercase(), "FF0369A1", 11, 24f)
            val xr = row(28f)
            headers.forEachIndexed { i, t -> put(xr, i, t, s.header("FF1E293B")) }
            r++
            if (body.isEmpty()) {
                merge(r, r, 0, maxOf(headers.size, 1) - 1, "No data", s.cell(italic = true, fontColor = "FF94A3B8")); row(20f); r++
            }
            body.forEach { cells ->
                val br = row(maxOf(22f, 15f * (cells.maxOf { it.lines().size } + 1)))
                cells.forEachIndexed { i, v -> num(br, i, v, if (StatusTone.of(v) != null) toneStyle(v) else s.cell(wrap = true, align = if (v.length > 30) HorizontalAlignment.LEFT else HorizontalAlignment.CENTER)) }
                r++
            }
            r++
        }

        fun photoSheet() {
            val entries = schema.tables.flatMap { t ->
                doc.rows(t.id).flatMapIndexed { i, row ->
                    t.columns.mapNotNull { col ->
                        photos[ReadingsDoc.cellKey(t.id, row[ROW_ID].orEmpty(), col.key)]?.let { PhotoEntry(t, i, col, row, it) }
                    }
                }
            }
            if (entries.isEmpty()) return
            val ps = wb.createSheet("Photos")
            listOf(8, 28, 10, 28, 16, 40).forEachIndexed { i, w -> ps.setColumnWidth(i, w * 256) }
            val hr = ps.createRow(0).apply { heightInPoints = 24f }
            listOf("S.No", "Table", "Row", "Field", "Value", "Photo").forEachIndexed { i, t -> hr.createCell(i).apply { setCellValue(t); cellStyle = s.header("FFF97316") } }
            entries.forEachIndexed { i, e ->
                val xr = ps.createRow(i + 1).apply { heightInPoints = 110f }
                listOf((i + 1).toString(), e.table.title, (e.rowIndex + 1).toString(), e.col.label, e.row[e.col.key].orEmpty(), "")
                    .forEachIndexed { c, v -> xr.createCell(c).apply { setCellValue(v); cellStyle = s.cell(wrap = true) } }
                pictureOn(ps, e.path, 5, i + 1)
            }
        }

        private class PhotoEntry(val table: TableSchema, val rowIndex: Int, val col: Col, val row: Row, val path: String)

        // ---------- helpers ----------

        private fun sectionWidth(t: TableSchema, sec: Section): Int =
            if (t.transposed) 2 + doc.rows(t.id).size.coerceAtLeast(1)
            else sec.headerRows?.firstOrNull()?.sumOf { it.span } ?: ((if (sec.serial != null) 1 else 0) + sec.keys.size)

        private fun display(c: Col, v: String?): String = when (c.type) {
            ColType.CHECK -> if (v == "true") "✓" else "✗"
            else -> v.orEmpty()
        }

        private fun toneStyle(v: String?): XSSFCellStyle {
            val tone = StatusTone.of(v) ?: return s.cell()
            return s.cell(bold = true, fill = tone.fillArgb, fontColor = tone.fontArgb)
        }

        private fun bar(text: String, bg: String, size: Short, height: Float) {
            merge(r, r, 0, width - 1, text, s.header(bg, size = size, align = HorizontalAlignment.LEFT))
            row(height); r++
        }

        private fun pairsRow(pairs: List<Pair<String, String>>) {
            val xr = row(20f)
            pairs.forEachIndexed { i, (label, value) ->
                put(xr, i * 2, label, s.plain(bold = true))
                put(xr, i * 2 + 1, value, s.cell(align = HorizontalAlignment.LEFT))
            }
            r++
        }

        private fun row(height: Float): org.apache.poi.ss.usermodel.Row =
            (sh.getRow(r) ?: sh.createRow(r)).also { it.heightInPoints = height }

        private fun rowAt(index: Int) = sh.getRow(index) ?: sh.createRow(index)

        private fun put(xr: org.apache.poi.ss.usermodel.Row, col: Int, value: String?, style: CellStyle) {
            (xr.getCell(col) ?: xr.createCell(col)).apply { setCellValue(value.orEmpty()); cellStyle = style }
        }

        private fun num(xr: org.apache.poi.ss.usermodel.Row, col: Int, value: String?, style: CellStyle) {
            val cell = xr.getCell(col) ?: xr.createCell(col)
            val t = value?.trim().orEmpty()
            val d = n(t)
            val leadingZeroCode = t.length > 1 && t.startsWith("0") && !t.startsWith("0.")
            if (d != null && !leadingZeroCode) cell.setCellValue(d) else cell.setCellValue(value.orEmpty())
            cell.cellStyle = style
        }

        private fun merge(r1: Int, r2: Int, c1: Int, c2: Int, text: String, style: XSSFCellStyle) {
            for (rr in r1..r2) {
                val xr = rowAt(rr)
                for (c in c1..c2) (xr.getCell(c) ?: xr.createCell(c)).cellStyle = style
            }
            rowAt(r1).getCell(c1).setCellValue(text)
            if (r1 != r2 || c1 != c2) sh.addMergedRegion(CellRangeAddress(r1, r2, c1, c2))
        }

        private fun picture(path: String, col: Int, rowIndex: Int): Boolean = pictureOn(sh, path, col, rowIndex)

        private fun pictureOn(sheet: XSSFSheet, path: String, col: Int, rowIndex: Int): Boolean = runCatching {
            val bytes = compressedJpeg(path) ?: return false
            val idx = wb.addPicture(bytes, Workbook.PICTURE_TYPE_JPEG)
            val anchor = XSSFClientAnchor(EMU_PER_PIXEL * 3, EMU_PER_PIXEL * 3, 0, 0, col, rowIndex, col + 1, rowIndex + 1)
            anchor.anchorType = ClientAnchor.AnchorType.MOVE_AND_RESIZE
            sheet.createDrawingPatriarch().createPicture(anchor, idx)
            true
        }.getOrDefault(false)

        private fun compressedJpeg(path: String): ByteArray? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 800) sample *= 2
            val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            return ByteArrayOutputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 80, out); bmp.recycle(); out.toByteArray() }
        }
    }

    private companion object { const val EMU_PER_PIXEL = 9525 }

    /** Cell style cache mirroring the web app's styleHeaderCell / applyBorderGrid helpers. */
    private class Styler(private val wb: XSSFWorkbook) {
        private val cache = HashMap<String, XSSFCellStyle>()

        private fun color(argb: String): XSSFColor {
            val v = argb.takeLast(6)
            return XSSFColor(byteArrayOf(v.substring(0, 2).toInt(16).toByte(), v.substring(2, 4).toInt(16).toByte(), v.substring(4, 6).toInt(16).toByte()), null)
        }

        private fun borders(st: XSSFCellStyle, argb: String) {
            val c = color(argb)
            st.setBorderTop(BorderStyle.THIN); st.setBorderBottom(BorderStyle.THIN); st.setBorderLeft(BorderStyle.THIN); st.setBorderRight(BorderStyle.THIN)
            st.setTopBorderColor(c); st.setBottomBorderColor(c); st.setLeftBorderColor(c); st.setRightBorderColor(c)
        }

        fun header(bg: String, white: Boolean = true, size: Short = 10, align: HorizontalAlignment = HorizontalAlignment.CENTER): XSSFCellStyle =
            cache.getOrPut("h|$bg|$white|$size|$align") {
                wb.createCellStyle().apply {
                    setFillForegroundColor(color(bg)); fillPattern = FillPatternType.SOLID_FOREGROUND
                    setFont(wb.createFont().apply { fontName = "Arial"; fontHeightInPoints = size; bold = true; setColor(color(if (white) "FFFFFFFF" else "FF1E293B")) })
                    alignment = align; verticalAlignment = VerticalAlignment.CENTER; wrapText = true
                    borders(this, if (white) "FFFFFFFF" else "FFCBD5E1")
                }
            }

        fun cell(
            bold: Boolean = false, italic: Boolean = false, fontColor: String? = null, fill: String? = null,
            align: HorizontalAlignment = HorizontalAlignment.CENTER, wrap: Boolean = false
        ): XSSFCellStyle = cache.getOrPut("c|$bold|$italic|$fontColor|$fill|$align|$wrap") {
            wb.createCellStyle().apply {
                borders(this, "FFCBD5E1")
                alignment = align; verticalAlignment = VerticalAlignment.CENTER; wrapText = wrap
                if (fill != null) { setFillForegroundColor(color(fill)); fillPattern = FillPatternType.SOLID_FOREGROUND }
                setFont(wb.createFont().apply {
                    fontName = "Arial"; fontHeightInPoints = 10; this.bold = bold; this.italic = italic
                    fontColor?.let { setColor(color(it)) }
                })
            }
        }

        fun plain(bold: Boolean = false): XSSFCellStyle = cache.getOrPut("p|$bold") {
            wb.createCellStyle().apply {
                alignment = HorizontalAlignment.LEFT; verticalAlignment = VerticalAlignment.CENTER
                setFont(wb.createFont().apply { fontName = "Arial"; fontHeightInPoints = 10; this.bold = bold })
            }
        }
    }
}
