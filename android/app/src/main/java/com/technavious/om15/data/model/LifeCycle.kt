package com.technavious.om15.data.model

import java.util.UUID

data class LifeCycleAssessmentRow(
    val id: String = UUID.randomUUID().toString(),
    val equipmentTag: String = "",
    val makeModel: String = "",
    val serialNo: String = "",
    val location: String = "",
    val installYear: String = "",
    val currentYear: String = "",
    val ageYrs: String = "",
    val vibrationScore: String = "",
    val corrosionScore: String = "",
    val maintScore: String = "",
    val healthIndex: String = "",
    val runningLifeYrs: String = "",
    val utilizationRatio: String = "",
    val utilizationFactor: String = "",
    val designLifeYrs: String = "",
    val adjustedLifeAL: String = "",
    val rulYrs: String = "",
    val rulPct: String = "",
    val lifeCycleStatus: String = "",
    val remarksRul: String = "",
    val surfaceTemp: String = "",
    val thermalImageUri: String = "",
    val vibrationVelocity: String = ""
)

data class LifeCycleReading(
    val header: ReadingHeader = ReadingHeader(),
    val equipmentList: List<EquipmentRow> = emptyList(),
    val lifeCycleRows: List<LifeCycleAssessmentRow> = emptyList(),
    val rulRows: List<LifeCycleAssessmentRow> = emptyList(),
    val observationsAndRecommendations: List<ObservationRecommendationRow> = emptyList()
)
