package com.technavious.om15.data.model

import java.util.UUID

data class SPDInspectionRow(
    val id: String = UUID.randomUUID().toString(),
    val location: String = "",
    val panelName: String = "",
    val make: String = "",
    val model: String = "",
    val rating: String = "",
    val remark: String = ""
)

data class SPDReading(
    val header: ReadingHeader = ReadingHeader(),
    val inspections: List<SPDInspectionRow> = emptyList(),
    val equipmentList: List<EquipmentRow> = emptyList(),
    val observations: String = "",
    val recommendations: String = ""
)
