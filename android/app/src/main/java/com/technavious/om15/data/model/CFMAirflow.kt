package com.technavious.om15.data.model

import java.util.UUID

data class CFMCacRow(
    val id: String = UUID.randomUUID().toString(),
    val rackRef: String = "",
    val loadW: String = "",
    val actualCFM: String = ""
)

data class CFMCacTable(
    val id: String = UUID.randomUUID().toString(),
    val cacName: String = "",
    val deltaT: String = "",
    val multiplier: String = "",
    val leftRows: List<CFMCacRow> = emptyList(),
    val rightRows: List<CFMCacRow> = emptyList()
)

data class CFMToolRow(
    val id: String = UUID.randomUUID().toString(),
    val instrumentName: String = "",
    val purpose: String = "",
    val make: String = "",
    val model: String = "",
    val calibrationIssue: String = "",
    val calibrationExpiry: String = ""
)

data class CFMReading(
    val header: ReadingHeader = ReadingHeader(),
    val equipmentList: List<EquipmentRow> = emptyList(),
    val toolsUsed: List<CFMToolRow> = emptyList(),
    val cacTables: List<CFMCacTable> = emptyList(),
    val observationsAndRecommendations: List<ObservationRecommendationRow> = emptyList()
)
