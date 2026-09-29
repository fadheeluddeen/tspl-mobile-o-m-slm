package com.technavious.om15.data.model

import java.util.UUID

data class CapacityToolRow(
    val id: String = UUID.randomUUID().toString(),
    val instrumentName: String = "",
    val make: String = "",
    val model: String = "",
    val serialNumber: String = "",
    val calibrationIssue: String = "",
    val calibrationExpiry: String = ""
)

data class PahuEntry(
    val id: String = UUID.randomUUID().toString(),
    val equipmentId: String = "",
    val modelNo: String = "",
    val serialNo: String = "",
    val designAirflow: String = "",
    val designRaTemp: String = "",
    val designSaTemp: String = "",
    val designPower: String = "",
    val designWaterFlowRate: String = "",
    val designCoolingTonnage: String = "",
    val measuredAirflow: String = "",
    val measuredSaTemp: String = "",
    val measuredRaTemp: String = "",
    val measuredFanSpeed: String = "",
    val measuredCoolingTonnage: String = "",
    val measuredPower: String = "",
    val measuredChwIn: String = "",
    val measuredChwOut: String = "",
    val measuredValveOpening: String = "",
    val measuredWaterFlowRate: String = "",
    val measuredWaterCoolingTonnage: String = ""
)

data class PacEntry(
    val id: String = UUID.randomUUID().toString(),
    val equipmentId: String = "",
    val modelNo: String = "",
    val serialNo: String = "",
    val designAirflow: String = "",
    val designRaTemp: String = "",
    val designSaTemp: String = "",
    val designPower: String = "",
    val designCoolingTonnage: String = "",
    val fanSpeed: String = "",
    val measuredAirflow: String = "",
    val measuredRaTemp: String = "",
    val measuredSaTemp: String = "",
    val measuredPower: String = "",
    val measuredCoolingTonnage: String = "",
    val measuredFanSpeed: String = ""
)

data class PahuTable(
    val id: String = UUID.randomUUID().toString(),
    val tableNumber: String = "",
    val title: String = "",
    val entries: List<PahuEntry> = emptyList()
)

data class PacTable(
    val id: String = UUID.randomUUID().toString(),
    val tableNumber: String = "",
    val title: String = "",
    val entries: List<PacEntry> = emptyList()
)

data class CapacityAssessmentReading(
    val header: ReadingHeader = ReadingHeader(),
    val itLoad: String = "",
    val toolsUsed: List<CapacityToolRow> = emptyList(),
    val pahuTables: List<PahuTable> = emptyList(),
    val pacTables: List<PacTable> = emptyList(),
    val observationsAndRecommendations: List<ObservationRecommendationRow> = emptyList()
)
