package com.technavious.om15.data.schema

import com.technavious.om15.data.model.TestType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

// ---------- column shorthands ----------

private fun text(k: String, h: String, default: String = "") = Col(k, h, ColType.TEXT, default = default)
private fun num(k: String, h: String, unit: String? = null, default: String = "") = Col(k, h, ColType.NUMBER, unit, default)
private fun date(k: String, h: String) = Col(k, h, ColType.DATE)
private fun long(k: String, h: String) = Col(k, h, ColType.LONGTEXT)
private fun sel(k: String, h: String, options: List<String>, default: String = "") = Col(k, h, ColType.SELECT, default = default, options = options)
private fun calc(k: String, h: String, unit: String? = null) = Col(k, h, ColType.CALC, unit)
private fun ro(k: String, h: String) = Col(k, h, ColType.TEXT, readOnly = true)
private fun check(k: String, h: String, default: String = "true") = Col(k, h, ColType.CHECK, default = default)
private fun image(k: String, h: String) = Col(k, h, ColType.IMAGE)

private fun h(text: String, span: Int = 1, rows: Int = 1) = HCell(text, span, rows)

private val today: String get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
private val utcDate: String get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
private val currentYear get() = Calendar.getInstance().get(Calendar.YEAR).toString()

private fun personnel(dateDefault: Boolean = false) = listOf(
    text("preparedBy", "Prepared By"), text("testedBy", "Tested By"), text("testerEmail", "Tester Email"),
    text("witnessedBy", "Witnessed By"), Col("testDate", "Test Date", ColType.DATE, default = if (dateDefault) today else "")
)

private val STATUS4 = listOf("PENDING", "IN_PROGRESS", "PASS", "FAIL")

private fun obsTable(title: String = "Observations & Recommendations") = TableSchema(
    "observations", title,
    listOf(long("observation", "Observation"), long("recommendation", "Recommendation")),
    serial = "SI. No"
)

/** S. No | Instrument Name | Purpose | Make | Model | Calibration Date (Issue / Expiry) — Vibration & CFM. */
private fun toolsWithPurpose(title: String, upper: Boolean) = TableSchema(
    "tools", title,
    listOf(
        text("instrumentName", if (upper) "INSTRUMENT NAME" else "Instrument Name"), text("purpose", if (upper) "PURPOSE" else "Purpose"),
        text("make", if (upper) "MAKE" else "Make"), text("model", if (upper) "MODEL" else "Model"),
        date("calibrationIssue", if (upper) "ISSUE" else "Issue"), date("calibrationExpiry", if (upper) "EXPIRY" else "Expiry")
    ),
    serial = if (upper) "S. NO" else "S. No",
    headerRows = if (upper) listOf(
        listOf(h("S. NO", rows = 2), h("INSTRUMENT NAME", rows = 2), h("PURPOSE", rows = 2), h("MAKE", rows = 2), h("MODEL", rows = 2), h("CALIBRATION DATE", 2)),
        listOf(h("ISSUE"), h("EXPIRY"))
    ) else listOf(
        listOf(h("S. No", rows = 2), h("Instrument Name", rows = 2), h("Purpose", rows = 2), h("Make", rows = 2), h("Model", rows = 2), h("Calibration Date", 2)),
        listOf(h("Issue"), h("Expiry"))
    )
)

/** SR. NO | INSTRUMENT NAME | MAKE | MODEL | SERIAL NUMBER | CALIBRATION DATE (ISSUE / EXPIRY) — Thermography, Capacity, PQ. */
private fun toolsWithSerial(title: String, serialHeader: String, calHeader: String, names: List<String>) = TableSchema(
    "tools", title,
    listOf(
        text("instrumentName", names[0]), text("make", names[1]), text("model", names[2]), text("serialNumber", names[3]),
        date("calibrationIssue", "Issue"), date("calibrationExpiry", "Expiry")
    ),
    serial = serialHeader,
    headerRows = listOf(
        listOf(h(serialHeader, rows = 2)) + names.map { h(it, rows = 2) } + h(calHeader, 2),
        listOf(h("ISSUE"), h("EXPIRY"))
    )
)

/** EQUIPMENT NAME | MAKE | MODEL | SERIAL NO | CALIBRATION DATE | EXPIRY DATE — Battery, LCA, Power Factor. */
private fun equipmentList(title: String, serial: String?, headers: List<String>, textDates: Boolean) = TableSchema(
    "tools", title,
    listOf(
        text("name", headers[0]), text("make", headers[1]), text("model", headers[2]), text("serialNo", headers[3]),
        if (textDates) text("calDate", headers[4]) else date("calDate", headers[4]),
        if (textDates) text("expDate", headers[5]) else date("expDate", headers[5])
    ),
    serial = serial
)

private fun fields(vararg cols: Col) = cols.toList()

// =====================================================================
// 1. Vibration analysis
// =====================================================================

private val vibrationLegend = mapOf(
    "GOOD" to Pair(
        "1. All units are within ISO acceptable vibration limits.\n2. No abnormal vibration trend observed in H/V/A directions.\n3. Equipment operating in stable dynamic condition.",
        "1. Continue routine condition monitoring as per PM schedule.\n2. Record baseline trends for future comparison."
    ),
    "Satisfactory" to Pair(
        "1. Vibration levels are within permissible ISO limits but approaching upper band.\n2. Slight increase observed in one or more measurement directions (H/V/A).\n3. No immediate mechanical fault indication.",
        "1. Monitor vibration trend closely.\n2. Inspect during next planned shutdown.\n3. Check for early signs of misalignment or looseness."
    ),
    "Unsatisfactory" to Pair(
        "1. Vibration levels exceed satisfactory range as per ISO limits.\n2. Indicates early mechanical degradation (possible imbalance, misalignment, or looseness).\n3. Trend shows increasing deviation from baseline.",
        "1. Conduct detailed vibration analysis.\n2. Inspect coupling alignment, foundation tightness, and bearing condition.\n3. Plan corrective maintenance in nearest shutdown window."
    ),
    "Unacceptable" to Pair(
        "1. Vibration levels exceed permissible ISO limits indicating critical condition.\n2. High risk of mechanical failure if operation continues.\n3. Abnormal vibration detected in one or more directions (H/V/A).",
        "1. Immediate corrective action required.\n2. Reduce load or shut down equipment if possible.\n3. Perform urgent inspection: alignment correction, bearing replacement, structural tightening before restart."
    )
)

private val vibration = TestSchema(
    blocks = listOf(
        FieldsBlock("Personnel & Test Schedule", personnel()),
        TableBlock(toolsWithPurpose("TOOLS USED FOR AUDIT", upper = false)),
        TableBlock(TableSchema(
            "summary", "VIBRATION MEASUREMENT SUMMARY",
            listOf(
                text("equipmentId", "Equipment ID"),
                sel("classDetails", "Class Details", listOf("Class I", "Class II", "Class III", "Class IV")),
                num("vertical", "Vertical", "mm/s"), num("horizontal", "Horizontal", "mm/s"), num("axial", "Axial", "mm/s"),
                calc("maxValue", "Max Value", "mm/s"), calc("condition", "Vibration Condition")
            ),
            headerRows = listOf(
                listOf(h("Equipment ID", rows = 2), h("Class Details", rows = 2), h("Measured Vibration (mm/s)", 4), h("Vibration Condition", rows = 2)),
                listOf(h("Vertical"), h("Horizontal"), h("Axial"), h("Max Value (mm/s)"))
            ),
            initialRows = listOf("PAHU 01", "PAHU 02", "PAHU 03", "PAHU 04").map { mapOf("groupName" to "OLD DC", "equipmentId" to it) }
        ) { r, _ ->
            val max = maxOf(n0(r["vertical"]), n0(r["horizontal"]), n0(r["axial"]))
            r["maxValue"] = if (max > 0) jsNum(max) else ""
            r["condition"] = vibrationCondition(r["classDetails"], r["maxValue"]).ifEmpty { r["condition"].orEmpty().ifEmpty { "GOOD" } }
        }),
        SummaryBlock("ISO Condition Legend", listOf("Condition", "No. of Units", "Unit Tags", "Observation", "Recommendation")) { doc ->
            val rows = doc.rows("summary")
            listOf("GOOD" to "Good", "Satisfactory" to "Satisfactory", "Unsatisfactory" to "Unsatisfactory", "Unacceptable" to "Unacceptable")
                .mapNotNull { (key, label) ->
                    val matching = rows.filter { it["condition"].orEmpty().ifEmpty { "GOOD" }.equals(key, true) }
                    if (matching.isEmpty()) null else listOf(
                        label, matching.size.toString(),
                        matching.map { it["equipmentId"].orEmpty() }.filter { it.isNotBlank() }.joinToString("\n").ifEmpty { "-" },
                        vibrationLegend.getValue(key).first, vibrationLegend.getValue(key).second
                    )
                }
        },
        TableBlock(obsTable())
    ),
    groupSets = listOf(GroupSet("groupName", "Group", "NEW SECTION", listOf("summary"))),
    fileName = { _, loc -> "${loc.ifBlank { "Vibration_Analysis" }}_Report_$utcDate" }
)

// =====================================================================
// 2. Thermography
// =====================================================================

