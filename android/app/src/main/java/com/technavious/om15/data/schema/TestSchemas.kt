package com.technavious.om15.data.schema

import com.technavious.om15.data.model.TestType
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

enum class ColType { TEXT, NUMBER, SELECT, CALC }

data class Col(
    val key: String,
    val header: String,
    val type: ColType = ColType.NUMBER,
    val unit: String? = null,
    val width: Int = 15,
    val default: String = "",
    val options: List<String> = emptyList()
) {
    val isInput: Boolean get() = type != ColType.CALC
    val label: String get() = if (unit != null) "$header ($unit)" else header
}

typealias Row = MutableMap<String, String>

class TableSchema(
    val id: String,
    val title: String,
    val columns: List<Col>,
    val calc: (Row) -> Unit = {}
) {
    fun col(key: String) = columns.firstOrNull { it.key == key }
    fun newRow(index: Int): Row = mutableMapOf<String, String>().apply {
        put(ROW_ID, "r${System.currentTimeMillis()}_$index")
        columns.forEach { if (it.default.isNotEmpty()) put(it.key, it.default) }
        calc(this)
    }
}

const val ROW_ID = "_id"

class TestSchema(val tables: List<TableSchema>) {
    fun table(id: String) = tables.firstOrNull { it.id == id }
}

