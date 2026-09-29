package com.technavious.om15.data.model

import java.util.UUID

data class VibrationToolRow(
    val id: String = UUID.randomUUID().toString(),
    val instrumentName: String = "",
    val purpose: String = "",
    val make: String = "",
    val model: String = "",
    val calibrationIssue: String = "",
    val calibrationExpiry: String = ""
)

data class AbbreviationRow(
    val id: String = UUID.randomUUID().toString(),
    val abbreviation: String = "",
    val fullMeaning: String = ""
)

data class VibrationSummaryRow(
    val id: String = UUID.randomUUID().toString(),
    val equipmentId: String = "",
    val classDetails: String = "",
    val vertical: String = "",
    val horizontal: String = "",
    val axial: String = "",
    val maxValue: String = "",
    val condition: String = "GOOD"
)

data class VibrationSummaryGroup(
    val id: String = UUID.randomUUID().toString(),
    val groupName: String = "",
    val rows: List<VibrationSummaryRow> = emptyList()
)

data class VibrationAnalysisReading(
    val header: ReadingHeader = ReadingHeader(),
    val toolsUsed: List<VibrationToolRow> = emptyList(),
    val abbreviations: List<AbbreviationRow> = emptyList(),
    val summaryGroups: List<VibrationSummaryGroup> = emptyList(),
    val observationsAndRecommendations: List<ObservationRecommendationRow> = emptyList()
)