private val thermography = TestSchema(
    blocks = listOf(
        FieldsBlock("Personnel", personnel()),
        TableBlock(toolsWithSerial("TOOLS USED & CALIBRATION DETAILS", "SR. NO", "CALIBRATION DATE (ISSUE / EXPIRY)",
            listOf("INSTRUMENT NAME", "MAKE", "MODEL", "SERIAL NUMBER"))),
        TableBlock(TableSchema(
            "inspections", "THERMOGRAPHY INSPECTION",
            listOf(text("roomName", "ROOM NAME"), text("panelName", "PANEL NAME"), text("feederName", "FEEDER NAME"),
                num("maxTemp", "MAX TEMP °C"), num("minTemp", "MIN TEMP °C"), text("remarks", "REMARKS")),
            serial = "S.NO"
        )),
        TableBlock(TableSchema(
            "images", "CRITICAL IMAGES",
            listOf(text("roomName", "ROOM NAME"), text("panelName", "PANEL NAME"), text("feederName", "FEEDER NAME"),
                image("imageUrl", "CRITICAL IMAGES"), text("remarks", "REMARKS")),
            serial = "S.NO"
        )),
        FieldsBlock("OBSERVATIONS & RECOMMENDATIONS", fields(long("observations", "TEST OBSERVATIONS"), long("recommendations", "RECOMMENDATIONS")))
    ),
    fileName = { _, _ -> "Thermography_Report_${System.currentTimeMillis()}" }
)

// =====================================================================
// 3. Battery impedance
// =====================================================================

private fun batteryStatus(r: Row): String {
    val mir = n(r["measuredIr"]); val fth = n(r["irFailThresh"]); val wth = n(r["irWarnThresh"])
    val mv = n(r["measuredVolt"]); val vth = n(r["voltThresh"])
    return when {
        mir == null && mv == null -> "..."
        (mir != null && fth != null && mir >= fth) || (mv != null && vth != null && mv < vth) -> "FAIL"
        mir != null && wth != null && mir >= wth -> "WARN"
        else -> "PASS"
    }
}

private val battery = TestSchema(
    blocks = listOf(
        FieldsBlock("Personnel", personnel()),
        TableBlock(equipmentList("SECTION 1: TOOLS USED FOR AUDIT", null,
            listOf("EQUIPMENT NAME", "MAKE", "MODEL", "SERIAL NO", "CALIB. DATE", "EXPIRY DATE"), textDates = true)),
        TableBlock(TableSchema(
            "upsInfo", "UPS SYSTEM",
            listOf(text("location", "LOCATION"), text("make", "MAKE/MODEL"), text("batteryType", "BATTERY TYPE")),
            canAddRows = false
        )),
        TableBlock(TableSchema(
            "readings", "BATTERY STRINGS",
            listOf(
                text("stringName", "String", default = "STRING 1"),
                num("measuredIr", "Measured IR", "mΩ"), num("irFailThresh", "Fail Thresh", default = "13"),
                num("irWarnThresh", "Warn Thresh", default = "8"), num("measuredVolt", "Voltage", "V"),
                num("voltThresh", "Volt Thresh", default = "12.1"), calc("remarks", "STATUS")
            ),
            sections = listOf(Section(null, listOf("measuredIr", "irFailThresh", "irWarnThresh", "measuredVolt", "voltThresh", "remarks"),
                listOf(listOf(h("No"), h("Measured IR (mΩ)"), h("Fail Thresh"), h("Warn Thresh"), h("Voltage (V)"), h("Volt Thresh"), h("STATUS"))),
                serial = "No")),
            subGroupCol = "stringName"
        ) { r, _ -> r["remarks"] = batteryStatus(r) }),
        SummaryBlock("OBSERVATIONS", listOf("UPS System", "String", "Battery No", "Measured IR", "Measured Voltage", "Condition")) { doc ->
            doc.rows("readings").groupBy { it["upsName"].orEmpty() to it["stringName"].orEmpty() }.flatMap { (key, rows) ->
                rows.mapIndexedNotNull { i, r ->
                    val st = r["remarks"].orEmpty()
                    if (st == "PASS" || st == "..." || st.isEmpty()) null
                    else listOf(key.first, key.second, (i + 1).toString(), "${r["measuredIr"].orEmpty()} mΩ", "${r["measuredVolt"].orEmpty()} V", st)
                }
            }
        },
        FieldsBlock("TECHNICAL RECOMMENDATIONS", fields(long("recommendations", "TECHNICAL RECOMMENDATIONS")))
    ),
    groupSets = listOf(GroupSet("upsName", "UPS System", "UPS-{n}", listOf("upsInfo", "readings"))),
    fileName = { doc, _ ->
        val first = doc.rows("readings").firstOrNull()
        "Battery_Impedance_${first?.get("upsName").orEmpty().ifBlank { "UPS" }}_${first?.get("stringName").orEmpty().ifBlank { "String" }}"
    }
)

// =====================================================================
// 4. Earth station / earth continuity
// =====================================================================

private val earth = TestSchema(
    blocks = listOf(
        FieldsBlock("Personnel & Test Schedule", personnel()),
        TableBlock(TableSchema(
            "tools", "Table: Test Equipment Used",
            listOf(text("name", "Equipment name"), text("make", "Make"), text("serial", "Serial number"), text("model", "Model"),
                date("calDate", "Calibration date"), date("expDate", "Expiry date")),
            serial = "Sr.No."
        )),
        TableBlock(TableSchema(
            "readings", "Table: Measuring Details (Readings)",
            listOf(text("pitNo", "Earth pit no", default = "EP-{n}"), text("description", "Earthing description"),
                num("measuredValue", "Measured value (Ω)"), num("limitValue", "Limit value (Ω)", default = "2"), calc("remarks", "Remarks"))
        ) { r, _ ->
            val mv = n(r["measuredValue"]); val lv = n(r["limitValue"])
            r["remarks"] = when {
                mv != null && lv != null && mv > lv -> "High Resistance - Review Required"
                mv != null -> "ok"
                else -> r["remarks"].orEmpty().ifEmpty { "ok" }
            }
        }),
        FieldsBlock("Observations & Recommendations", fields(long("observations", "Test Observations"), long("recommendations", "Professional Recommendations")))
    ),
    fileName = { doc, _ -> "Earth_Continuity_Report_${doc.fields["testDate"].orEmpty().ifBlank { utcDate }}" }
)

// =====================================================================
// 5. Rack cooling index
// =====================================================================

private fun rciAvgFront(r: Row) = n(r["avgFront"])
private fun rciDelta(r: Row) = n(r["deltaT"])

private val rci = TestSchema(
    blocks = listOf(
        FieldsBlock("Personnel & Execution Metadata", personnel()),
        FieldsBlock("Tools used for audit", fields(date("toolCalibrationDate", "Calibration Date"), date("toolExpiryDate", "Expiry Date"))),
        TableBlock(TableSchema(
            "temperatures", "Temperature Measurement Portfolio",
            listOf(
                text("cacRef", "CAC Ref"), text("rackRef", "Rack Reference"),
                num("front15U", "Front 15U"), num("front22U", "Front 22U"), num("front35U", "Front 35U"),
                calc("avgFront", "Front Avg Temp"),
                num("rhFront15U", "Front RH 15U", "%"), num("rhFront22U", "Front RH 22U", "%"), num("rhFront35U", "Front RH 35U", "%"),
                calc("avgRhFront", "Front Avg RH", "%"),
                num("rear15U", "Rear 15U"), num("rear22U", "Rear 22U"), num("rear35U", "Rear 35U"),
                calc("avgRear", "Rear Avg Temp"),
                num("rhRear15U", "Rear RH 15U", "%"), num("rhRear22U", "Rear RH 22U", "%"), num("rhRear35U", "Rear RH 35U", "%"),
                calc("avgRhRear", "Rear Avg RH", "%"),
                calc("deltaT", "Delta T (F or C)"), calc("inletTempResult", "Inlet Temp Result (18-27)"),
                calc("excessInletTemp", "Excess Inlet Temp"), calc("deficitInletTemp", "Deficit Inlet Temp")
            ),
            serial = "S.No",
            headerRows = listOf(
                listOf(h("S.No", rows = 2), h("CAC Ref", rows = 2), h("Rack Reference", rows = 2),
                    h("Front End Temp (F or C)", 3), h("Avg Temp", rows = 2), h("Front End RH %", 3), h("Avg RH %", rows = 2),
                    h("Rear End Temp (F or C)", 3), h("Avg Temp", rows = 2), h("Rear End RH %", 3), h("Avg RH %", rows = 2),
                    h("Delta T (F or C)", rows = 2), h("Inlet Temp Result (18-27)", rows = 2), h("Excess Inlet Temp", rows = 2), h("Deficit Inlet Temp", rows = 2)),
                List(4) { listOf(h("15U"), h("22U"), h("35U")) }.flatten()
            )
        ) { r, _ ->
            if (r["cacRef"].isNullOrBlank()) r["cacRef"] = r["cacName"].orEmpty()
            val f = avgOf(r["front15U"], r["front22U"], r["front35U"])
            val b = avgOf(r["rear15U"], r["rear22U"], r["rear35U"])
            r["avgFront"] = f?.let { fixed(it, 2) } ?: ""
            r["avgRear"] = b?.let { fixed(it, 2) } ?: ""
            r["avgRhFront"] = avgOf(r["rhFront15U"], r["rhFront22U"], r["rhFront35U"])?.let { fixed(it, 2) } ?: ""
            r["avgRhRear"] = avgOf(r["rhRear15U"], r["rhRear22U"], r["rhRear35U"])?.let { fixed(it, 2) } ?: ""
            r["deltaT"] = if (f != null && b != null) fixed(abs(n0(r["avgRear"]) - n0(r["avgFront"])), 2) else ""
            val avgF = n(r["avgFront"])
            r["inletTempResult"] = ""; r["excessInletTemp"] = ""; r["deficitInletTemp"] = ""
            if (avgF != null) when {
                avgF in 18.0..27.0 -> r["inletTempResult"] = "PASS"
                avgF < 18 -> { r["inletTempResult"] = "FAIL"; r["deficitInletTemp"] = fixed(18 - avgF, 2) }
                else -> { r["inletTempResult"] = "FAIL"; r["excessInletTemp"] = fixed(avgF - 27, 2) }
            }
        }),
        SummaryBlock("Global RCI Assessment — Results Table 1", listOf("Parameter", "Result")) { doc ->
            val rows = doc.rows("temperatures").filter { rciAvgFront(it) != null }
            if (rows.isEmpty()) return@SummaryBlock emptyList()
            val f = rows.map { rciAvgFront(it)!! }
            listOf(
                listOf("Total No. of Racks (N)", rows.size.toString()),
                listOf("Summation of excess temperature (Ti − Tr, Hi)", fixed(f.filter { it > 27 }.sumOf { it - 27 }, 2)),
                listOf("No. of Racks above 27 deg C (Nhi)", f.count { it > 27 }.toString()),
                listOf("Summation of deficit temperature (Tr, Lo − Ti)", fixed(f.filter { it < 18 }.sumOf { 18 - it }, 2)),
                listOf("No. of Racks below 18 deg C (Nlo)", f.count { it < 18 }.toString()),
                listOf("No. of Racks above 15 deg C Delta T", doc.rows("temperatures").count { (rciDelta(it) ?: 0.0) > 15 }.toString()),
                listOf("No. of Racks below 5 deg C Delta T", doc.rows("temperatures").count { rciDelta(it)?.let { d -> d < 5 } == true }.toString())
            )
        },
        SummaryBlock("Global RCI Assessment — Results Table 2", listOf("Parameter", "Result", "RCI Rating")) { doc ->
            val f = doc.rows("temperatures").mapNotNull { rciAvgFront(it) }
            if (f.isEmpty()) return@SummaryBlock emptyList()
            val nRacks = f.size.toDouble()
            val hi = (1 - f.filter { it > 27 }.sumOf { it - 27 } / (nRacks * 5)) * 100
            val lo = (1 - f.filter { it < 18 }.sumOf { 18 - it } / (nRacks * 3)) * 100
            val overall = (hi + lo) / 2
            fun rating(v: Double) = when { v == 100.0 -> "Excellent"; v >= 96 -> "Good"; v >= 91 -> "Fair"; else -> "Poor" }
            listOf(
                listOf("RCI Hi — High Side (Over-Temperature)", "${fixed(hi, 1)}%", rating(hi)),
                listOf("RCI Lo — Low Side (Under-Temperature)", "${fixed(lo, 1)}%", rating(lo)),
                listOf("Overall RCI ((RCI Hi + RCI Lo)/2)", "${fixed(overall, 1)}%", rating(overall))
            )
        },
        TableBlock(obsTable("Professional Findings Summary"))
    ),
    groupSets = listOf(GroupSet("cacName", "CAC Unit", "CAC-{nn}", listOf("temperatures"))),
    fileName = { doc, loc -> "${doc.rows("temperatures").firstOrNull()?.get("cacName").orEmpty().ifBlank { loc.ifBlank { "CAC" } }}_Temperature_Study_$utcDate" }
)

