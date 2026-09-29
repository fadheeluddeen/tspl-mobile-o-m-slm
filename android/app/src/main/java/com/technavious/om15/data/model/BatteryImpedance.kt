package com.technavious.om15.data.model

import java.util.UUID

data class BatteryReading(
    val id: String = UUID.randomUUID().toString(),
    val batteryNo: Int = 0,
    val measuredIr: String = "",
    val irFailThresh: String = "",
    val irWarnThresh: String = "",
    val measuredVolt: String = "",
    val voltThresh: String = "",
    val remarks: String = ""
)

data class BatteryString(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val readings: List<BatteryReading> = emptyList()
)

data class BatteryUPS(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val location: String = "",
    val make: String = "",
    val batteryType: String = "",
    val strings: List<BatteryString> = emptyList()
)

data class BatteryImpedanceReading(
    val header: ReadingHeader = ReadingHeader(),
    val equipmentList: List<EquipmentRow> = emptyList(),
    val upsSystems: List<BatteryUPS> = emptyList(),
    val recommendations: String = ""
)
