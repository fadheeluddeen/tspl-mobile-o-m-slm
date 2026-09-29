package com.technavious.om15.data.model

import java.util.UUID

data class SPOFInspectionRow(
    val id: String = UUID.randomUUID().toString(),
    val criticalEquipment: String = "",
    val source: String = "",
    val redundant: String = "",
    val remarks: String = ""
)

data class SPOFDCSection(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val rows: List<SPOFInspectionRow> = emptyList()
)

data class SPOFReading(
    val header: ReadingHeader = ReadingHeader(),
    val scopeDescription: String = "",
    val equipmentList: List<EquipmentRow> = emptyList(),
    val dcSections: List<SPOFDCSection> = emptyList(),
    val observationsAndRecommendations: List<ObservationRecommendationRow> = emptyList()
)