// =====================================================================
// 6. CFM airflow
// =====================================================================

private fun cfmTotals(rows: List<Row>): Triple<Double, Double, Double> {
    var w = 0.0; var req = 0.0; var act = 0.0
    rows.forEach { r ->
        w += n0(r["lLoadW"]) + n0(r["rLoadW"])
        req += n0(r["lReqRaw"]) + n0(r["rReqRaw"])
        act += n0(r["lActualCFM"]) + n0(r["rActualCFM"])
    }
    return Triple(w, req, act)
}

private val cfm = TestSchema(
    blocks = listOf(
        FieldsBlock("Personnel", personnel()),
        TableBlock(toolsWithPurpose("TOOLS USED FOR AUDIT", upper = true)),
        FieldsBlock("Global Inputs", fields(
            num("deltaT", "Rack Delta", default = "13"), num("multiplier", "Envelop Multiplier", default = "1"),
            num("importedPahuCfm", "Return Airflow for All PAHU (CFM)"), num("importedPacCfm", "Return Airflow for All PAC (CFM)"),
            num("importedPahuTr", "Total TR for PAHU"), num("importedPacTr", "Total TR for PAC")
        )),
        TableBlock(TableSchema(
            "racks", "CAC AIRFLOW",
            listOf(
                text("lRackRef", "Left Rack Reference"), num("lLoadW", "Left Rack Load", "W"), num("lActualCFM", "Left Actual CFM"),
                calc("lReq", "Left Required CFM"), calc("lDeficit", "Left Deficit"),
                text("rRackRef", "Right Rack Reference"), num("rLoadW", "Right Rack Load", "W"), num("rActualCFM", "Right Actual CFM"),
                calc("rReq", "Right Required CFM"), calc("rDeficit", "Right Deficit")
            ),
            sections = listOf(
                Section(null, listOf("lRackRef", "lLoadW", "lActualCFM", "lReq", "rRackRef", "rLoadW", "rActualCFM", "rReq"),
                    listOf(
                        listOf(h("CAC", 4), h("TABLE 1", 4)),
                        listOf(h("RACK REFERENCE"), h("RACK LOAD W"), h("ACTUAL CFM"), h("REQUIRED CFM"), h("RACK REFERENCE"), h("RACK LOAD W"), h("ACTUAL CFM"), h("REQUIRED CFM"))
                    ),
                    totals = { rows, _ ->
                        val l = roundStr(rows.sumOf { n0(it["lReqRaw"]) }); val r = roundStr(rows.sumOf { n0(it["rReqRaw"]) })
                        val all = roundStr(rows.sumOf { n0(it["lReqRaw"]) + n0(it["rReqRaw"]) })
                        listOf(
                            listOf(h("SUB TOTAL", 3), h(l), h("SUB TOTAL", 3), h(r)),
                            listOf(h("TOTAL CFM REQUIRED", 3), h(l), h("TOTAL CFM REQUIRED", 3), h(r)),
                            listOf(h("TOTAL SUPPLY CFM", 7), h(all))
                        )
                    }),
                Section("Preview", listOf("lLoadW", "lActualCFM", "lReq", "lDeficit", "rLoadW", "rActualCFM", "rReq", "rDeficit"),
                    listOf(
                        listOf(h(""), h("R 0 1", 4), h("R 0 2", 4)),
                        listOf(h("GRILL"), h("RACK LOAD"), h("ACTUAL"), h("REQUIRED"), h("DEFICIT"), h("RACK LOAD"), h("ACTUAL"), h("REQUIRED"), h("DEFICIT"))
                    ),
                    serial = "GRILL",
                    totals = { rows, _ ->
                        fun side(p: String): List<HCell> {
                            val load = rows.sumOf { n0(it["${p}LoadW"]) }; val act = rows.sumOf { n0(it["${p}ActualCFM"]) }
                            val req = rows.sumOf { n0(it["${p}ReqRaw"]) }
                            return listOf(h(roundStr(load)), h(roundStr(act)), h(roundStr(req)), h(roundStr(act - req)))
                        }
                        listOf(listOf(h("SUBTOTAL")) + side("l") + side("r"))
                    })
            )
        ) { r, f ->
            listOf("l", "r").forEach { p ->
                val req = cfmRequired(r["${p}LoadW"], f["deltaT"], f["multiplier"])
                r["${p}ReqRaw"] = req.toString()
                r["${p}Req"] = if (req > 0) cfmFmt(req) else ""
                r["${p}Deficit"] = if (req > 0 || n(r["${p}ActualCFM"]) != null) cfmFmt(n0(r["${p}ActualCFM"]) - req) else ""
            }
        }),
        SummaryBlock("CFM SUMMARY ANALYSIS", emptyList()) { doc ->
            val m = n(doc.fields["multiplier"])?.takeIf { it != 0.0 } ?: 1.0
            val header = listOf("REF", "RACK (KW)", "RACK (TR)",
                "REQUIRED MINIMUM AIRFLOW @ ${doc.fields["deltaT"].orEmpty().ifBlank { "13" }} DEG C RACK DELTA & ${Math.round((m - 1) * 100)}% ENVELOPE MULTIPLIER",
                "ACTUAL CFM", "DEFICIT")
            val groups = doc.rows("racks").groupBy { it["cacName"].orEmpty() }
            val rows = groups.map { (cac, rs) ->
                val (w, req, act) = cfmTotals(rs)
                listOf(cac, cfmFmt(w / 1000), cfmFmt(w / 3517), roundStr(req), roundStr(act), roundStr(act - req))
            }
            val total = if (groups.size > 1) {
                val (w, req, act) = cfmTotals(doc.rows("racks"))
                listOf(listOf("TOTAL", fixed(w / 1000, 2), fixed(w / 3517, 2), roundStr(req), roundStr(act), roundStr(act - req)))
            } else emptyList()
            listOf(header) + rows + total
        },
        SummaryBlock("CFM Summary Analysis — Aggregate Performance Metrics",
            listOf("CATEGORY", "VALUE", "UNIT", "TOTAL AIRFLOW REQUIRED (CFM)", "TOTAL AIRFLOW AVAILABLE (CFM)", "DEFICIT (CFM)")) { doc ->
            val (w, req, act) = cfmTotals(doc.rows("racks"))
            val m = n(doc.fields["multiplier"])?.takeIf { it != 0.0 } ?: 1.0
            val kw = w / 1000; val tr = w / 3517
            val pahu = n(doc.fields["importedPahuCfm"]) ?: act
            val pac = n(doc.fields["importedPacCfm"]) ?: act
            val pahuTr = n(doc.fields["importedPahuTr"]) ?: (tr * 1.07877)
            val pacTr = n(doc.fields["importedPacTr"]) ?: (tr * 0.66579)
            listOf(
                listOf("Total Rack Load", fixed(kw, 2), "kW", roundStr(req), roundStr(act), roundStr(act - req)),
                listOf("Total Rack TR", fixed(tr, 2), "TR", "", "", ""),
                listOf("Adding Room Load of ${doc.fields["multiplier"].orEmpty().ifBlank { "1" }}%", fixed(tr * m, 2), "TR", "", "", ""),
                listOf("Return Airflow for All PAHU", roundStr(pahu), "CFM", "", "", ""),
                listOf("Return Airflow for All PAC", roundStr(pac), "CFM", "", "", ""),
                listOf("Total TR for PAHU", fixed(pahuTr, 2), "TR", "", "", ""),
                listOf("Total TR for PAC", fixed(pacTr, 2), "TR", "", "", "")
            )
        },
        SummaryBlock("Airflow Summary", listOf("", "Measured (CFM)", "Required (CFM)", "Deficit (CFM)")) { doc ->
            val (_, req, act) = cfmTotals(doc.rows("racks"))
            val ret = (n(doc.fields["importedPahuCfm"]) ?: act) + (n(doc.fields["importedPacCfm"]) ?: act)
            listOf(
                listOf("Supply Airflow for DC Hall (Grills)", roundStr(act), roundStr(req), roundStr(act - req)),
                listOf("Return Airflow for DC Hall", roundStr(ret), roundStr(req), roundStr(ret - req))
            )
        },
        TableBlock(obsTable("Audit Assessment Summary"))
    ),
    groupSets = listOf(GroupSet("cacName", "CAC Table", "CAC-{nn}", listOf("racks"))),
    fileName = { doc, loc -> "${doc.rows("racks").firstOrNull()?.get("cacName").orEmpty().ifBlank { loc.ifBlank { "CAC" } }}_Data" }
)

