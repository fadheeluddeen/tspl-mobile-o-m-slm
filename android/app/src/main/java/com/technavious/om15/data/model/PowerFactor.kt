package com.technavious.om15.data.model

import java.util.UUID

data class PowerFactorBankRow(
    val id: String = UUID.randomUUID().toString(),
    val feederName: String = "",
    val bankKvar: String = "",
    val breakerRating: String = "",
    val contactorRating: String = "",
    val obtainedCap: String = "",
    val actualRY: String = "",
    val actualYB: String = "",
    val actualBR: String = "",
    val measuredKvar: String = "",
    val acceptableLimit: String = "",
    val remarks: String = ""
)

data class PowerFactorBalanceRow(
    val id: String = UUID.randomUUID().toString(),
    val feederName: String = "",
    val caseTemp: String = "",
    val currentR: String = "",
    val currentY: String = "",
    val currentB: String = "",
    val remarks: String = ""
)

data class PowerFactorPanel(
    val id: String = UUID.randomUUID().toString(),
    val panelName: String = "",
    val bankRows: List<PowerFactorBankRow> = emptyList(),
    val balanceRows: List<PowerFactorBalanceRow> = emptyList()
)

data class PowerFactorReading(
    val header: ReadingHeader = ReadingHeader(),
    val equipmentList: List<EquipmentRow> = emptyList(),
    val panels: List<PowerFactorPanel> = emptyList(),
    val observations: String = "",
    val recommendations: String = ""
)
