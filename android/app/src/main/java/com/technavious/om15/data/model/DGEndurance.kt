package com.technavious.om15.data.model

import java.util.UUID

data class DGOnSiteTestRow(
    val id: String = UUID.randomUUID().toString(),
    val srNo: String = "",
    val description: String = "",
    val result: Boolean = false
)

data class DGLoadReadingRow(
    val id: String = UUID.randomUUID().toString(),
    val parameter: String = "",
    val unit: String = "",
    val v0: String = "",
    val v25: String = "",
    val v50: String = "",
    val v75: String = "",
    val v100: String = "",
    val remarks: String = ""
)

data class DGFuelSummaryRow(
    val id: String = UUID.randomUUID().toString(),
    val srNo: String = "",
    val percentLoad: String = "",
    val time: String = "",
    val kwhInitial: String = "",
    val kwhFinal: String = "",
    val kwhUnits: String = "",
    val fuelInitial: String = "",
    val fuelFinal: String = "",
    val fuelConsum: String = "",
    val oemStd: String = "",
    val efficiency: String = ""
)

data class DGUnit(
    val id: String = UUID.randomUUID().toString(),
    val unitName: String = "",
    val make: String = "",
    val model: String = "",
    val nomenclature: String = "",
    val engineNumber: String = "",
    val alternatorSerial: String = "",
    val location: String = "",
    val loadReadings: List<DGLoadReadingRow> = emptyList(),
    val transientSummary: List<DGTransientRow> = emptyList(),
    val noiseLevels: List<DGNoiseRow> = emptyList(),
    val vibrationLevels: List<DGVibrationRow> = emptyList(),
    val fuelSummary: List<DGFuelSummaryRow> = emptyList()
)

data class DGTransientRow(
    val id: String = UUID.randomUUID().toString(),
    val srNo: String = "",
    val load: String = "",
    val result: String = ""
)

data class DGNoiseRow(
    val id: String = UUID.randomUUID().toString(),
    val load: String = "",
    val open: String = "",
    val closed: String = ""
)

data class DGVibrationRow(
    val id: String = UUID.randomUUID().toString(),
    val load: String = "",
    val a: String = "", val b: String = "", val c: String = "", val d: String = "",
    val e: String = "", val f: String = "", val g: String = "", val h: String = ""
)

data class DGEnduranceReading(
    val header: ReadingHeader = ReadingHeader(),
    val onSiteTests: List<DGOnSiteTestRow> = emptyList(),
    val equipmentList: List<EquipmentRow> = emptyList(),
    val dgUnits: List<DGUnit> = emptyList(),
    val observations: String = "",
    val recommendations: String = ""
)