// =====================================================================
// 7. Capacity assessment (PAHU / PAC)
// =====================================================================

private fun tr1(d: Double) = fixed(d, 1)

private val capacity = TestSchema(
    blocks = listOf(
        FieldsBlock("Personnel & Test Schedule", personnel() + listOf(num("itLoad", "IT Load (kW)"), calc("measuredPue", "Measured PUE"))),
        TableBlock(toolsWithSerial("TOOLS USED:", "SR. NO", "CALIBRATION DATE", listOf("INSTRUMENT NAME", "MAKE", "MODEL", "SERIAL NUMBER"))),
        TableBlock(TableSchema(
            "pahu", "PAHU Capacity assessment",
            listOf(
                text("equipmentId", "Equipment ID"), text("modelNo", "Model No"), text("serialNo", "Serial No"),
                num("designAirflow", "Design Airflow", "CFM"), num("designRaTemp", "Design RA Temp", "°C"), num("designSaTemp", "Design SA Temp", "°C"),
                num("designPower", "Design Power", "kW"), num("designWaterFlowRate", "Design Water Flow", "GPM"), calc("dTr", "Design Cooling Tonnage", "TR"),
                num("measuredAirflow", "Airflow", "CFM"), num("measuredSaTemp", "SA Temp", "°C"), num("measuredRaTemp", "RA Temp", "°C"),
                num("measuredFanSpeed", "Fan Speed", "%"), calc("mTr", "Cooling Tonnage (Air)", "TR"), num("measuredPower", "Power", "kW"),
                num("measuredChwIn", "CHW In", "°C"), num("measuredChwOut", "CHW Out", "°C"), num("measuredValveOpening", "Valve Opening", "%"),
                num("measuredWaterFlowRate", "Water Flow", "GPM"), calc("waterTr", "Cooling Tonnage (Water)", "TR")
            ),
            headerRows = listOf(
                listOf(h("Equipment ID", rows = 3), h("Model No", rows = 3), h("Serial No", rows = 3), h("Design", 6), h("Measured", 11)),
                listOf("Airflow", "RA Temp", "SA Temp", "Power", "Water Flow", "Cooling Tonnage", "Airflow", "SA Temp", "RA Temp", "Fan Speed",
                    "Cooling Tonnage (Air)", "Power", "CHW In", "CHW Out", "Valve Opening", "Water Flow", "Cooling Tonnage (Water)").map { h(it) },
                listOf("CFM", "(°C)", "(°C)", "kW", "GPM", "TR", "CFM", "(°C)", "(°C)", "%", "TR", "kW", "(°C)", "(°C)", "%", "GPM", "TR").map { h(it) }
            ),
            totals = { rows, _ ->
                if (rows.isEmpty()) emptyList()
                else listOf(listOf(h("Total", 9)) + listOf("measuredAirflow", "measuredSaTemp", "measuredRaTemp", "measuredFanSpeed", "mTr",
                    "measuredPower", "measuredChwIn", "measuredChwOut", "measuredValveOpening", "measuredWaterFlowRate", "waterTr")
                    .map { k -> h(tr1(rows.sumOf { n0(it[k]) })) })
            }
        ) { r, _ ->
            r["dTr"] = tr1(coolingTr(r["designAirflow"], r["designRaTemp"], r["designSaTemp"]))
            r["mTr"] = tr1(coolingTr(r["measuredAirflow"], r["measuredRaTemp"], r["measuredSaTemp"]))
            r["waterTr"] = tr1((n0(r["measuredWaterFlowRate"]) * abs(n0(r["measuredChwOut"]) - n0(r["measuredChwIn"])) * 1.8) / 24)
        }),
        TableBlock(TableSchema(
            "pac", "PAC capacity assessment",
            listOf(
                text("equipmentId", "Equipment ID"), text("modelNo", "Model No"), text("serialNo", "Serial No"),
                num("designAirflow", "Design Airflow", "CFM"), num("designRaTemp", "Design RA Temp", "°C"), num("designSaTemp", "Design SA Temp", "°C"),
                num("designPower", "Design Power", "kW"), calc("dTr", "Design Cooling Tonnage", "TR"), num("fanSpeed", "Fan Speed", "%"),
                num("measuredAirflow", "Airflow", "CFM"), num("measuredRaTemp", "RA Temp", "°C"), num("measuredSaTemp", "SA Temp", "°C"),
                calc("mTr", "Cooling Tonnage (Air Side)", "TR"), num("measuredPower", "Power", "kW"), calc("cop", "COP")
            ),
            headerRows = listOf(
                listOf(h("Equipment ID", rows = 3), h("Model No", rows = 3), h("Serial No", rows = 3), h("Design", 5), h("Fan Speed (%)", rows = 3), h("Measured", 5), h("COP", rows = 3)),
                listOf("Airflow", "RA Temp", "SA Temp", "Power", "Cooling Tonnage", "Airflow", "RA Temp", "SA Temp", "Cooling Tonnage (Air Side)", "Power").map { h(it) },
                listOf("CFM", "(°C)", "(°C)", "kW", "TR", "CFM", "(°C)", "(°C)", "TR", "kW").map { h(it) }
            ),
            totals = { rows, _ ->
                if (rows.isEmpty()) emptyList() else {
                    val trSum = rows.sumOf { n0(it["mTr"]) }; val power = rows.sumOf { n0(it["measuredPower"]) }
                    listOf(listOf(h("Total", 9)) + listOf("measuredAirflow", "measuredRaTemp", "measuredSaTemp").map { k -> h(tr1(rows.sumOf { n0(it[k]) })) } +
                        listOf(h(tr1(trSum)), h(tr1(power)), h(fixed(if (trSum != 0.0 && power != 0.0) trSum * 3.517 / power else 0.0, 2))))
                }
            }
        ) { r, _ ->
            val mTr = coolingTr(r["measuredAirflow"], r["measuredRaTemp"], r["measuredSaTemp"])
            val p = n(r["measuredPower"])
            r["dTr"] = tr1(coolingTr(r["designAirflow"], r["designRaTemp"], r["designSaTemp"]))
            r["mTr"] = tr1(mTr)
            r["cop"] = fixed(if (p != null && p != 0.0) mTr * 3.517 / p else 0.0, 2)
        }),
        SummaryBlock("IMPORT TABLE DATA (FOR CFM TEST)", listOf("CATEGORY", "TOTAL CFM VALUE", "TOTAL TR VALUE")) { doc ->
            fun sums(id: String) = doc.rows(id).let { rs -> rs.sumOf { n0(it["measuredAirflow"]) } to rs.sumOf { coolingTr(it["measuredAirflow"], it["measuredRaTemp"], it["measuredSaTemp"]) } }
            val (pahuCfm, pahuTr) = sums("pahu"); val (pacCfm, pacTr) = sums("pac")
            listOf(listOf("PAHU", fixed(pahuCfm, 2), fixed(pahuTr, 2)), listOf("PAC", fixed(pacCfm, 2), fixed(pacTr, 2)))
        },
        TableBlock(obsTable("OBSERVATIONS & RECOMMENDATIONS"))
    ),
    groupSets = listOf(
        GroupSet("tableTitle", "PAHU Table (6.1)", "6.1 PAHU Capacity assessment", listOf("pahu")),
        GroupSet("tableTitle", "PAC Table (6.2)", "6.2 PAC capacity assessment", listOf("pac"))
    ),
    fieldCalc = { doc ->
        val it = n(doc.fields["itLoad"])
        doc.fields["measuredPue"] = if (it != null && it > 0) fixed((it + doc.rows("pac").sumOf { r -> n0(r["measuredPower"]) }) / it, 2) else "-"
    },
    fileName = { _, loc -> "${loc.ifBlank { "Capacity_Assessment" }}_Report_$utcDate" }
)

// =====================================================================
// 8. Life cycle assessment
// =====================================================================

