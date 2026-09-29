package com.technavious.om15.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.technavious.om15.data.db.TestAssignmentEntity
import com.technavious.om15.data.model.TestType
import com.technavious.om15.data.repository.TestRepository
import com.technavious.om15.data.schema.*
import com.technavious.om15.data.schema.Row
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ExcelExporter(private val context: Context) {

    suspend fun export(assignmentId: String, repository: TestRepository): SavedFile? = withContext(Dispatchers.IO) {
        val assignment = repository.getAssignment(assignmentId) ?: return@withContext null
        val type = runCatching { TestType.valueOf(assignment.testType) }.getOrNull() ?: return@withContext null
        val project = repository.getProject(assignment.projectId)
        val schema = schemaFor(type)
        val doc = ReadingsDoc.parse(assignment.readingsJson, schema)
        val photos = repository.getPhotosByAssignment(assignmentId).first()
            .filter { File(it.filePath).exists() }
            .associateBy({ it.fieldKey }, { it.filePath })

        val ctx = ExportContext(assignment, project?.name ?: assignment.projectName, project?.clientName ?: assignment.clientName, doc, schema, photos)
        val wb = XSSFWorkbook()
        val s = Styler(wb)
        val fileName = when (type) {
            TestType.VIBRATION_ANALYSIS -> vibration(wb, s, ctx)
            TestType.THERMOGRAPHY -> thermography(wb, s, ctx)
            TestType.BATTERY_IMPEDANCE -> battery(wb, s, ctx)
            TestType.EARTH_STATION -> earth(wb, s, ctx)
            TestType.RACK_COOLING_INDEX -> rci(wb, s, ctx)
            TestType.CFM_AIRFLOW -> cfm(wb, s, ctx)
            TestType.CAPACITY_ASSESSMENT -> capacity(wb, s, ctx)
            TestType.LIFE_CYCLE -> lifeCycle(wb, s, ctx)
            TestType.POWER_FACTOR -> powerFactor(wb, s, ctx)
            TestType.LIGHTNING_ARRESTOR -> lightning(wb, s, ctx)
            TestType.DG_ENDURANCE -> dg(wb, s, ctx)
            TestType.COUPON_TEST -> coupon(wb, s, ctx)
            else -> generic(wb, s, ctx, type)
        }
        photoSheet(wb, s, ctx)

        try {
            DownloadSaver.saveXlsx(context, fileName) { wb.write(it) }
        } finally {
            wb.close()
        }
    }

    private class ExportContext(
        val assignment: TestAssignmentEntity,
        val projectName: String,
        val clientName: String,
        val doc: ReadingsDoc,
        val schema: TestSchema,
        val photos: Map<String, String>
    ) {
        fun rows(tableId: String): List<Row> = doc.tables[tableId].orEmpty()
        val location get() = assignment.location.trim()
    }

    private val utcDate: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    private fun name(base: String) = "${DownloadSaver.safeName(base)}.xlsx"

    // ---------------- Vibration ----------------

    private fun vibration(wb: XSSFWorkbook, s: Styler, c: ExportContext): String {
        val group = c.location.ifBlank { "GROUP 1" }
        val sh = wb.createSheet(sheetName(c.location.ifBlank { "Vibration Summary" }))
        widths(sh, 22, 15, 14, 14, 14, 14, 22)
        merge(sh, 0, 0, 0, 6, group.uppercase(), s.header("FFE04F22", size = 11)); sh.getRow(0).heightInPoints = 28f
        merge(sh, 1, 2, 0, 0, "Equipment ID", s.header("FF1E293B"))
        merge(sh, 1, 2, 1, 1, "Class Details", s.header("FF1E293B"))
        merge(sh, 1, 1, 2, 5, "Measured Vibration (mm/s)", s.header("FF1E293B"))
        merge(sh, 1, 2, 6, 6, "Vibration Condition", s.header("FF1E293B"))
        val sub = row(sh, 2, 20f)
        listOf("Vertical", "Horizontal", "Axial", "Max Value (mm/s)").forEachIndexed { i, t -> put(sub, 2 + i, t, s.header("FF334155")) }

        c.rows("vibration").forEachIndexed { i, r ->
            val xr = row(sh, 3 + i, 22f)
            val base = s.cell(size = 9)
            put(xr, 0, r["equipmentId"], s.cell(size = 9, bold = true))
            put(xr, 1, r["classDetails"], base)
            num(xr, 2, r["vertical"], base); num(xr, 3, r["horizontal"], base); num(xr, 4, r["axial"], base)
            num(xr, 5, r["maxValue"], base)
            val cond = r["condition"].orEmpty().ifBlank { "GOOD" }
            val u = cond.uppercase()
            val good = u.contains("GOOD") || (u.contains("SATISFACTORY") && !u.contains("UNSATISFACTORY"))
            put(xr, 6, cond, s.cell(size = 9, bold = true, fontColor = if (good) "FF156534" else "FF9A3412", fill = if (good) "FFF0FDF4" else "FFFFF7ED"))
        }
        return name("${c.location.ifBlank { "Vibration_Analysis" }}_Report_$utcDate")
    }

    // ---------------- Thermography ----------------

    private fun thermography(wb: XSSFWorkbook, s: Styler, c: ExportContext): String {
        val rows = c.rows("inspections")
        val sh = wb.createSheet("Thermography Inspection")
        widths(sh, 8, 25, 25, 25, 15, 15, 35)
        headerRow(sh, s, 0, 24f, "FFF97316", "S.NO", "ROOM NAME", "PANEL NAME", "FEEDER NAME", "MAX TEMP °C", "MIN TEMP °C", "REMARKS")
        rows.forEachIndexed { i, r ->
            val xr = row(sh, i + 1, 22f)
            put(xr, 0, (i + 1).toString(), s.cell())
            put(xr, 1, r["roomName"], s.cell(bold = true)); put(xr, 2, r["panelName"], s.cell(bold = true)); put(xr, 3, r["feederName"], s.cell())
            put(xr, 4, r["maxTemp"], s.cell(bold = true, fontColor = "FFDC2626"))
            put(xr, 5, r["minTemp"], s.cell(bold = true, fontColor = "FF2563EB"))
            put(xr, 6, r["remarks"], s.cell(italic = true))
        }

        val img = wb.createSheet("Critical Images")
        widths(img, 8, 25, 25, 25, 30, 35)
        headerRow(img, s, 0, 24f, "FFF97316", "S.NO", "ROOM NAME", "PANEL NAME", "FEEDER NAME", "CRITICAL IMAGES", "REMARKS")
        rows.forEachIndexed { i, r ->
            val xr = row(img, i + 1, 22f)
            put(xr, 0, (i + 1).toString(), s.cell())
            put(xr, 1, r["roomName"], s.cell(bold = true)); put(xr, 2, r["panelName"], s.cell(bold = true)); put(xr, 3, r["feederName"], s.cell())
            put(xr, 5, r["remarks"], s.cell(italic = true))
            val photo = firstPhoto(c, "inspections", r)
            if (photo != null && picture(wb, img, photo, 4, i + 1, 5, i + 2)) {
                put(xr, 4, "", s.cell()); xr.heightInPoints = 80f
            } else put(xr, 4, "No Image", s.cell())
        }
        return name("Thermography_Report_${System.currentTimeMillis()}")
    }

    // ---------------- Battery impedance ----------------

    private fun battery(wb: XSSFWorkbook, s: Styler, c: ExportContext): String {
        val sh = wb.createSheet("Battery Impedance")
        widths(sh, 10, 22, 16, 16, 16, 16, 16)
        var r = 0
        val byUps = c.rows("readings").groupBy { it["upsName"].orEmpty() }
        byUps.forEach { (ups, upsRows) ->
            merge(sh, r, r, 0, 6, "UPS SYSTEM: $ups", s.header("FFF97316", size = 12)); sh.getRow(r).heightInPoints = 28f; r++
            val d = row(sh, r, 20f)
            put(d, 0, "Location:", s.plain(bold = true)); put(d, 1, c.location, s.plain())
            put(d, 2, "Project:", s.plain(bold = true)); put(d, 3, c.projectName, s.plain())
            put(d, 4, "Client:", s.plain(bold = true)); put(d, 5, c.clientName, s.plain())
            r += 2
            upsRows.groupBy { it["stringName"].orEmpty() }.forEach { (str, strRows) ->
                merge(sh, r, r, 0, 6, "STRING: $str", s.header("FF1E293B", size = 11)); sh.getRow(r).heightInPoints = 24f; r++
                headerRow(sh, s, r, 22f, "FF475569", "No", "Measured IR (mΩ)", "Fail Thresh", "Warn Thresh", "Voltage (V)", "Volt Thresh", "Status"); r++
                strRows.forEach { b ->
                    val xr = row(sh, r, 20f)
                    num(xr, 0, b["batteryNo"], s.cell(bold = true))
                    listOf("measuredIr", "irFailThresh", "irWarnThresh", "measuredVolt", "voltThresh").forEachIndexed { i, k -> num(xr, i + 1, b[k], s.cell()) }
                    val st = b["remarks"].orEmpty().ifBlank { "..." }
                    val fill = when (st.uppercase()) { "FAIL" -> "FFDC2626"; "WARN", "WARNING" -> "FFF59E0B"; "PASS" -> "FF198754"; else -> null }
                    put(xr, 6, st, s.cell(bold = true, fill = fill, fontColor = fill?.let { "FFFFFFFF" }))
                    r++
                }
                r += 2
            }
            r++
        }
        val first = c.rows("readings").firstOrNull()
        return name("Battery_Impedance_${first?.get("upsName").orEmpty().ifBlank { "UPS" }}_${first?.get("stringName").orEmpty().ifBlank { "String" }}")
    }

    // ---------------- Earth station ----------------

    private fun earth(wb: XSSFWorkbook, s: Styler, c: ExportContext): String {
        val sh = wb.createSheet("Earth Continuity")
        widths(sh, 16, 30, 22, 22, 35)
        merge(sh, 0, 0, 0, 4, "EARTH CONTINUITY & EARTH STATION TEST", s.header("FFFB923C", size = 12)); sh.getRow(0).heightInPoints = 28f
        var r = metadata(sh, s, c, 1, pairsPerRow = 2)
        r += 2
        merge(sh, r, r, 0, 4, "MEASURING DETAILS (READINGS)", s.header("FFFB923C", size = 11)); sh.getRow(r).heightInPoints = 24f; r++
        headerRow(sh, s, r, 22f, "FF475569", "Earth Pit No", "Earthing Description", "Measured Value (Ω)", "Limit Value (Ω)", "Remarks"); r++
        c.rows("readings").forEach { e ->
            val xr = row(sh, r++, 20f)
            put(xr, 0, e["pitNo"], s.cell(bold = true))
            put(xr, 1, e["description"], s.cell(align = HorizontalAlignment.LEFT))
            num(xr, 2, e["measuredValue"], s.cell()); num(xr, 3, e["limitValue"], s.cell())
            val rem = e["remarks"].orEmpty()
            val style = when {
                rem.isEmpty() -> s.cell(bold = true)
                rem.equals("ok", true) -> s.cell(bold = true, fill = "FFE2F0D9", fontColor = "FF385723")
                else -> s.cell(bold = true, fill = "FFFCE4D6", fontColor = "FFC00000")
            }
            put(xr, 4, rem, style)
        }
        return name("Earth_Continuity_Report_$utcDate")
    }

    // ---------------- Rack cooling index ----------------

    private fun rci(wb: XSSFWorkbook, s: Styler, c: ExportContext): String {
        val cac = c.rows("temperatures").firstOrNull()?.get("cacRef").orEmpty().ifBlank { c.location }
        val sh = wb.createSheet(sheetName(cac.ifBlank { "RCI Temp Study" }))
        widths(sh, 8, 18, 18, 12, 12, 12, 12, 12, 12, 12, 12, 15, 15, 15)
        headerRow(sh, s, 0, 28f, "FFF59E0B", "S.No.", "CAC Reference", "Rack Reference", "Front 15U", "Front 22U", "Front 35U",
            "Rear 15U", "Rear 22U", "Rear 35U", "RH Front", "RH Rear", "Inlet Temp Result", "Excess Temp", "Deficit Temp")
        c.rows("temperatures").forEachIndexed { i, t ->
            val xr = row(sh, i + 1, 20f)
            val base = s.cell(size = 9)
            put(xr, 0, (i + 1).toString(), base)
            put(xr, 1, t["cacRef"].orEmpty().ifBlank { cac }, s.cell(size = 9, bold = true))
            put(xr, 2, t["rackRef"], s.cell(size = 9, bold = true))
            listOf("front15U", "front22U", "front35U", "rear15U", "rear22U", "rear35U", "rhFront", "rhRear", "inletTempResult", "excessInletTemp", "deficitInletTemp")
                .forEachIndexed { k, key -> num(xr, 3 + k, t[key], base) }
        }
        return name("${cac.ifBlank { "CAC" }}_Temperature_Study_$utcDate")
    }

    // ---------------- CFM ----------------

    private fun cfm(wb: XSSFWorkbook, s: Styler, c: ExportContext): String {
        val byCac = c.rows("racks").groupBy { it["cacName"].orEmpty().ifBlank { c.location.ifBlank { "CAC" } } }
        val single = byCac.keys.singleOrNull()
        val input = wb.createSheet(sheetName("${(single ?: "CAC").take(20)} Input"))
        val preview = wb.createSheet(sheetName("${(single ?: "CAC").take(20)} Preview"))
        widths(input, 18, 14, 14, 14, 18, 14, 14, 14)
        widths(preview, 10, 12, 12, 12, 12, 12, 12, 12, 12)
        var ri = 0; var rp = 0
        byCac.forEach { (cac, rows) ->
            val left = rows.filter { it["side"] != "Right" }
            val right = rows.filter { it["side"] == "Right" }
            fun req(r: Row) = cfmRequired(r["loadW"], r["deltaT"], r["multiplier"])
            val leftSub = left.sumOf(::req); val rightSub = right.sumOf(::req)

            merge(input, ri, ri, 0, 3, cac, s.header("FFF97316")); merge(input, ri, ri, 4, 7, "TABLE 1", s.header("FFF97316")); ri++
            headerRow(input, s, ri++, 22f, "FF1E293B", "RACK REFERENCE", "RACK LOAD W", "ACTUAL CFM", "REQUIRED CFM", "RACK REFERENCE", "RACK LOAD W", "ACTUAL CFM", "REQUIRED CFM")
            for (i in 0 until maxOf(left.size, right.size)) {
                val xr = row(input, ri++, 20f)
                listOf(left.getOrNull(i), right.getOrNull(i)).forEachIndexed { side, r ->
                    val o = side * 4
                    if (r == null) (0..3).forEach { put(xr, o + it, "", s.cell()) }
                    else {
                        put(xr, o, r["rackRef"], s.cell(bold = true)); num(xr, o + 1, r["loadW"], s.cell()); num(xr, o + 2, r["actualCFM"], s.cell())
                        num(xr, o + 3, req(r).takeIf { it > 0 }?.let(::cfmFmt), s.cell())
                    }
                }
            }
            val tot = s.cell(bold = true, fill = "FFF1F5F9")
            listOf("SUB TOTAL", "TOTAL CFM REQUIRED").forEach { label ->
                val xr = row(input, ri, 20f)
                merge(input, ri, ri, 0, 2, label, tot); num(xr, 3, fixed(Math.round(leftSub).toDouble(), 0), tot)
                merge(input, ri, ri, 4, 6, label, tot); num(xr, 7, fixed(Math.round(rightSub).toDouble(), 0), tot)
                ri++
            }
            val xr = row(input, ri, 20f)
            merge(input, ri, ri, 0, 6, "TOTAL SUPPLY CFM", tot); num(xr, 7, fixed(Math.round(leftSub + rightSub).toDouble(), 0), tot)
            ri += 3

            merge(preview, rp, rp + 1, 0, 0, "GRILL", s.header("FF1E293B"))
            merge(preview, rp, rp, 1, 4, "R 0 1", s.header("FFF97316")); merge(preview, rp, rp, 5, 8, "R 0 2", s.header("FFF97316"))
            rp++
            val sub = row(preview, rp++, 20f)
            listOf("RACK LOAD", "ACTUAL", "REQUIRED", "DEFICIT", "RACK LOAD", "ACTUAL", "REQUIRED", "DEFICIT").forEachIndexed { i, t -> put(sub, i + 1, t, s.header("FF334155")) }
            for (i in 0 until maxOf(left.size, right.size)) {
                val pr = row(preview, rp++, 20f)
                put(pr, 0, (i + 1).toString(), s.cell(bold = true))
                listOf(left.getOrNull(i), right.getOrNull(i)).forEachIndexed { side, r ->
                    val o = 1 + side * 4
                    val q = r?.let(::req) ?: 0.0
                    num(pr, o, r?.get("loadW")?.ifBlank { "0" } ?: "0", s.cell())
                    num(pr, o + 1, r?.get("actualCFM")?.ifBlank { "0" } ?: "0", s.cell())
                    num(pr, o + 2, cfmFmt(q), s.cell())
                    num(pr, o + 3, cfmFmt((n(r?.get("actualCFM")) ?: 0.0) - q), s.cell())
                }
            }
            val ft = row(preview, rp++, 20f)
            put(ft, 0, "SUBTOTAL", tot)
            listOf(left to leftSub, right to rightSub).forEachIndexed { side, (rs, subReq) ->
                val o = 1 + side * 4
                val load = rs.sumOf { n(it["loadW"]) ?: 0.0 }; val act = rs.sumOf { n(it["actualCFM"]) ?: 0.0 }
                listOf(load, act, subReq, act - subReq).forEachIndexed { k, v -> num(ft, o + k, fixed(Math.round(v).toDouble(), 0), tot) }
            }
            rp += 2
        }
        return name("${single ?: c.location.ifBlank { "CAC" }}_Data")
    }

    // ---------------- Capacity (PAHU / PAC) ----------------

    private fun capacity(wb: XSSFWorkbook, s: Styler, c: ExportContext): String {
        val pahu = c.rows("pahu"); val pac = c.rows("pac")
        if (pahu.isNotEmpty() || pac.isEmpty()) {
            val sh = wb.createSheet("PAHU Capacity")
            widths(sh, 18, 18, 18, 15, 12, 12, 12, 15, 15, 15, 12, 12, 12, 15, 12, 12, 12, 12, 15, 15)
            merge(sh, 0, 0, 0, 19, "${c.location} - PAHU CAPACITY TABLE".trim(' ', '-').uppercase(), s.header("FF1E3A8A")); sh.getRow(0).heightInPoints = 40f
            listOf("Equipment ID", "Model No", "Serial No").forEachIndexed { i, t -> merge(sh, 1, 2, i, i, t, s.header("FFF97316")) }
            merge(sh, 1, 1, 3, 8, "Design", s.header("FF3B82F6")); merge(sh, 1, 1, 9, 19, "Measured", s.header("FF10B981"))
            val h = row(sh, 2, 35f)
            listOf("Airflow \n(CFM)", "RA Temp \n(°C)", "SA Temp \n(°C)", "Power \n(kW)", "Water Flow \n(GPM)", "Cooling Tonnage \n(TR)")
                .forEachIndexed { i, t -> put(h, 3 + i, t, s.header("FF3B82F6")) }
            listOf("Airflow \n(CFM)", "SA Temp \n(°C)", "RA Temp \n(°C)", "Fan Speed \n(%)", "Cooling Tonnage (Air) \n(TR)", "Power \n(kW)",
                "CHW In \n(°C)", "CHW Out \n(°C)", "Valve \n(%)", "Water Flow \n(GPM)", "Cooling Tonnage (Water) \n(TR)")
                .forEachIndexed { i, t -> put(h, 9 + i, t, s.header("FF10B981")) }
            val keys = listOf("equipmentId", "modelNo", "serialNo", "designAirflow", "designRaTemp", "designSaTemp", "designPower", "designWaterFlowRate",
                "designCoolingTonnage", "measuredAirflow", "measuredSaTemp", "measuredRaTemp", "measuredFanSpeed", "measuredCoolingTonnage", "measuredPower",
                "measuredChwIn", "measuredChwOut", "measuredValveOpening", "measuredWaterFlowRate", "measuredWaterCoolingTonnage")
            pahu.forEachIndexed { i, e -> dataRow(sh, s, 3 + i, 25f, e, keys, boldCols = setOf(8, 13, 19), font = null) }
        }
        if (pac.isNotEmpty()) {
            val sh = wb.createSheet("PAC Capacity")
            widths(sh, 18, 18, 18, 15, 12, 12, 12, 15, 12, 15, 12, 12, 15, 12, 12)
            merge(sh, 0, 0, 0, 14, "${c.location} - PAC CAPACITY TABLE".trim(' ', '-').uppercase(), s.header("FF1E3A8A")); sh.getRow(0).heightInPoints = 40f
            listOf("Equipment ID", "Model No", "Serial No").forEachIndexed { i, t -> merge(sh, 1, 2, i, i, t, s.header("FFF97316")) }
            merge(sh, 1, 1, 3, 7, "Design", s.header("FF3B82F6"))
            merge(sh, 1, 2, 8, 8, "Measured", s.header("FFF97316"))
            merge(sh, 1, 1, 9, 14, "Measured", s.header("FF3B82F6"))
            val h = row(sh, 2, 35f)
            listOf("Airflow \n(CFM)", "RA Temp \n(°C)", "SA Temp \n(°C)", "Power \n(kW)", "Cooling Tonnage \n(TR)").forEachIndexed { i, t -> put(h, 3 + i, t, s.header("FF3B82F6")) }
            listOf("Airflow \n(CFM)", "RA Temp \n(°C)", "SA Temp \n(°C)", "Cooling Tonnage (Air) \n(TR)", "Power \n(kW)", "COP").forEachIndexed { i, t -> put(h, 9 + i, t, s.header("FF3B82F6")) }
            val keys = listOf("equipmentId", "modelNo", "serialNo", "designAirflow", "designRaTemp", "designSaTemp", "designPower", "designCoolingTonnage",
                "fanSpeed", "measuredAirflow", "measuredRaTemp", "measuredSaTemp", "measuredCoolingTonnage", "measuredPower", "cop")
            pac.forEachIndexed { i, e -> dataRow(sh, s, 3 + i, 25f, e, keys, boldCols = setOf(7, 12, 14), font = null) }
        }
        return name("${c.location.ifBlank { "Capacity_Assessment" }}_Report_$utcDate")
    }

    // ---------------- Life cycle ----------------

    private fun lifeCycle(wb: XSSFWorkbook, s: Styler, c: ExportContext): String {
        val rows = c.rows("lca")
        val t1 = wb.createSheet("LCA Table-1")
        widths(t1, 8, 22, 22, 20, 20, 15, 15, 12, 18, 18, 18, 18, 18, 20, 20)
        headerRow(t1, s, 0, 26f, "FF0369A1", "S.No", "Equipment Tag / Name", "Make & Model No", "Serial No", "Location", "Install Year", "Current Year",
            "Age (yrs)", "Vibration Score (VS)", "Corrosion Score (CS)", "Maint. Score (MS)", "Health Index (HI)", "Running Life (yrs)",
            "Utilization Ratio (UR)", "Utilization Factor (UF)")
        rows.forEachIndexed { i, r ->
            val row = mutableMapOf("sNo" to (i + 1).toString()).apply { putAll(r) }
            dataRow(t1, s, i + 1, 20f, row, listOf("sNo", "equipmentTag", "makeModel", "serialNo", "location", "installYear", "currentYear", "ageYrs",
                "vibrationScore", "corrosionScore", "maintScore", "healthIndex", "runningLifeYrs", "utilizationRatio", "utilizationFactor"), size = 9)
        }
        val t2 = wb.createSheet("LCA Table-2")
        widths(t2, 8, 22, 20, 14, 18, 22, 20, 18, 18, 22, 25)
        headerRow(t2, s, 0, 26f, "FF0369A1", "S.No", "Equipment Tag / Name", "Design Life (yrs)", "Age (yrs)", "Health Index (HI)",
            "Utilization Factor (UF)", "Adjusted Life (AL)", "RUL (yrs)", "RUL (%)", "Life cycle Status", "Remarks (RUL)")
        rows.forEachIndexed { i, r ->
            val row = mutableMapOf("sNo" to (i + 1).toString()).apply { putAll(r) }
            dataRow(t2, s, i + 1, 20f, row, listOf("sNo", "equipmentTag", "designLifeYrs", "ageYrs", "healthIndex", "utilizationFactor",
                "adjustedLifeAL", "rulYrs", "rulPct", "lifeCycleStatus", "remarksRul"), size = 9)
        }
        return name("LCA_Evaluation_$utcDate")
    }

    // ---------------- Power factor ----------------

    private fun powerFactor(wb: XSSFWorkbook, s: Styler, c: ExportContext): String {
        val sh = wb.createSheet("Power Factor")
        widths(sh, 20, 22, 20, 22, 24, 25, 25, 25, 18, 35)
        merge(sh, 0, 0, 0, 9, "POWER FACTOR / CAPACITOR HEALTH ASSESSMENT", s.header("FFF97316", size = 12)); sh.getRow(0).heightInPoints = 28f
        var r = metadata(sh, s, c, 1, pairsPerRow = 3) + 1
        c.rows("feeders").groupBy { it["panelName"].orEmpty().ifBlank { "APFC PANEL" } }.forEach { (panel, feeders) ->
            merge(sh, r, r, 0, 9, "APFC PANEL: $panel", s.header("FF2563EB", size = 11)); sh.getRow(r).heightInPoints = 24f; r++
            merge(sh, r, r, 0, 9, "CAPACITOR BANK METRICS", s.header("FFF97316")); sh.getRow(r).heightInPoints = 22f; r++
            headerRow(sh, s, r++, 22f, "FF475569", "Feeder Name", "Capacitor Bank in kVAR", "Breaker Rating in A", "Contactor Rating in A",
                "Obtained Capacitance in µF", "Actual RY Capacitance in µF", "Actual YB Capacitance in µF", "Actual BR Capacitance in µF",
                "Measured kVAR", "Acceptable Limit of Capacitance in kVAR")
            feeders.forEach { f ->
                dataRow(sh, s, r++, 20f, f, listOf("feederName", "bankKvar", "breakerRating", "contactorRating", "obtainedCap", "actualRY",
                    "actualYB", "actualBR", "measuredKvar", "acceptableLimit"), boldCols = setOf(0), leftCols = setOf(0))
            }
            r++
            merge(sh, r, r, 0, 5, "TEMPERATURE & PHASE CURRENT READINGS", s.header("FFF97316")); sh.getRow(r).heightInPoints = 22f; r++
            headerRow(sh, s, r++, 22f, "FF475569", "Feeder Name", "Case Temperature", "R Phase Current in Amp", "Y Phase Current in Amp", "B Phase Current in Amp", "Remarks")
            feeders.forEach { f ->
                dataRow(sh, s, r++, 20f, f, listOf("feederName", "caseTemp", "currentR", "currentY", "currentB", "remarks"), boldCols = setOf(0), leftCols = setOf(0))
            }
            r += 2
        }
        return name("Power_Factor_Assessment_$utcDate")
    }

    // ---------------- Lightning arrestor ----------------

    private fun lightning(wb: XSSFWorkbook, s: Styler, c: ExportContext): String {
        val sh = wb.createSheet("Grounding Resistance")
        widths(sh, 10, 25, 25, 25, 22, 20, 25, 15)
        headerRow(sh, s, 0, 26f, "FFF97316", "Sr. No", "Earth Pit Names", "Arrester Location", "Conductor Size & Type", "Grounding Value (Ω)", "Accept Value (Ω)", "Remarks", "Result")
        c.rows("pits").forEachIndexed { i, p ->
            val xr = row(sh, i + 1, 22f)
            put(xr, 0, (i + 1).toString(), s.cell(bold = true))
            put(xr, 1, p["pitName"], s.cell(align = HorizontalAlignment.LEFT))
            put(xr, 2, p["location"], s.cell(align = HorizontalAlignment.LEFT))
            put(xr, 3, p["conductorType"], s.cell(align = HorizontalAlignment.LEFT))
            num(xr, 4, p["groundingValue"], s.cell(bold = true)); num(xr, 5, p["acceptValue"], s.cell(bold = true))
            put(xr, 6, p["remarks"], s.cell(align = HorizontalAlignment.LEFT))
            val res = p["result"].orEmpty().ifBlank { "..." }
            put(xr, 7, res, when (res.lowercase()) {
                "pass" -> s.cell(bold = true, fontColor = "FF15803D", fill = "FFDCFCE7")
                "fail" -> s.cell(bold = true, fontColor = "FFB91C1C", fill = "FFFEE2E2")
                else -> s.cell()
            })
        }
        return name("Lightning_Protection_$utcDate")
    }

    // ---------------- DG endurance ----------------

    private fun dg(wb: XSSFWorkbook, s: Styler, c: ExportContext): String {
        val header = s.header("FF0F172A", gridBorder = true)
        fun sheet(title: String, headers: List<String>, widthsList: List<Int>, keys: List<String>, rows: List<Row>, serial: Boolean) {
            val sh = wb.createSheet("DG1 - $title")
            widths(sh, *widthsList.toIntArray())
            val hr = sh.createRow(0)
            headers.forEachIndexed { i, t -> put(hr, i, t, header) }
            rows.forEachIndexed { i, r ->
                val data = mutableMapOf<String, String>().apply { putAll(r); if (serial) put("srNo", (i + 1).toString()) }
                val xr = sh.createRow(i + 1)
                keys.forEachIndexed { k, key -> put(xr, k, data[key], s.cell(font = null)) }
            }
        }
        sheet("Parameters", listOf("Parameter", "Unit", "0", "25", "50", "75", "100", "Remarks"), listOf(25, 10, 12, 12, 12, 12, 12, 30),
            listOf("parameter", "unit", "v0", "v25", "v50", "v75", "v100", "remarks"), c.rows("parameters"), false)
        sheet("Transient Load", listOf("Sr No", "Transient Load", "Result"), listOf(10, 25, 15), listOf("srNo", "load", "result"), c.rows("transient"), true)
        sheet("Noise Level", listOf("Load in %", "Doors Open", "Doors Closed"), listOf(15, 15, 15), listOf("load", "open", "closed"), c.rows("noise"), false)
        sheet("Vibration", listOf("Load %") + ('A'..'H').map { it.toString() }, List(9) { 10 }, listOf("load") + ('a'..'h').map { it.toString() }, c.rows("vibration"), false)
        sheet("Fuel Consum", listOf("Sr No", "% LOAD", "TIME", "kWh Generated Initial", "kWh Generated Final", "kWh Generated Consumed",
            "Fuel in Litres Initial", "Fuel in Litres Final", "Fuel in Litres Consumed", "OEM Std (L/H)", "Efficiency (kWh/L)"),
            listOf(10, 15, 15, 25, 25, 25, 25, 25, 25, 20, 25),
            listOf("srNo", "percentLoad", "time", "kwhInitial", "kwhFinal", "kwhUnits", "fuelInitial", "fuelFinal", "fuelConsum", "oemStd", "efficiency"),
            c.rows("fuel"), true)
        return name("DG_Endurance_Test_$utcDate")
    }

    // ---------------- Coupon ----------------

    private fun coupon(wb: XSSFWorkbook, s: Styler, c: ExportContext): String {
        val sh = wb.createSheet(sheetName(c.location.ifBlank { "Coupon Test" }))
        widths(sh, 20, 15, 15, 15, 15, 15, 20, 30, 40)
        headerRow(sh, s, 0, 30f, "FF1E3A8A", "Type of coupon", "Oxide", "Chloride", "Sulfide", "Other", "Total (Å)", "Level", "Remarks", "Graph Image")
        c.rows("results").forEachIndexed { i, r ->
            val xr = row(sh, i + 1, 25f)
            put(xr, 0, r["couponType"], s.cell(font = null, align = HorizontalAlignment.LEFT))
            listOf("oxide", "chloride", "sulfide", "other").forEachIndexed { k, key -> num(xr, 1 + k, r[key]?.ifBlank { "0" } ?: "0", s.cell()) }
            num(xr, 5, r["totalAngstroms"]?.ifBlank { "0" } ?: "0", s.cell(font = null))
            put(xr, 6, r["isaLevel"], s.cell(font = null)); put(xr, 7, r["remarks"], s.cell(font = null)); put(xr, 8, "", s.cell(font = null))
        }
        return name("${c.location.ifBlank { "Coupon" }}_Test_$utcDate")
    }

    // ---------------- Tests without a web-app Excel format ----------------

    private fun generic(wb: XSSFWorkbook, s: Styler, c: ExportContext, type: TestType): String {
        val table = c.schema.tables.first()
        val cols = table.columns
        val sh = wb.createSheet(sheetName(type.displayName))
        widths(sh, 8, *cols.map { it.width }.toIntArray())
        merge(sh, 0, 0, 0, cols.size, type.displayName.uppercase(), s.header("FFF97316", size = 12)); sh.getRow(0).heightInPoints = 28f
        val r = metadata(sh, s, c, 1, pairsPerRow = 2) + 1
        headerRow(sh, s, r, 24f, "FF1E293B", "S.No", *cols.map { it.label }.toTypedArray())
        c.rows(table.id).forEachIndexed { i, row ->
            val data = mutableMapOf("sNo" to (i + 1).toString()).apply { putAll(row) }
            dataRow(sh, s, r + 1 + i, 20f, data, listOf("sNo") + cols.map { it.key })
        }
        return name("${type.displayName.replace(Regex("[()/]"), "")}_Report_$utcDate")
    }

    // ---------------- Photos ----------------

    private fun photoSheet(wb: XSSFWorkbook, s: Styler, c: ExportContext) {
        val entries = c.schema.tables.flatMap { t ->
            c.rows(t.id).flatMapIndexed { i, row ->
                t.columns.mapNotNull { col ->
                    c.photos[ReadingsDoc.cellKey(t.id, row[ROW_ID].orEmpty(), col.key)]?.let { Triple(t, i, col) to (row to it) }
                }
            }
        }
        if (entries.isEmpty()) return
        val sh = wb.createSheet("Photos")
        widths(sh, 8, 22, 10, 26, 16, 40)
        headerRow(sh, s, 0, 24f, "FFF97316", "S.No", "Table", "Row", "Field", "Value", "Photo")
        entries.forEachIndexed { i, (meta, data) ->
            val (t, rowIdx, col) = meta
            val (row, path) = data
            val xr = row(sh, i + 1, 110f)
            put(xr, 0, (i + 1).toString(), s.cell())
            put(xr, 1, t.title, s.cell()); put(xr, 2, (rowIdx + 1).toString(), s.cell())
            put(xr, 3, col.label, s.cell(bold = true)); num(xr, 4, row[col.key], s.cell(bold = true))
            put(xr, 5, "", s.cell())
            picture(wb, sh, path, 5, i + 1, 6, i + 2)
        }
    }

    // ---------------- Helpers ----------------

    private fun firstPhoto(c: ExportContext, tableId: String, row: Row): String? {
        val rowId = row[ROW_ID].orEmpty()
        return c.schema.table(tableId)?.columns?.firstNotNullOfOrNull { c.photos[ReadingsDoc.cellKey(tableId, rowId, it.key)] }
    }

    private fun metadata(sh: Sheet, s: Styler, c: ExportContext, start: Int, pairsPerRow: Int): Int {
        val pairs = listOf(
            "Project:" to c.projectName, "Client:" to c.clientName, "Location:" to c.location,
            "Doc No:" to c.assignment.docNo, "Test Date:" to SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(Date(c.assignment.updatedAt)),
            "Status:" to c.assignment.workflowStatus
        )
        var r = start
        pairs.chunked(pairsPerRow).forEach { chunk ->
            val xr = row(sh, r++, 20f)
            chunk.forEachIndexed { i, (label, value) ->
                put(xr, i * 2, label, s.plain(bold = true, align = HorizontalAlignment.LEFT))
                put(xr, i * 2 + 1, value, s.cell(align = HorizontalAlignment.LEFT))
            }
        }
        return r
    }

    private fun sheetName(raw: String) = org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(raw.take(31))

    private fun widths(sh: Sheet, vararg w: Int) = w.forEachIndexed { i, v -> sh.setColumnWidth(i, v * 256) }

    private fun row(sh: Sheet, index: Int, height: Float? = null): org.apache.poi.ss.usermodel.Row =
        (sh.getRow(index) ?: sh.createRow(index)).also { r -> height?.let { r.heightInPoints = it } }

    private fun put(r: org.apache.poi.ss.usermodel.Row, col: Int, value: String?, style: CellStyle) {
        (r.getCell(col) ?: r.createCell(col)).apply { setCellValue(value.orEmpty()); cellStyle = style }
    }

    private fun num(r: org.apache.poi.ss.usermodel.Row, col: Int, value: String?, style: CellStyle) {
        val cell = r.getCell(col) ?: r.createCell(col)
        val t = value?.trim().orEmpty()
        val d = n(t)
        val leadingZeroCode = t.length > 1 && t.startsWith("0") && !t.startsWith("0.")
        if (d != null && !leadingZeroCode) cell.setCellValue(d) else cell.setCellValue(value.orEmpty())
        cell.cellStyle = style
    }

    private fun headerRow(sh: Sheet, s: Styler, index: Int, height: Float, bg: String, vararg titles: String) {
        val r = row(sh, index, height)
        titles.forEachIndexed { i, t -> put(r, i, t, s.header(bg)) }
    }

    private fun dataRow(
        sh: Sheet, s: Styler, index: Int, height: Float, data: Map<String, String>, keys: List<String>,
        boldCols: Set<Int> = emptySet(), leftCols: Set<Int> = emptySet(), size: Short = 10, font: String? = "Arial"
    ) {
        val r = row(sh, index, height)
        keys.forEachIndexed { i, k ->
            val style = s.cell(size = size, font = font, bold = i in boldCols,
                align = if (i in leftCols) HorizontalAlignment.LEFT else HorizontalAlignment.CENTER)
            num(r, i, data[k], style)
        }
    }

    private fun merge(sh: Sheet, r1: Int, r2: Int, c1: Int, c2: Int, text: String, style: CellStyle) {
        for (r in r1..r2) {
            val xr = row(sh, r)
            for (c in c1..c2) (xr.getCell(c) ?: xr.createCell(c)).cellStyle = style
        }
        sh.getRow(r1).getCell(c1).setCellValue(text)
        if (r1 != r2 || c1 != c2) sh.addMergedRegion(CellRangeAddress(r1, r2, c1, c2))
    }

    private fun picture(wb: XSSFWorkbook, sh: Sheet, path: String, c1: Int, r1: Int, c2: Int, r2: Int): Boolean = runCatching {
        val bytes = compressedJpeg(path) ?: return false
        val idx = wb.addPicture(bytes, Workbook.PICTURE_TYPE_JPEG)
        val anchor = XSSFClientAnchor(EMU_PER_PIXEL * 4, EMU_PER_PIXEL * 4, 0, 0, c1, r1, c2, r2)
        anchor.anchorType = ClientAnchor.AnchorType.MOVE_AND_RESIZE
        (sh as XSSFSheet).createDrawingPatriarch().createPicture(anchor, idx)
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

    private companion object { const val EMU_PER_PIXEL = 9525 }

    /** Cell style cache mirroring the web app's styleHeaderCell / applyBorderGrid helpers. */
    private class Styler(private val wb: XSSFWorkbook) {
        private val cache = HashMap<String, XSSFCellStyle>()

        private fun color(argb: String): XSSFColor {
            val v = argb.takeLast(6)
            val rgb = byteArrayOf(v.substring(0, 2).toInt(16).toByte(), v.substring(2, 4).toInt(16).toByte(), v.substring(4, 6).toInt(16).toByte())
            return XSSFColor(rgb, null)
        }

        private fun borders(st: XSSFCellStyle, argb: String) {
            val c = color(argb)
            st.setBorderTop(BorderStyle.THIN); st.setBorderBottom(BorderStyle.THIN); st.setBorderLeft(BorderStyle.THIN); st.setBorderRight(BorderStyle.THIN)
            st.setTopBorderColor(c); st.setBottomBorderColor(c); st.setLeftBorderColor(c); st.setRightBorderColor(c)
        }

        fun header(bg: String, white: Boolean = true, size: Short = 10, gridBorder: Boolean = false): XSSFCellStyle =
            cache.getOrPut("h|$bg|$white|$size|$gridBorder") {
                wb.createCellStyle().apply {
                    setFillForegroundColor(color(bg)); fillPattern = FillPatternType.SOLID_FOREGROUND
                    setFont(wb.createFont().apply { fontName = "Arial"; fontHeightInPoints = size; bold = true; setColor(color(if (white) "FFFFFFFF" else "FF1E293B")) })
                    alignment = HorizontalAlignment.CENTER; verticalAlignment = VerticalAlignment.CENTER; wrapText = true
                    borders(this, if (gridBorder) "FFCBD5E1" else "FFFFFFFF")
                }
            }

        fun cell(
            size: Short = 10, bold: Boolean = false, italic: Boolean = false, fontColor: String? = null, fill: String? = null,
            align: HorizontalAlignment = HorizontalAlignment.CENTER, font: String? = "Arial"
        ): XSSFCellStyle = cache.getOrPut("c|$size|$bold|$italic|$fontColor|$fill|$align|$font") {
            wb.createCellStyle().apply {
                borders(this, "FFCBD5E1")
                alignment = align; verticalAlignment = VerticalAlignment.CENTER
                if (fill != null) { setFillForegroundColor(color(fill)); fillPattern = FillPatternType.SOLID_FOREGROUND }
                if (font != null || bold || italic || fontColor != null) {
                    setFont(wb.createFont().apply {
                        font?.let { fontName = it; fontHeightInPoints = size }
                        this.bold = bold; this.italic = italic
                        fontColor?.let { setColor(color(it)) }
                    })
                }
            }
        }

        fun plain(bold: Boolean = false, align: HorizontalAlignment = HorizontalAlignment.LEFT): XSSFCellStyle =
            cache.getOrPut("p|$bold|$align") {
                wb.createCellStyle().apply {
                    alignment = align; verticalAlignment = VerticalAlignment.CENTER
                    setFont(wb.createFont().apply { fontName = "Arial"; fontHeightInPoints = 10; this.bold = bold })
                }
            }
    }
}