fun n(s: String?): Double? = s?.replace(",", "")?.trim()?.toDoubleOrNull()
private fun n0(s: String?): Double = n(s) ?: 0.0
fun fixed(d: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", d)
private fun jsNum(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

private fun text(key: String, header: String, width: Int = 22, default: String = "") = Col(key, header, ColType.TEXT, width = width, default = default)
private fun num(key: String, header: String, unit: String? = null, width: Int = 15, default: String = "") = Col(key, header, ColType.NUMBER, unit, width, default)
private fun calc(key: String, header: String, unit: String? = null, width: Int = 15) = Col(key, header, ColType.CALC, unit, width)
private fun select(key: String, header: String, options: List<String>, width: Int = 16, default: String = "") = Col(key, header, ColType.SELECT, width = width, default = default, options = options)

// ---------- Formulas (ported 1:1 from the O&M web app) ----------

fun coolingTr(cfm: String?, ra: String?, sa: String?): Double {
    val c = n(cfm); val r = n(ra); val s = n(sa)
    if (c == null || r == null || s == null) return 0.0
    return (1.06 * c * (r - s) * 1.8) / 12000
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

private fun avgTemp(vararg values: String?): Double? {
    val nums = values.mapNotNull { n(it) }
    return if (nums.isEmpty()) null else nums.average()
}

fun cfmRequired(loadW: String?, deltaT: String?, multiplier: String?): Double {
    val w = n0(loadW); val dt = n0(deltaT); val m = n(multiplier)?.takeIf { it != 0.0 } ?: 1.0
    if (w == 0.0 || dt == 0.0) return 0.0
    return (w * 3.412969 * m) / (1.8 * 1.08 * dt)
}

fun cfmFmt(x: Double): String = if (x % 1.0 == 0.0) fixed(x, 0) else fixed(x, 2)

fun rulStatus(rulPct: Double, al: Double): String = when {
    al <= 0 -> ""
    rulPct > 50 -> "Fit for Service"
    rulPct >= 25 -> "Degrading Stage"
    rulPct >= 10 -> "Approaching Replacement"
    rulPct > 0 -> "Reaching End-of-Life"
    else -> "End-of-Life"
}

// ---------- Schemas ----------

private val vibration = TestSchema(listOf(
    TableSchema("vibration", "Vibration Summary", listOf(
        text("equipmentId", "Equipment ID"),
        select("classDetails", "Class Details", listOf("Class I", "Class II", "Class III", "Class IV")),
        num("vertical", "Vertical", "mm/s", 14),
        num("horizontal", "Horizontal", "mm/s", 14),
        num("axial", "Axial", "mm/s", 14),
        calc("maxValue", "Max Value", "mm/s", 14),
        calc("condition", "Vibration Condition", width = 22)
    )) { r ->
        val max = maxOf(n0(r["vertical"]), n0(r["horizontal"]), n0(r["axial"]))
        r["maxValue"] = if (max > 0) jsNum(max) else ""
        r["condition"] = vibrationCondition(r["classDetails"], r["maxValue"])
    }
))

private val thermography = TestSchema(listOf(
    TableSchema("inspections", "Thermography Inspection", listOf(
        text("roomName", "Room Name", 25), text("panelName", "Panel Name", 25), text("feederName", "Feeder Name", 25),
        num("maxTemp", "Max Temp", "°C"), num("minTemp", "Min Temp", "°C"), text("remarks", "Remarks", 35)
    ))
))

private val battery = TestSchema(listOf(
    TableSchema("readings", "Battery Readings", listOf(
        text("upsName", "UPS System", 18), text("stringName", "String", 16), num("batteryNo", "Battery No", width = 10),
        num("measuredIr", "Measured IR", "mΩ"), num("irFailThresh", "Fail Thresh", "mΩ", default = "13"),
        num("irWarnThresh", "Warn Thresh", "mΩ", default = "8"), num("measuredVolt", "Voltage", "V"),
        num("voltThresh", "Volt Thresh", "V", default = "12.1"), calc("remarks", "Status")
    )) { r ->
        val mir = n(r["measuredIr"]); val fth = n(r["irFailThresh"]); val wth = n(r["irWarnThresh"])
        val mv = n(r["measuredVolt"]); val vth = n(r["voltThresh"])
        r["remarks"] = when {
            mir == null && mv == null -> "..."
            (mir != null && fth != null && mir >= fth) || (mv != null && vth != null && mv < vth) -> "FAIL"
            mir != null && wth != null && mir >= wth -> "WARN"
            else -> "PASS"
        }
    }
))

private val earth = TestSchema(listOf(
    TableSchema("readings", "Measuring Details (Readings)", listOf(
        text("pitNo", "Earth Pit No", 16), text("description", "Earthing Description", 30),
        num("measuredValue", "Measured Value", "Ω", 22), num("limitValue", "Limit Value", "Ω", 22, default = "2"),
        calc("remarks", "Remarks", width = 35)
    )) { r ->
        val mv = n(r["measuredValue"]); val lv = n(r["limitValue"])
        r["remarks"] = when {
            mv != null && lv != null && mv > lv -> "High Resistance - Review Required"
            mv != null -> "ok"
            else -> ""
        }
    }
))

private val rci = TestSchema(listOf(
    TableSchema("temperatures", "Rack Temperature Study", listOf(
        text("cacRef", "CAC Reference", 18), text("rackRef", "Rack Reference", 18),
        num("front15U", "Front 15U", "°C", 12), num("front22U", "Front 22U", "°C", 12), num("front35U", "Front 35U", "°C", 12),
        num("rear15U", "Rear 15U", "°C", 12), num("rear22U", "Rear 22U", "°C", 12), num("rear35U", "Rear 35U", "°C", 12),
        num("rhFront", "RH Front", "%", 12), num("rhRear", "RH Rear", "%", 12),
        calc("inletTempResult", "Inlet Temp Result"), calc("excessInletTemp", "Excess Temp", "°C"), calc("deficitInletTemp", "Deficit Temp", "°C")
    )) { r ->
        val avgF = avgTemp(r["front15U"], r["front22U"], r["front35U"])?.let { n(fixed(it, 2)) }
        r["inletTempResult"] = ""; r["excessInletTemp"] = ""; r["deficitInletTemp"] = ""
        if (avgF != null) when {
            avgF in 18.0..27.0 -> r["inletTempResult"] = "PASS"
            avgF < 18 -> { r["inletTempResult"] = "FAIL"; r["deficitInletTemp"] = fixed(18 - avgF, 2) }
            else -> { r["inletTempResult"] = "FAIL"; r["excessInletTemp"] = fixed(avgF - 27, 2) }
        }
    }
))

private val cfm = TestSchema(listOf(
    TableSchema("racks", "CAC Airflow", listOf(
        text("cacName", "CAC", 16), select("side", "Side", listOf("Left", "Right"), 10, "Left"), text("rackRef", "Rack Reference", 18),
        num("loadW", "Rack Load", "W"), num("actualCFM", "Actual CFM"),
        num("deltaT", "ΔT", "°C", 10), num("multiplier", "Multiplier", width = 10, default = "1"),
        calc("requiredCFM", "Required CFM"), calc("deficitCFM", "Deficit")
    )) { r ->
        val req = cfmRequired(r["loadW"], r["deltaT"], r["multiplier"])
        r["requiredCFM"] = if (req > 0) cfmFmt(req) else ""
        r["deficitCFM"] = if (req > 0 || n(r["actualCFM"]) != null) cfmFmt(n0(r["actualCFM"]) - req) else ""
    }
))

private val capacity = TestSchema(listOf(
    TableSchema("pahu", "PAHU Capacity", listOf(
        text("equipmentId", "Equipment ID", 18), text("modelNo", "Model No", 18), text("serialNo", "Serial No", 18),
        num("designAirflow", "Design Airflow", "CFM"), num("designRaTemp", "Design RA Temp", "°C", 12), num("designSaTemp", "Design SA Temp", "°C", 12),
        num("designPower", "Design Power", "kW", 12), num("designWaterFlowRate", "Design Water Flow", "GPM"),
        calc("designCoolingTonnage", "Design Cooling Tonnage", "TR"),
        num("measuredAirflow", "Measured Airflow", "CFM"), num("measuredSaTemp", "Measured SA Temp", "°C", 12), num("measuredRaTemp", "Measured RA Temp", "°C", 12),
        num("measuredFanSpeed", "Fan Speed", "%", 12), calc("measuredCoolingTonnage", "Cooling Tonnage (Air)", "TR"),
        num("measuredPower", "Measured Power", "kW", 12), num("measuredChwIn", "CHW In", "°C", 12), num("measuredChwOut", "CHW Out", "°C", 12),
        num("measuredValveOpening", "Valve", "%", 12), num("measuredWaterFlowRate", "Measured Water Flow", "GPM"),
        calc("measuredWaterCoolingTonnage", "Cooling Tonnage (Water)", "TR")
    )) { r ->
        r["designCoolingTonnage"] = fixed(coolingTr(r["designAirflow"], r["designRaTemp"], r["designSaTemp"]), 2)
        r["measuredCoolingTonnage"] = fixed(coolingTr(r["measuredAirflow"], r["measuredRaTemp"], r["measuredSaTemp"]), 2)
        r["measuredWaterCoolingTonnage"] = fixed((n0(r["measuredWaterFlowRate"]) * abs(n0(r["measuredChwOut"]) - n0(r["measuredChwIn"])) * 1.8) / 24, 2)
    },
    TableSchema("pac", "PAC Capacity", listOf(
        text("equipmentId", "Equipment ID", 18), text("modelNo", "Model No", 18), text("serialNo", "Serial No", 18),
        num("designAirflow", "Design Airflow", "CFM"), num("designRaTemp", "Design RA Temp", "°C", 12), num("designSaTemp", "Design SA Temp", "°C", 12),
        num("designPower", "Design Power", "kW", 12), calc("designCoolingTonnage", "Design Cooling Tonnage", "TR"),
        num("fanSpeed", "Fan Speed", "%", 12),
        num("measuredAirflow", "Measured Airflow", "CFM"), num("measuredRaTemp", "Measured RA Temp", "°C", 12), num("measuredSaTemp", "Measured SA Temp", "°C", 12),
        calc("measuredCoolingTonnage", "Cooling Tonnage (Air)", "TR"), num("measuredPower", "Measured Power", "kW", 12), calc("cop", "COP", width = 12)
    )) { r ->
        val dTr = coolingTr(r["designAirflow"], r["designRaTemp"], r["designSaTemp"])
        val mTr = coolingTr(r["measuredAirflow"], r["measuredRaTemp"], r["measuredSaTemp"])
        val power = n0(r["measuredPower"])
        r["designCoolingTonnage"] = fixed(dTr, 2)
        r["measuredCoolingTonnage"] = fixed(mTr, 2)
        r["cop"] = fixed(if (mTr != 0.0 && power != 0.0) (mTr * 3.517) / power else 0.0, 2)
    }
))

private val lifeCycle = TestSchema(listOf(
    TableSchema("lca", "Life Cycle Assessment", listOf(
        text("equipmentTag", "Equipment Tag / Name"), text("makeModel", "Make & Model No"), text("serialNo", "Serial No", 20), text("location", "Location", 20),
        num("installYear", "Install Year"), num("currentYear", "Current Year", default = Calendar.getInstance().get(Calendar.YEAR).toString()),
        calc("ageYrs", "Age", "yrs", 12),
        num("vibrationScore", "Vibration Score (VS)", width = 18), num("corrosionScore", "Corrosion Score (CS)", width = 18), num("maintScore", "Maint. Score (MS)", width = 18),
        calc("healthIndex", "Health Index (HI)", width = 18),
        num("runningLifeYrs", "Running Life", "yrs", 18), calc("utilizationRatio", "Utilization Ratio (UR)", width = 20), calc("utilizationFactor", "Utilization Factor (UF)", width = 20),
        num("designLifeYrs", "Design Life", "yrs", 20), calc("adjustedLifeAL", "Adjusted Life (AL)", width = 20),
        calc("rulYrs", "RUL", "yrs", 18), calc("rulPct", "RUL", "%", 18), calc("lifeCycleStatus", "Life cycle Status", width = 22),
        text("remarksRul", "Remarks (RUL)", 25)
    )) { r ->
        val install = n(r["installYear"])
        val current = n(r["currentYear"]) ?: Calendar.getInstance().get(Calendar.YEAR).toDouble()
        r["ageYrs"] = if (install != null) jsNum(current - install) else ""
        val scores = listOf(r["vibrationScore"], r["corrosionScore"], r["maintScore"])
        r["healthIndex"] = if (scores.any { !it.isNullOrBlank() })
            fixed(0.4 * n0(scores[0]) + 0.3 * n0(scores[1]) + 0.3 * n0(scores[2]), 1) else ""
        val age = n0(r["ageYrs"])
        val ur = if (age > 0 && n(r["runningLifeYrs"]) != null) n0(r["runningLifeYrs"]) / age else null
        r["utilizationRatio"] = ur?.let { fixed(it, 2) } ?: ""
        r["utilizationFactor"] = when {
            ur == null -> "1.0"
            ur > 0.8 -> "0.9"
            ur >= 0.6 -> "0.95"
            ur >= 0.4 -> "1.0"
            else -> "1.05"
        }
        val dl = n0(r["designLifeYrs"]); val hi = n0(r["healthIndex"]); val uf = n(r["utilizationFactor"]) ?: 1.0
        val al = if (dl > 0 && hi > 0) dl * hi * uf else 0.0
        val rul = if (al > 0) al - age else 0.0
        val pct = if (al > 0) rul / al * 100 else 0.0
        r["adjustedLifeAL"] = if (al > 0) fixed(al, 1) else ""
        r["rulYrs"] = if (al > 0) fixed(rul, 1) else ""
        r["rulPct"] = if (al > 0 && rul >= 0) fixed(pct, 1) else ""
        r["lifeCycleStatus"] = rulStatus(pct, al)
    }
))

private val powerFactor = TestSchema(listOf(
    TableSchema("feeders", "APFC Feeders", listOf(
        text("panelName", "APFC Panel", 20), text("feederName", "Feeder Name", 20),
        num("bankKvar", "Capacitor Bank", "kVAR", 22), num("breakerRating", "Breaker Rating", "A", 20), num("contactorRating", "Contactor Rating", "A", 22),
        num("obtainedCap", "Obtained Capacitance", "µF", 24), num("actualRY", "Actual RY Capacitance", "µF", 25),
        num("actualYB", "Actual YB Capacitance", "µF", 25), num("actualBR", "Actual BR Capacitance", "µF", 25),
        num("measuredKvar", "Measured kVAR", width = 18), text("acceptableLimit", "Acceptable Limit of Capacitance in kVAR", 35),
        num("caseTemp", "Case Temperature", "°C", 20), num("currentR", "R Phase Current", "A", 22), num("currentY", "Y Phase Current", "A", 22),
        num("currentB", "B Phase Current", "A", 22), text("remarks", "Remarks", 25)
    ))
))

private val lightning = TestSchema(listOf(
    TableSchema("pits", "Grounding Resistance", listOf(
        text("pitName", "Earth Pit Names", 25), text("location", "Arrester Location", 25), text("conductorType", "Conductor Size & Type", 25),
        num("groundingValue", "Grounding Value", "Ω", 22), num("acceptValue", "Accept Value", "Ω", 20, default = "10"),
        text("remarks", "Remarks", 25), calc("result", "Result")
    )) { r ->
        val gv = n(r["groundingValue"]); val av = n(r["acceptValue"])
        r["result"] = if (gv != null && av != null) (if (gv <= av) "Pass" else "Fail") else "..."
    }
))

private val dg = TestSchema(listOf(
    TableSchema("parameters", "Parameters", listOf(
        text("parameter", "Parameter", 25), text("unit", "Unit", 10),
        num("v0", "0", width = 12), num("v25", "25", width = 12), num("v50", "50", width = 12),
        num("v75", "75", width = 12), num("v100", "100", width = 12), text("remarks", "Remarks", 30)
    )),
    TableSchema("transient", "Transient Load", listOf(text("load", "Transient Load", 25), text("result", "Result", 15))),
    TableSchema("noise", "Noise Level", listOf(num("load", "Load in %"), num("open", "Doors Open"), num("closed", "Doors Closed"))),
    TableSchema("vibration", "Vibration", listOf(num("load", "Load %", width = 10)) + ('a'..'h').map { num(it.toString(), it.uppercase(), width = 10) }),
    TableSchema("fuel", "Fuel Consum", listOf(
        num("percentLoad", "% LOAD"), text("time", "TIME", 15),
        num("kwhInitial", "kWh Generated Initial", width = 25), num("kwhFinal", "kWh Generated Final", width = 25), calc("kwhUnits", "kWh Generated Consumed", width = 25),
        num("fuelInitial", "Fuel in Litres Initial", width = 25), num("fuelFinal", "Fuel in Litres Final", width = 25), calc("fuelConsum", "Fuel in Litres Consumed", width = 25),
        num("oemStd", "OEM Std (L/H)", width = 20), calc("efficiency", "Efficiency (kWh/L)", width = 25)
    )) { r ->
        val kwh = n0(r["kwhFinal"]) - n0(r["kwhInitial"])
        val fuel = n0(r["fuelFinal"]) - n0(r["fuelInitial"])
        r["kwhUnits"] = jsNum(kwh)
        r["fuelConsum"] = jsNum(fuel)
        r["efficiency"] = if (fuel > 0) fixed(kwh / fuel, 2) else "0.00"
    }
))

private val coupon = TestSchema(listOf(
    TableSchema("results", "Coupon Results", listOf(
        select("couponType", "Type of coupon", listOf("Copper (Cu)", "Silver (Ag)"), 20, "Copper (Cu)"),
        num("oxide", "Oxide"), num("chloride", "Chloride"), num("sulfide", "Sulfide"), num("other", "Other"),
        calc("totalAngstroms", "Total", "Å"), text("isaLevel", "Level", 15), text("remarks", "Remarks", 30)
    )) { r ->
        r["totalAngstroms"] = jsNum(n0(r["oxide"]) + n0(r["chloride"]) + n0(r["sulfide"]) + n0(r["other"]))
    }
))

private fun simple(id: String, title: String, vararg cols: Col) = TestSchema(listOf(TableSchema(id, title, cols.toList())))

private val powerQuality = simple("readings", "Power Quality Readings",
    text("location", "Location / Panel"), num("frequency", "Frequency", "Hz"),
    num("voltageR", "Voltage R", "V"), num("voltageY", "Voltage Y", "V"), num("voltageB", "Voltage B", "V"),
    num("currentR", "Current R", "A"), num("currentY", "Current Y", "A"), num("currentB", "Current B", "A"),
    num("thdVoltage", "THD Voltage", "%"), num("thdCurrent", "THD Current", "%"))

private val arcFlash = simple("readings", "Arc Flash Results",
    text("equipment", "Equipment / Bus"), num("incidentEnergy", "Incident Energy", "cal/cm²", 20),
    num("arcFlashBoundary", "Arc Flash Boundary", "mm", 20), num("workingDistance", "Working Distance", "mm", 20), text("ppeCategory", "PPE Category", 16))

private val spd = simple("readings", "SPD Health Check",
    text("location", "SPD Location"), select("spdStatus", "SPD Status", listOf("Healthy", "Faulty", "Replace")),
    num("leakageCurrent", "Leakage Current", "mA", 18), num("earthResistance", "Earth Resistance", "Ω", 18), text("remarks", "Remarks", 30))

private val spof = simple("readings", "Single Point of Failure",
    text("component", "Component", 25), text("redundancy", "Redundancy", 20),
    select("riskLevel", "Risk Level", listOf("Low", "Medium", "High", "Critical")), text("mitigation", "Mitigation", 35))

private val elvGap = simple("readings", "ELV Gap Assessment",
    text("system", "System", 25), text("currentStatus", "Current Status", 25), text("gap", "Gap Identified", 30), text("recommendation", "Recommendation", 35))

private val powerSystem = simple("readings", "Power System Study",
    text("bus", "Bus / Equipment", 22), num("busVoltage", "Bus Voltage", "V", 18),
    num("shortCircuitCurrent", "Short Circuit Current", "kA", 22), num("arcFlashEnergy", "Arc Flash Energy", "cal/cm²", 22))

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