private val lcaStatusText = listOf(
    Triple("Fit for Service", "Equipment is in good operating condition with sufficient remaining life.", "Continue normal operations. Maintain routine preventive maintenance schedule."),
    Triple("Degrading Stage", "Early signs of ageing, performance still acceptable but degradation may begin.", "Increase preventive maintenance frequency and closely monitor performance."),
    Triple("Approaching Replacement", "Significant life consumption, Increases risk of low performance.", "Initiate replacement planning, allocate budget, and define shutdown schedule."),
    Triple("Reaching End-of-Life", "Equipment is near end of usable life, high risk of failure.", "Immediate replacement planning required. Prepare procurement and execution plan."),
    Triple("End-of-Life", "1. Equipment has exceeded its estimated useful life.", "Urgent replacement required. Operate only if critical, with contingency support.")
)

private val lifeCycle = TestSchema(
    blocks = listOf(
        FieldsBlock("Life Cycle Assessment Test", fields(sel("status", "Global Health Status", STATUS4, "PENDING"))),
        FieldsBlock("Personnel & Execution Schedule", personnel()),
        TableBlock(equipmentList("LIST OF TESTING EQUIPMENT (CALIBRATION DETAILS)", "S. No",
            listOf("EQUIPMENT NAME", "MAKE", "MODEL", "SERIAL NO", "CALIBRATION DATE", "EXPIRY DATE"), textDates = true)),
        TableBlock(TableSchema(
            "lca", "4. Equipment Remaining Useful Life (RUL) Estimation",
            listOf(
                text("equipmentTag", "Equipment Tag / Name"), text("makeModel", "Make & Model No"), text("serialNo", "Serial No"), text("location", "Location"),
                num("installYear", "Install Year"), num("currentYear", "Current Year", default = currentYear), calc("ageYrs", "Age (yrs)"),
                num("surfaceTemp", "Surface Temp (°C)"), image("thermalImageUrl", "Thermal Evidence"), num("vibrationVelocity", "Vibration (mm/s)"),
                num("vibrationScore", "Vibration Score (VS)"), num("corrosionScore", "Corrosion Score (CS)"), num("maintScore", "Maint. Score (MS)"),
                calc("healthIndex", "Health Index (HI)"), num("runningLifeYrs", "Running Life (yrs)"),
                calc("utilizationRatio", "Utilization Ratio (UR)"), calc("utilizationFactor", "Utilization Factor (UF)"),
                num("designLifeYrs", "Design Life (yrs)"), calc("adjustedLifeAL", "Adjusted Life (AL)"), calc("rulYrs", "RUL (yrs)"),
                calc("rulPct", "RUL (%)"), calc("lifeCycleStatus", "Life cycle Status"), text("remarksRul", "Remarks")
            ),
            sections = listOf(
                Section("4.1 Table-1 (Equipment Summary & Factor Calculation)", listOf("equipmentTag", "makeModel", "serialNo", "location", "installYear",
                    "currentYear", "ageYrs", "surfaceTemp", "thermalImageUrl", "vibrationVelocity", "vibrationScore", "corrosionScore", "maintScore",
                    "healthIndex", "runningLifeYrs", "utilizationRatio", "utilizationFactor"), serial = "Sl No"),
                Section("4.2 Table-2 (RUL Calculation)", listOf("equipmentTag", "designLifeYrs", "ageYrs", "healthIndex", "utilizationFactor",
                    "adjustedLifeAL", "rulYrs", "rulPct", "lifeCycleStatus", "remarksRul"), serial = "Sl. No")
            )
        ) { r, _ ->
            val install = n(r["installYear"]); val current = n(r["currentYear"])
            r["ageYrs"] = if (install != null && current != null) jsNum(current - install) else ""
            val scores = listOf(r["vibrationScore"], r["corrosionScore"], r["maintScore"])
            r["healthIndex"] = if (scores.any { !it.isNullOrBlank() }) fixed(0.4 * n0(scores[0]) + 0.3 * n0(scores[1]) + 0.3 * n0(scores[2]), 1) else ""
            val age = n0(r["ageYrs"])
            val ur = if (age > 0 && n(r["runningLifeYrs"]) != null) n0(r["runningLifeYrs"]) / age else null
            r["utilizationRatio"] = ur?.let { fixed(it, 2) } ?: ""
            r["utilizationFactor"] = when { ur == null -> "1.0"; ur > 0.8 -> "0.9"; ur >= 0.6 -> "0.95"; ur >= 0.4 -> "1.0"; else -> "1.05" }
            val dl = n0(r["designLifeYrs"]); val hi = n0(r["healthIndex"]); val uf = n(r["utilizationFactor"]) ?: 1.0
            val al = if (dl > 0 && hi > 0) n0(fixed(dl * hi * uf, 1)) else 0.0
            r["adjustedLifeAL"] = if (al > 0) fixed(al, 1) else ""
            val rul = if (al > 0) n0(fixed(al - age, 1)) else 0.0
            r["rulYrs"] = if (al > 0) fixed(rul, 1) else ""
            val pct = if (al > 0) rul / al * 100 else 0.0
            r["rulPct"] = if (al > 0) fixed(pct, 1) else ""
            r["lifeCycleStatus"] = rulStatus(pct, al)
        }),
        SummaryBlock("OBSERVATIONS & RECOMMENDATIONS", listOf("Life cycle Status", "No. of Equipment", "Equipment Tag / Name", "Observation", "Recommendation")) { doc ->
            val rows = doc.rows("lca")
            lcaStatusText.mapNotNull { (status, obs, rec) ->
                val m = rows.filter { it["lifeCycleStatus"] == status }
                if (m.isEmpty()) null else listOf(status, m.size.toString(),
                    m.joinToString("\n") { it["equipmentTag"].orEmpty().ifBlank { "Unnamed" } }, obs, rec)
            }
        }
    ),
    fileName = { _, _ -> "LCA_Evaluation_$utcDate" }
)

// =====================================================================
// 9. Power factor / capacitor health
// =====================================================================

private val powerFactor = TestSchema(
    blocks = listOf(
        FieldsBlock("Power Factor / Capacitor health assessment", fields(sel("status", "Global Status", STATUS4, "PENDING"))),
        FieldsBlock("Personnel & Test Schedule", personnel(dateDefault = true)),
        TableBlock(equipmentList("List of testing equipment (Calibration Detail)", null,
            listOf("EQUIPMENT NAME", "MAKE", "MODEL", "SERIAL NO", "CALIBRATION DATE", "EXPIRY DATE"), textDates = false)),
        TableBlock(TableSchema(
            "feeders", "APFC PANEL INSPECTION DETAILS",
            listOf(
                text("feederName", "FEEDER NAME"), num("bankKvar", "CAPACITOR BANK IN KVAR"), num("breakerRating", "BREAKER RATING IN A"),
                num("contactorRating", "CONTACTOR RATING IN A"), num("obtainedCap", "OBTAINED CAPACITANCE IN μF"),
                num("actualRY", "ACTUAL RY CAPACITANCE IN μF"), num("actualYB", "ACTUAL YB CAPACITANCE IN μF"), num("actualBR", "ACTUAL BR CAPACITANCE IN μF"),
                num("measuredKvar", "MEASURED KVAR"), text("acceptableLimit", "ACCEPTABLE LIMIT OF CAPACITANCE IN KVAR"),
                num("caseTemp", "CASE TEMPERATURE"), num("currentR", "R PHASE CURRENT IN AMP"), num("currentY", "Y PHASE CURRENT IN AMP"),
                num("currentB", "B PHASE CURRENT IN AMP"), text("remarks", "REMARKS")
            ),
            sections = listOf(
                Section(null, listOf("feederName", "bankKvar", "breakerRating", "contactorRating", "obtainedCap", "actualRY", "actualYB", "actualBR", "measuredKvar", "acceptableLimit")),
                Section(null, listOf("feederName", "caseTemp", "currentR", "currentY", "currentB", "remarks"))
            )
        )),
        FieldsBlock("Observations & Recommendations", fields(long("observations", "TEST OBSERVATIONS"), long("recommendations", "RECOMMENDATIONS")))
    ),
    groupSets = listOf(GroupSet("panelName", "Panel Section", "MLTP-{n} APFC", listOf("feeders"))),
    fileName = { doc, _ -> "Power_Factor_Assessment_${doc.fields["testDate"].orEmpty().ifBlank { utcDate }}" }
)

// =====================================================================
// 10. Power quality
// =====================================================================

private val PQ_REMARK = listOf("Pass", "Fail")

private fun pqTemplate(type: String): List<Map<String, String>> {
    fun p(param: String, phase: String = "") = mapOf("parameter" to param, "phase" to phase)
    val voltage = if (type == "CHILLER") "2. Phase to Neutral Voltage" to listOf("RN Phase", "YN Phase", "BN Phase")
    else "2. Phase to Phase Voltage" to listOf("RY Phase", "YB Phase", "BR Phase")
    return buildList {
        add(p("Monitoring Period", "From / To"))
        add(p("1. Power Frequency"))
        voltage.second.forEach { add(p(voltage.first, it)) }
        listOf("R Phase", "Y Phase", "B Phase", "Load Notes").forEach { add(p("3. Current Trend in Ampere", it)) }
        add(p("4. Actual Power Trend in KW")); add(p("5. Apparent Power Trend in KVA")); add(p("6. Reactive Power Trend in KVAR"))
        add(p("7. Power Factor Trend"))
        listOf("r", "y", "b").forEach { add(p("8. THDv Trend", it)) }
        listOf("r", "y", "b").forEach { add(p("9. THDi Trend", it)) }
        add(p("10. Unbalance Voltage Trend")); add(p("11. Unbalance Current Trend"))
    }.map { it + ("groupType" to type) }
}

