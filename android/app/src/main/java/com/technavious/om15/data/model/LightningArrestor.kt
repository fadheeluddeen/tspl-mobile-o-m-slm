package com.technavious.om15.data.model

import java.util.UUID

data class LAEquipmentRow(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val make: String = "",
    val serial: String = "",
    val model: String = "",
    val calDate: String = "",
    val dueDate: String = ""
)

data class LAReadingRow(
    val id: String = UUID.randomUUID().toString(),
    val pitName: String = "",
    val location: String = "",
    val conductorType: String = "",
    val groundingValue: String = "",
    val acceptValue: String = "",
    val remarks: String = "",
    val result: String = ""
)

data class LightningArrestorReading(
    val header: ReadingHeader = ReadingHeader(),
    val equipmentList: List<LAEquipmentRow> = emptyList(),
    val laReadings: List<LAReadingRow> = emptyList(),
    val remarks: String = "",
    val recommendations: String = ""
)
