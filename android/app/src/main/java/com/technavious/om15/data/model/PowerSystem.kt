package com.technavious.om15.data.model

import java.util.UUID

data class LoadFlowRow(
    val id: String = UUID.randomUUID().toString(),
    val location: String = "",
    val busVoltKv: String = "",
    val powerMva: String = "",
    val realPowerMw: String = "",
    val reactivePowerMvar: String = "",
    val pf: String = "",
    val isHeader: Boolean = false
)

data class PowerBusVoltageRow(
    val id: String = UUID.randomUUID().toString(),
    val busId: String = "",
    val nominalKv: String = "",
    val operatingKv: String = "",
    val percentVolt: String = "",
    val remark: String = ""
)

data class PowerShortCircuitRow(
    val id: String = UUID.randomUUID().toString(),
    val location: String = "",
    val busVoltKv: String = "",
    val threePhaseKa: String = "",
    val lineToGroundKa: String = "",
    val busRatingKa: String = "",
    val isHeader: Boolean = false
)

data class RelayCoordRow(
    val id: String = UUID.randomUUID().toString(),
    val relayNo: String = "",
    val feeder: String = "",
    val ratedCurrent: String = "",
    val relayType: String = "",
    val ctRatio: String = "",
    val isHeader: Boolean = false,
    val existingCurve: String = "", val existingPlug: String = "", val existingTms: String = "",
    val existingHiI: String = "", val existingHiT: String = "",
    val suggestedCurve: String = "", val suggestedPlug: String = "", val suggestedTms: String = "",
    val suggestedHiI: String = "", val suggestedHiT: String = "",
    val remarks: String = ""
)

data class ReleaseSettingsRow(
    val id: String = UUID.randomUUID().toString(),
    val slNo: String = "",
    val feederName: String = "",
    val ratedCurrent: String = "",
    val breakerType: String = "",
    val relayMakeType: String = "",
    val isHeader: Boolean = false,
    val existingLtPickup: String = "", val existingLtTime: String = "",
    val existingStPickup: String = "", val existingStTime: String = "",
    val existingInstant: String = "",
    val existingEarthPickup: String = "", val existingEarthTime: String = "",
    val proposedLtPickup: String = "", val proposedLtTime: String = "",
    val proposedStPickup: String = "", val proposedStTime: String = "",
    val proposedInstant: String = "",
    val proposedEarthPickup: String = "", val proposedEarthTime: String = "",
    val remarks: String = ""
)

data class PowerArcFlashRow(
    val id: String = UUID.randomUUID().toString(),
    val locationName: String = "",
    val nominalKv: String = "",
    val boltedKa: String = "",
    val arcingKa: String = "",
    val fctSecs: String = "",
    val incidentEnergy: String = "",
    val afbM: String = "",
    val workingDistanceCm: String = "",
    val energyLevel: String = "",
    val remarks: String = "",
    val isHeader: Boolean = false
)

data class PowerSystemReading(
    val header: ReadingHeader = ReadingHeader(),
    val revisionNo: String = "",
    val loadFlow: List<LoadFlowRow> = emptyList(),
    val busVoltages: List<PowerBusVoltageRow> = emptyList(),
    val shortCircuit: List<PowerShortCircuitRow> = emptyList(),
    val relayPhase: List<RelayCoordRow> = emptyList(),
    val relayEarth: List<RelayCoordRow> = emptyList(),
    val releaseSettings: List<ReleaseSettingsRow> = emptyList(),
    val arcFlashAnalysis: List<PowerArcFlashRow> = emptyList(),
    val observations: String = "",
    val recommendations: String = ""
)