private val powerQuality = TestSchema(
    blocks = listOf(
        FieldsBlock("PQ Personnel & Test Schedule", personnel()),
        TableBlock(toolsWithSerial("Instrument Calibration Detail", "Sr. No", "Calibration (Issue / Expiry)", listOf("Instrument Name", "Make", "Model", "Serial No"))),
        TableBlock(TableSchema(
            "metrics", "POWER QUALITY ANALYSIS",
            listOf(
                ro("parameter", "Parameter"), ro("phase", "Phase"), text("average", "Average Measurement"),
                num("max", "Max %"), num("min", "Min %"), text("limits", "Standard Limits"),
                sel("remark", "Remarks (Pass/Fail)", PQ_REMARK), image("imageUrl", "Trend Screenshot")
            ),
            canAddRows = false
        )),
        FieldsBlock("Executive Summary", fields(long("observations", "OVERALL OBSERVATIONS"), long("recommendations", "RECOMMENDATIONS")))
    ),
    groupSets = listOf(GroupSet("groupTitle", "Group", "{type}-{nn}", listOf("metrics"), options = listOf("MLTP", "CHILLER", "UPS")) { _, type -> pqTemplate(type) }),
    fileName = { _, loc -> "${loc.ifBlank { "Power_Quality" }}_Report_$utcDate" }
)

// =====================================================================
// 11. Power system study
// =====================================================================

private val TF_PARAMS = listOf("SPECIFICATION REFERENCE", "MAKE", "RATED IN KVA", "HV VOLTAGE IN VOLTS", "LV VOLTAGE IN VOLTS", "HV CURRENT IN AMPS",
    "LV CURRENT IN AMPS", "NO. OF PHASES", "FREQUENCY IN HZ", "TYPES OF COOLING", "IMPEDANCE VOLTAGE %", "CONNECTION SYMBOLS")

private fun relayTable(id: String, title: String, earth: Boolean): TableSchema {
    val plug = if (earth) "Plug Setting Ie>" else "Plug Setting (I>)"
    val tms = if (earth) "TMS Te>" else "TMS T>"
    val hiI = if (earth) "Hi Set (Ie>>)" else "Hi Set (I>>)"
    val hiT = if (earth) "Hi Set (te>>)" else "Hi Set (t>>)"
    val sPlug = if (earth) "Plug Setting (Ie>)" else "Plug Setting (I>)"
    val sHiT = if (earth) "Hi Set (te>>) sec" else "Hi Set (t>>)"
    return TableSchema(
        id, title,
        listOf(
            text("feeder", "Feeder Name"), text("relayNo", "Relay No"), text("ratedCurrent", "Rated Current (A)"), text("relayType", "Relay Type/ Make"),
            text("ctRatio", "CT Ratio"),
            text("exCurve", "Existing Curve", default = "SI"), text("exPlug", "Existing $plug"), text("exTms", "Existing $tms"),
            text("exOpTime", "Existing Operating time (s)"), text("exHiI", "Existing $hiI"), text("exHiT", "Existing $hiT"),
            text("suCurve", "Suggested Curve", default = "NI"), text("suPlug", "Suggested $sPlug"), text("suTms", "Suggested $tms"),
            text("suHiI", "Suggested $hiI"), text("suHiT", "Suggested $sHiT"), text("remarks", "Remarks")
        ),
        sections = listOf(Section(null,
            listOf("relayNo", "feeder", "ratedCurrent", "relayType", "ctRatio", "exCurve", "exPlug", "exTms", "exOpTime", "exHiI", "exHiT",
                "suCurve", "suPlug", "suTms", "suHiI", "suHiT", "remarks"),
            listOf(
                listOf(h("Relay No", rows = 3), h("Feeder Name", rows = 3), h("Rated Current (A)", rows = 3), h("Relay Type/ Make", rows = 3), h("CT Ratio", rows = 3),
                    h("Existing Settings", 6), h("Suggested Settings", 5), h("Remarks", rows = 3)),
                listOf(h("IDMT", 4), h("DT", 2), h("IDMT", 3), h("DT", 2)),
                listOf(h("Curve"), h(plug), h(tms), h("Operating time (s) for closein fault"), h(hiI), h(hiT), h("Curve"), h(sPlug), h(tms), h(hiI), h(sHiT))
            ))),
        sectionHeaders = true
    )
}

private val powerSystem = TestSchema(
    blocks = listOf(
        FieldsBlock("TEST PERSONNEL & SCHEDULE", fields(text("revisionNo", "REVISION NO."), text("preparedBy", "PREPARED BY"),
            text("testedBy", "TESTED BY"), text("approvedBy", "APPROVED BY"), date("testDate", "TEST DATE"))),
        TableBlock(TableSchema(
            "transformers", "7.1 TRANSFORMER NAME PLATE DETAILS.",
            listOf(text("header", "Transformer", default = "Transformer {n}")) + TF_PARAMS.mapIndexed { i, p -> text("p$i", p) },
            transposed = true
        )),
        TableBlock(TableSchema(
            "loadFlow", "LOAD FLOW CASE",
            listOf(text("location", "LOCATION"), num("busVoltKv", "BUS VOLTAGE IN KV"), num("powerMva", "POWER CONSUMPTION MVA"),
                num("realPowerMw", "REAL POWER CONSUMPTION MW"), num("reactivePowerMvar", "REACTIVE POWER CONSUMPTION MVAR"), num("pf", "POWER FACTOR")),
            sectionHeaders = true
        )),
        TableBlock(TableSchema(
            "shortCircuit", "SHORT CIRCUIT ANALYSIS",
            listOf(text("location", "LOCATION"), num("busVoltKv", "BUS VOLTAGE IN KV"), num("threePhaseKa", "3 PHASE FAULT CURRENT IN KA"),
                num("lineToGroundKa", "LINE TO GROUND FAULT CURRENT IN KA"), num("busRatingKa", "BUS RATINGS IN KA")),
            sectionHeaders = true
        )),
        TableBlock(relayTable("relayPhase", "RELAY COORDINATION - PHASE SETTINGS", earth = false)),
        TableBlock(relayTable("relayEarth", "RELAY COORDINATION - EARTH FAULT SETTINGS", earth = true)),
        TableBlock(TableSchema(
            "release", "415V_RELEASE SETTINGS",
            listOf(text("feederName", "Feeder Name"), text("slNo", "Sl. No."), text("ratedCurrent", "Rated current (A)"), text("relayMakeType", "Relay Make/ type")) +
                listOf("ex" to "Existing", "pr" to "Proposed").flatMap { (p, label) ->
                    listOf(text("${p}LtPickup", "$label Long Time Pickup (xIn)"), text("${p}LtTime", "$label Long Time Time (s)"),
                        text("${p}StPickup", "$label Short Time Pickup (xIn)"), text("${p}StTime", "$label Short Time Time (s)"),
                        text("${p}Inst", "$label Instantaneous (xIn)"), text("${p}EPickup", "$label Earth Pickup (xIn)"), text("${p}ETime", "$label Earth Time (s)"))
                } + text("remarks", "Remarks"),
            sections = listOf(Section(null,
                listOf("slNo", "feederName", "ratedCurrent", "relayMakeType", "exLtPickup", "exLtTime", "exStPickup", "exStTime", "exInst", "exEPickup", "exETime",
                    "prLtPickup", "prLtTime", "prStPickup", "prStTime", "prInst", "prEPickup", "prETime", "remarks"),
                listOf(
                    listOf(h("Sl. No.", rows = 3), h("Feeder Name", rows = 3), h("Rated current (A)", rows = 3), h("Relay Make/ type", rows = 3),
                        h("Existing Settings", 7), h("Proposed Settings", 7), h("Remarks", rows = 3)),
                    listOf(h("Long Time", 2), h("Short Time", 2), h("Instantaneous (xIn)", rows = 2), h("Earth", 2),
                        h("Long Time", 2), h("Short Time", 2), h("Instantaneous (xIn)", rows = 2), h("Earth", 2)),
                    listOf(h("Pickup (xIn)"), h("Time (s) at 3xIr"), h("Pickup (xIn)"), h("Time (s)"), h("Pickup (xIn)"), h("Time (s)"),
                        h("Pickup (xIn)"), h("Time (s) at 6xIr"), h("Pickup (xIn)"), h("Time (s)"), h("Pickup (xIn)"), h("Time (s)"))
                ))),
            sectionHeaders = true
        )),
        TableBlock(TableSchema(
            "arcFlash", "ARC FLASH ANALYSIS RESULTS",
            listOf(text("locationName", "Name"), num("nominalKv", "Nominal kV"), num("boltedKa", "Bolted kA"), num("arcingKa", "Arcing kA"),
                num("fctSecs", "FCT (Secs)"), num("incidentEnergy", "Incident Energy (Cal/cm²)"), num("afbM", "AFB (m)"),
                num("workingDistanceCm", "Working Distance (cm)"), text("energyLevel", "Energy Level"), text("remarks", "Remarks")),
            headerRows = listOf(
                listOf(h("Location", 2), h("Total Fault current", 3), h("Arc Flash Analysis Results", 4), h("Remarks", rows = 2)),
                listOf(h("Name"), h("Nominal kV"), h("Bolted kA"), h("Arcing kA"), h("FCT (Secs)"), h("Incident Energy (Cal/cm²)"), h("AFB (m)"), h("Working Distance (cm)"), h("Energy Level"))
            ),
            sectionHeaders = true
        )),
        FieldsBlock("Observations & Recommendations", fields(long("observations", "TEST OBSERVATIONS"), long("recommendations", "RECOMMENDATIONS")))
    ),
    fileName = { _, loc -> "${loc.ifBlank { "Power_System_Study" }}_Report_$utcDate" }
)

// =====================================================================
// 12. Arc flash
// =====================================================================

