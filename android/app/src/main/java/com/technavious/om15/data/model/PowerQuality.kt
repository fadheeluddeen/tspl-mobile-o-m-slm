package com.technavious.om15.data.model

import java.util.UUID

data class PQHarmonicPhase(
    val max: String = "",
    val min: String = "",
    val avg: String = ""
)

data class PQAnalysisGroup(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val type: String = "MLTP",
    val dateFrom: String = "",
    val dateTo: String = "",
    val frequency: PQFrequency = PQFrequency(),
    val voltage: PQVoltage = PQVoltage(),
    val current: PQCurrent = PQCurrent(),
    val activePower: PQPower = PQPower(),
    val apparentPower: PQPower = PQPower(),
    val reactivePower: PQPower = PQPower(),
    val powerFactor: PQSimple = PQSimple(),
    val thdv: PQHarmonic = PQHarmonic(),
    val thdi: PQHarmonic = PQHarmonic(),
    val voltageUnbalance: PQSimple = PQSimple(),
    val currentUnbalance: PQSimple = PQSimple()
)

data class PQFrequency(
    val average: String = "", val limits: String = "", val remark: String = "", val imageUri: String = ""
)

data class PQVoltage(
    val ry: String = "", val yb: String = "", val br: String = "",
    val rn: String = "", val yn: String = "", val bn: String = "",
    val limits: String = "", val remark: String = "", val imageUri: String = ""
)

data class PQCurrent(
    val r: String = "", val y: String = "", val b: String = "",
    val note: String = "", val imageUri: String = ""
)

data class PQPower(
    val average: String = "", val remark: String = "", val note: String = "", val imageUri: String = ""
)

data class PQSimple(
    val average: String = "", val limits: String = "", val remark: String = "", val imageUri: String = ""
)

data class PQHarmonic(
    val r: PQHarmonicPhase = PQHarmonicPhase(),
    val y: PQHarmonicPhase = PQHarmonicPhase(),
    val b: PQHarmonicPhase = PQHarmonicPhase(),
    val limits: String = "", val remark: String = "", val imageUri: String = ""
)

data class PowerQualityReading(
    val header: ReadingHeader = ReadingHeader(),
    val equipmentList: List<ThermographyToolRow> = emptyList(),
    val analysisGroups: List<PQAnalysisGroup> = emptyList(),
    val observations: String = "",
    val recommendations: String = ""
)
