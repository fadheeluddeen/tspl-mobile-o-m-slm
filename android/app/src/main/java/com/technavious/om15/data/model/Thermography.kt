package com.technavious.om15.data.model

import java.util.UUID

data class ThermographyToolRow(
    val id: String = UUID.randomUUID().toString(),
    val instrumentName: String = "",
    val make: String = "",
    val model: String = "",
    val serialNumber: String = "",
    val calibrationIssue: String = "",
    val calibrationExpiry: String = ""
)

data class ThermographyInspectionRow(
    val id: String = UUID.randomUUID().toString(),
    val roomName: String = "",
    val panelName: String = "",
    val feederName: String = "",
    val maxTemp: String = "",
    val minTemp: String = "",
    val remarks: String = ""
)

data class ThermographyImageRow(
    val id: String = UUID.randomUUID().toString(),
    val roomName: String = "",
    val panelName: String = "",
    val feederName: String = "",
    val imageUri: String = "",
    val remarks: String = ""
)

data class ThermographyReading(
    val header: ReadingHeader = ReadingHeader(),
    val equipmentList: List<ThermographyToolRow> = emptyList(),
    val inspections: List<ThermographyInspectionRow> = emptyList(),
    val images: List<ThermographyImageRow> = emptyList(),
    val observations: String = "",
    val recommendations: String = ""
)