private val arcFlash = TestSchema(
    blocks = listOf(
        FieldsBlock("Personnel & Test Schedule", personnel(dateDefault = true)),
        TableBlock(TableSchema("ppe", "Table A: Arc Flash PPE Categories",
            listOf(text("category", "PPE cat."), text("minEnergy", "Min. incident energy cal/cm²"), text("clothing", "Arc rated clothing")))),
        TableBlock(TableSchema("gloves", "Table B: Glove Class Specifications",
            listOf(text("class", "Class of glove"), text("acProof", "AC proof-test voltage, RMS, V"), text("maxAc", "Maximum AC use voltage, AC RMS, V"),
                text("dcProof", "DC proof-test voltage, avg V"), text("maxDc", "Maximum DC use voltage, avg V")))),
        TableBlock(TableSchema("distances", "Table C: Working Distances by Equipment Class",
            listOf(text("equipClass", "Equipment Class"), num("mm", "Working Distance", "mm"), num("inch", "Working Distance", "Inch"), num("busGap", "Typical Conductor or Bus gap (mm)")),
            headerRows = listOf(
                listOf(h("Equipment Class", rows = 2), h("Working Distance", 2), h("Typical Conductor or Bus gap (mm)", rows = 2)),
                listOf(h("mm"), h("Inch"))
            ))),
        TableBlock(TableSchema("study", "Table D: Arc Flash Study Result",
            listOf(text("busId", "ID"), num("nomKv", "Nom. kV"), text("type", "Type"), num("bolted", "Bolted"), num("arcing", "Arcing"),
                num("fct", "FCT (cycles)"), num("energy", "Incident Energy (cal/cm²)"), num("afb", "AFB (m)"), text("level", "Energy Level")),
            headerRows = listOf(
                listOf(h("Bus", 3), h("Total Fault Current (kA)", 2), h("Arc-Flash Analysis Results", 4)),
                listOf(h("ID"), h("Nom. kV"), h("Type"), h("Bolted"), h("Arcing"), h("FCT (cycles)"), h("Incident Energy (cal/cm²)"), h("AFB (m)"), h("Energy Level"))
            ))),
        FieldsBlock("Observations & Recommendations", fields(long("observations", "Observations"), long("recommendations", "Recommendations")))
    ),
    fileName = { _, loc -> "${loc.ifBlank { "Arc_Flash_Study" }}_Report_$utcDate" }
)

// =====================================================================
// 13. SPD health check
// =====================================================================

private val spd = TestSchema(
    blocks = listOf(
        FieldsBlock("Health checks for SPDs", fields(sel("status", "Protocol Status", STATUS4, "PENDING"))),
        FieldsBlock("Test Personnel & Schedule", personnel(dateDefault = true)),
        TableBlock(TableSchema("inspections", "4. SURGE PROTECTIVE DEVICE INSPECTION",
            listOf(text("location", "SPD Location"), text("panelName", "Panel Name"), text("make", "Make"), text("model", "Model"),
                text("rating", "Rating"), text("remark", "Remark")))),
        FieldsBlock("Observations & Recommendations", fields(long("observations", "OBSERVATIONS"), long("recommendations", "RECOMMENDATIONS")))
    ),
    fileName = { _, loc -> "${loc.ifBlank { "SPD_Health_Check" }}_Report_$utcDate" }
)

// =====================================================================
// 14. Lightning arrestor
// =====================================================================

private val lightning = TestSchema(
    blocks = listOf(
        FieldsBlock("Diagnostic Status — IEC 62305-1 Standard Compliance", fields(sel("status", "Overall Result", listOf("PENDING", "PASS", "FAIL"), "PENDING"))),
        FieldsBlock("Personnel & Test Schedule", personnel(dateDefault = true)),
        TableBlock(TableSchema("tools", "Table: Equipment's Used for Audit",
            listOf(text("name", "Equipment's Used for Audit"), text("make", "Make"), text("serial", "Serial no"), text("model", "Model no"),
                date("calDate", "Calibration date"), date("dueDate", "Calibration due date")),
            serial = "Sr.No.")),
        TableBlock(TableSchema("pits", "Table: Grounding Resistance Measurements",
            listOf(text("pitName", "Earth pit names"), text("location", "Arrester Location"), text("conductorType", "Conductor Size & Type"),
                num("groundingValue", "Grounding Value (Ω)"), num("acceptValue", "Accept Value (Ω)", default = "10"),
                text("remarks", "Remarks"), calc("result", "Result")),
            serial = "Sr.no"
        ) { r, _ ->
            val gv = n(r["groundingValue"]); val av = n(r["acceptValue"])
            r["result"] = if (gv != null && av != null) (if (gv <= av) "Pass" else "Fail") else "..."
        }),
        FieldsBlock("Observations & Recommendations", fields(long("remarks", "Test Observations"), long("recommendations", "Professional Recommendations")))
    ),
    fileName = { _, _ -> "Lightning_Protection_$utcDate" }
)

// =====================================================================
// 15. Single point of failure
// =====================================================================

private val spof = TestSchema(
    blocks = listOf(
        FieldsBlock("Single Point of Failure", fields(sel("status", "Status", STATUS4, "PENDING"), long("scopeDescription", "SCOPE & DESCRIPTION"))),
        FieldsBlock("Personnel & Test Schedule", fields(text("preparedBy", "Prepared By"), text("testedBy", "Tested By"), text("testerEmail", "Tester Email"),
            text("witnessedBy", "Witnessed By"), text("approvedBy", "Approved By"), Col("testDate", "Test Date", ColType.DATE, default = today))),
        TableBlock(TableSchema("inspection", "System Inspection",
            listOf(text("criticalEquipment", "CRITICAL EQUIPMENT LIST"), text("source", "SOURCE"), text("redundant", "REDUNDANT"), text("remarks", "REMARKS")))),
        TableBlock(obsTable())
    ),
    groupSets = listOf(GroupSet("sectionName", "DC Section", "DC-{n}", listOf("inspection"))),
    fileName = { _, loc -> "${loc.ifBlank { "SPOF" }}_Report_$utcDate" }
)

// =====================================================================
// 16. ELV gap assessment
// =====================================================================

private val elvGap = TestSchema(
    blocks = listOf(
        FieldsBlock("Personnel & Test Schedule", personnel()),
        TableBlock(TableSchema("checklist", "Full Standard Verification Checklist",
            listOf(ro("no", "No"), ro("description", "Description"), sel("result", "Result", listOf("Yes", "No", "N/A")),
                text("remarks", "Remarks"), text("correctiveActions", "Corrective Actions")),
            canAddRows = false, subGroupCol = "system", presetRows = ELV_CHECKLIST_ROWS)),
        SummaryBlock("Infrastructure Compliance Summary", listOf("S. No", "Infrastructure", "Compliance", "Remarks", "Corrective Actions")) { doc ->
            doc.rows("checklist").filter { it["result"] == "No" }.mapIndexed { i, r ->
                listOf((i + 1).toString(), r["system"].orEmpty(), "No", r["remarks"].orEmpty(), r["correctiveActions"].orEmpty())
            }
        },
        TableBlock(TableSchema("gap", "GAP ANALYSIS – SAFETY AND SECURITY INFRASTRUCTURE",
            listOf(Col("standard", "Standard", ColType.TEXT, default = "TIA-942", readOnly = true), text("section", "Section"),
                long("requirement", "Standard Requirement"), text("system", "System"), long("implementation", "Current Implementation"),
                sel("compliance", "Compliance", listOf("-", "Yes", "No", "N/A"), "-"), long("correctiveActions", "Corrective Actions")),
            serial = "No",
            initialRows = listOf(
                listOf("Table 1", "Site access must be restricted...", "Physical Security", "Observed security fencing..."),
                listOf("Table 1", "Visitor entry logs must be automated...", "Access Control", "Manual logs in use..."),
                listOf("Table 2", "Camera coverage at entry lobby...", "CCTV", "2 cameras present..."),
                listOf("Table 2", "Motion sensors at parking area...", "Detection", "Sensors not present..."),
                listOf("Table 3", "Emergency lighting backup time...", "Lighting", "Backup for 30 mins...")
            ).map { mapOf("section" to it[0], "requirement" to it[1], "system" to it[2], "implementation" to it[3]) })),
        TableBlock(obsTable("Executive ELV Gap Assessment Summary"))
    ),
    fileName = { _, loc -> "${loc.ifBlank { "ELV_Gap_Assessment" }}_Report_$utcDate" }
)

// =====================================================================
// 17. DG endurance
// =====================================================================

private val DG_PARAMS = listOf(
    "Voltage (R-Y)" to "Volts", "Voltage (Y-B)" to "Volts", "Voltage (B-R)" to "Volts", "Voltage (R-N)" to "Volts", "Voltage (Y-N)" to "Volts",
    "Voltage (B-N)" to "Volts", "Current - R Phase" to "Amps", "Current - Y Phase" to "Amps", "Current - B Phase" to "Amps",
    "Power factor" to "%", "Frequency" to "Hz", "Battery Voltage" to "V", "Total active power" to "KW", "Total apparent power" to "KVA",
    "Speed" to "RPM", "Lube oil Pressure" to "Kpa", "Coolant Temp" to "°C", "Oil Temp" to "°C"
)
private val DG_LOADS = listOf("25%", "50%", "75%", "100%")

