package com.technavious.om15.data.model

import java.util.UUID

data class EarthEquipmentRow(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val make: String = "",
    val serial: String = "",
    val model: String = "",
    val calDate: String = "",
    val expDate: String = ""
)

data class EarthMeasuringRow(
    val id: String = UUID.randomUUID().toString(),
    val pitNo: String = "",
    val description: String = "",
    val measuredValue: String = "",
    val limitValue: String = "",
    val remarks: String = ""
)

data class EarthStationReading(
    val header: ReadingHeader = ReadingHeader(),
    val equipmentList: List<EarthEquipmentRow> = emptyList(),
    val readings: List<EarthMeasuringRow> = emptyList(),
    val observations: String = "",
    val recommendations: String = ""
)
