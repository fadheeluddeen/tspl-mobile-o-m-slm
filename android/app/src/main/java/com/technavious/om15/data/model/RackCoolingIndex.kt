package com.technavious.om15.data.model

import java.util.UUID

data class RCITemperatureRow(
    val id: String = UUID.randomUUID().toString(),
    val sNo: String = "",
    val cacRef: String = "",
    val rackRef: String = "",
    val front15U: String = "",
    val front22U: String = "",
    val front35U: String = "",
    val rear15U: String = "",
    val rear22U: String = "",
    val rear35U: String = "",
    val rhFront: String = "",
    val rhRear: String = "",
    val inletTempResult: String = "",
    val excessInletTemp: String = "",
    val deficitInletTemp: String = ""
)

data class RCICacGroup(
    val id: String = UUID.randomUUID().toString(),
    val cacName: String = "",
    val temperatureRows: List<RCITemperatureRow> = emptyList()
)

data class RackCoolingIndexReading(
    val header: ReadingHeader = ReadingHeader(),
    val equipmentList: List<EquipmentRow> = emptyList(),
    val dc1Groups: List<RCICacGroup> = emptyList(),
    val observationsAndRecommendations: List<ObservationRecommendationRow> = emptyList()
)