private fun dgTemplate(tableId: String): List<Map<String, String>> = when (tableId) {
    "standardInfo" -> listOf("Standard Number" to "---", "Author" to "Technavious Solutions", "Performed Date" to "---",
        "Category" to "Load Test - Test Report", "Subcategory" to "Routine Test for DG", "Description" to "---")
        .map { mapOf("label" to it.first, "value" to it.second, ROW_FIXED to "1") }
    "equipmentDetails" -> listOf("Nomenclature", "Engine Number", "Make", "Alternator Serial", "Location", "Model")
        .map { mapOf("label" to it, "value" to "---", ROW_FIXED to "1") }
    "loadReadings" -> DG_PARAMS.map { (p, u) -> mapOf("parameter" to p, "unit" to u, ROW_FIXED to "1") + listOf("v0", "v25", "v50", "v75", "v100").associateWith { "---" } }
    "transient" -> listOf("0% to 36%", "36% to 61%", "61% to 88 %", "88% to 100 %").mapIndexed { i, l -> mapOf("srNo" to "${i + 1}", "load" to l, "result" to "PASS/FAIL", ROW_FIXED to "1") }
    "noise" -> DG_LOADS.map { mapOf("load" to it, "open" to "0.0", "closed" to "0.0", ROW_FIXED to "1") }
    "vibration" -> DG_LOADS.map { l -> mapOf("load" to l, ROW_FIXED to "1") + ('a'..'h').associate { it.toString() to "0.0" } }
    "fuel" -> listOf(mapOf("percentLoad" to "---", "time" to "---", "oemStd" to "---"))
    else -> emptyList()
}

private val dg = TestSchema(
    blocks = listOf(
        FieldsBlock("Test Personnel & Schedule", personnel()),
        TableBlock(TableSchema("onSite", "On Site Test Description",
            listOf(ro("srNo", "Sr. No."), ro("description", "Test Description"), check("result", "Result")),
            canAddRows = false,
            presetRows = listOf(
                "Part Load & Full Load Test (25%, 50%, 75%, 100%, 110%)",
                "Step / Transient Load Test (0% to 36%, 36% to 61%) (61% to 88%, 88% to 100%) (0% to 50%, 50% to 100%)",
                "Noise Level Measurement Test", "Vibration Test", "Fuel Consumption Test"
            ).mapIndexed { i, d -> mapOf("srNo" to "${i + 1}", "description" to d) })),
        TableBlock(TableSchema("tools", "Testing & Commissioning Equipment",
            listOf(text("name", "Equipment Name"), text("model", "Model"), text("serialNo", "Serial No."), text("calDate", "Issue"), text("expDate", "Expiry")),
            headerRows = listOf(
                listOf(h("Equipment Name", rows = 2), h("Model", rows = 2), h("Serial No.", rows = 2), h("Calibration Date", 2)),
                listOf(h("Issue"), h("Expiry"))
            ),
            initialRows = listOf(
                listOf("Digital clamp meter", "-1080-TRMS", "23070822", "2025-05-10", "2026-05-09"),
                listOf("Thermal gun", "TIC 23", "2022030006417", "2025-02-05", "2026-04-04"),
                listOf("DB meter", "WT85B", "H22693476", "2025-04-22", "2026-04-21"),
                listOf("Vibration meter", "WT63B", "H22690512", "2025-04-23", "2026-04-22"),
                listOf("Power quality analyzer", "1775", "6649115", "2025-03-11", "2026-03-10"),
                listOf("Load bank", "Resistive load bank", "Serial number...", "Not applicable", "Expiry Date")
            ).map { mapOf("name" to it[0], "model" to it[1], "serialNo" to it[2], "calDate" to it[3], "expDate" to it[4]) })),
        TableBlock(TableSchema("standardInfo", "STANDARD INFORMATION", listOf(ro("label", "Field"), text("value", "Value")), canAddRows = false)),
        TableBlock(TableSchema("equipmentDetails", "EQUIPMENT DETAILS", listOf(ro("label", "Field"), text("value", "Value")), canAddRows = false)),
        TableBlock(TableSchema("loadReadings", "PART LOAD AND FULL LOAD TEST READINGS",
            listOf(ro("parameter", "Parameters"), ro("unit", "Units"), text("v0", "0"), text("v25", "25"), text("v50", "50"), text("v75", "75"),
                text("v100", "100"), text("remarks", "Remarks")),
            headerRows = listOf(
                listOf(h("Parameters", rows = 2), h("Units", rows = 2), h("Load Percentage", 5), h("Remarks", rows = 2)),
                listOf(h("0"), h("25"), h("50"), h("75"), h("100"))
            ),
            canAddRows = false)),
        TableBlock(TableSchema("transient", "Transient Load Test Summary",
            listOf(ro("srNo", "SR NO"), ro("load", "TRANSIENT LOAD"), text("result", "RESULT")), canAddRows = false)),
        TableBlock(TableSchema("noise", "Noise Level Measurement (DBS)",
            listOf(ro("load", "LOAD IN %"), num("open", "DOORS OPEN"), num("closed", "DOORS CLOSED")), canAddRows = false)),
        TableBlock(TableSchema("vibration", "Vibration Measurement Values (M/S²)",
            listOf(ro("load", "LOAD %")) + ('a'..'h').map { num(it.toString(), it.uppercase()) }, canAddRows = false)),
        TableBlock(TableSchema("fuel", "FUEL CONSUMPTION SUMMARY",
            listOf(text("percentLoad", "% LOAD"), text("time", "TIME"),
                num("kwhInitial", "KWH Initial", default = "0"), num("kwhFinal", "KWH Final", default = "0"), calc("kwhUnits", "KWH Units"),
                num("fuelInitial", "Fuel Initial (L)", default = "0"), num("fuelFinal", "Fuel Final (L)", default = "0"), calc("fuelConsum", "Fuel Consum. (L)"),
                text("oemStd", "OEM STD (L/H)", default = "---"), calc("efficiency", "EFFICIENCY (KWH/L)")),
            serial = "SR.NO",
            headerRows = listOf(
                listOf(h("SR.NO", rows = 2), h("% LOAD", rows = 2), h("TIME", rows = 2), h("KWH GENERATED", 3), h("FUEL (LITRES)", 3), h("OEM STD (L/H)", rows = 2), h("EFFICIENCY (KWH/L)", rows = 2)),
                listOf(h("INITIAL"), h("FINAL"), h("UNITS"), h("INITIAL"), h("FINAL"), h("CONSUM."))
            )
        ) { r, _ ->
            val units = n0(r["kwhFinal"]) - n0(r["kwhInitial"]); val consum = n0(r["fuelFinal"]) - n0(r["fuelInitial"])
            r["kwhUnits"] = jsNum(units); r["fuelConsum"] = jsNum(consum)
            r["efficiency"] = if (consum > 0) fixed(units / consum, 2) else "0.00"
        }),
        FieldsBlock("Assessment Summary", fields(long("recommendations", "Recommendations"), long("observations", "Observations")))
    ),
    groupSets = listOf(GroupSet("unitName", "DG Unit", "DC-01 DG-{nn}",
        listOf("standardInfo", "equipmentDetails", "loadReadings", "transient", "noise", "vibration", "fuel")) { tid, _ -> dgTemplate(tid) }),
    fileName = { _, _ -> "DG_Endurance_Test_$utcDate" }
)

// =====================================================================
// 18. Coupon test
// =====================================================================

private val coupon = TestSchema(
    blocks = listOf(
        FieldsBlock("Personnel & Test Schedule", personnel(dateDefault = true)),
        TableBlock(TableSchema("tools", "4. TOOLS USED FOR AUDIT",
            listOf(text("name", "Equipment's used for audit"), text("make", "Make"), text("parameter", "Parameter measured")), serial = "Sr. No")),
        TableBlock(TableSchema("results", "8. TEST RESULTS — Corrosion film thickness",
            listOf(ro("couponType", "Type of coupon"), num("oxide", "Oxide"), num("chloride", "Chloride"), num("sulfide", "Sulfide"), num("other", "Other"),
                calc("totalAngstroms", "Total (Å)"), text("isaLevel", "Level"), text("remarks", "Remarks")),
            headerRows = listOf(
                listOf(h("Type of coupon", rows = 2), h("Composition of Corrosion", 4), h("Total (Å)", rows = 2), h("ISA Classification", 2)),
                listOf(h("Oxide"), h("Chloride"), h("Sulfide"), h("Other"), h("Level"), h("Remarks"))
            ),
            canAddRows = false
        ) { r, _ -> r["totalAngstroms"] = fixed(n0(r["oxide"]) + n0(r["chloride"]) + n0(r["sulfide"]) + n0(r["other"]), 2) }),
        TableBlock(TableSchema("graphs", "Graphical Representation of corrosion film thickness",
            listOf(image("graphImage", "Graph Image")), canAddRows = false)),
        TableBlock(obsTable("Audit Summary & Findings"))
    ),
    groupSets = listOf(GroupSet("locationName", "Location", "Location {n}", listOf("results", "graphs")) { tid, _ ->
        if (tid == "results") listOf("Copper (Cu)", "Silver (Ag)").map { mapOf("couponType" to it, ROW_FIXED to "1") }
        else listOf(mapOf(ROW_FIXED to "1"))
    }),
    fileName = { doc, loc -> "${doc.rows("results").firstOrNull()?.get("locationName").orEmpty().ifBlank { loc.ifBlank { "Coupon" } }}_Test_$utcDate" }
)

fun schemaFor(type: TestType): TestSchema = when (type) {
    TestType.VIBRATION_ANALYSIS -> vibration
    TestType.THERMOGRAPHY -> thermography
    TestType.BATTERY_IMPEDANCE -> battery
    TestType.EARTH_STATION -> earth
    TestType.RACK_COOLING_INDEX -> rci
    TestType.CFM_AIRFLOW -> cfm
    TestType.CAPACITY_ASSESSMENT -> capacity
    TestType.LIFE_CYCLE -> lifeCycle
    TestType.POWER_FACTOR -> powerFactor
    TestType.POWER_QUALITY -> powerQuality
    TestType.ARC_FLASH -> arcFlash
    TestType.SPD_HEALTH -> spd
    TestType.LIGHTNING_ARRESTOR -> lightning
    TestType.SPOF -> spof
    TestType.ELV_GAP -> elvGap
    TestType.DG_ENDURANCE -> dg
    TestType.POWER_SYSTEM -> powerSystem
    TestType.COUPON_TEST -> coupon
}
