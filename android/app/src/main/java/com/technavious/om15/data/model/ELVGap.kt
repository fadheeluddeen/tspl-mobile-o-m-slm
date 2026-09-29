package com.technavious.om15.data.model

import java.util.UUID

data class ELVChecklistRow(
    val id: String = UUID.randomUUID().toString(),
    val no: String = "",
    val description: String = "",
    val result: String = "",
    val remarks: String = "",
    val correctiveActions: String = "",
    val isHeader: Boolean = false
)

data class ELVGapAnalysisRow(
    val id: String = UUID.randomUUID().toString(),
    val no: Int = 0,
    val standard: String = "",
    val section: String = "",
    val requirement: String = "",
    val system: String = "",
    val implementation: String = "",
    val compliance: String = "",
    val correctiveActions: String = ""
)

data class ELVGapAssessmentReading(
    val header: ReadingHeader = ReadingHeader(),
    val systems: Map<String, List<ELVChecklistRow>> = emptyMap(),
    val gapAnalysisRows: List<ELVGapAnalysisRow> = emptyList(),
    val observationsAndRecommendations: List<ObservationRecommendationRow> = emptyList()
)
