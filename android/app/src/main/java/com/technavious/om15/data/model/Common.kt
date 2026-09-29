package com.technavious.om15.data.model

import java.util.UUID

enum class Department { ELECTRICAL, MECHANICAL, ELV }

enum class TestStatus { PENDING, IN_PROGRESS, PASS, FAIL }

enum class WorkflowStatus { DRAFT, SUBMITTED, REJECTED, APPROVED }

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val clientName: String = "",
    val address: String = "",
    val status: String = "ACTIVE",
    val createdAt: String = ""
)

data class TestAssignment(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String = "",
    val projectName: String = "",
    val clientName: String = "",
    val location: String = "",
    val docNo: String = "",
    val testType: TestType = TestType.VIBRATION_ANALYSIS,
    val testName: String = "",
    val assignedEngineer: String = "",
    val status: String = "IN_PROGRESS",
    val workflowStatus: WorkflowStatus = WorkflowStatus.DRAFT,
    val readings: String = "", // JSON blob of the specific reading type
    val createdAt: String = "",
    val reviewedAt: String = ""
)

enum class TestType(val displayName: String, val department: Department, val hasAiCapture: Boolean) {
    VIBRATION_ANALYSIS("Vibration Analysis", Department.MECHANICAL, true),
    THERMOGRAPHY("Thermography Inspection", Department.ELECTRICAL, true),
    BATTERY_IMPEDANCE("Battery Impedance", Department.ELECTRICAL, true),
    EARTH_STATION("Earth Station / Continuity", Department.ELECTRICAL, true),
    RACK_COOLING_INDEX("Rack Cooling Index (RCI)", Department.MECHANICAL, true),
    CFM_AIRFLOW("CFM Airflow", Department.MECHANICAL, true),
    CAPACITY_ASSESSMENT("Capacity Assessment (PAHU/PAC)", Department.MECHANICAL, true),
    LIFE_CYCLE("Life Cycle Assessment", Department.MECHANICAL, true),
    POWER_FACTOR("Power Factor", Department.ELECTRICAL, true),
    POWER_QUALITY("Power Quality", Department.ELECTRICAL, true),
    ARC_FLASH("Arc Flash Study", Department.ELECTRICAL, false),
    SPD_HEALTH("SPD Health Check", Department.ELECTRICAL, false),
    LIGHTNING_ARRESTOR("Lightning Arrestor", Department.ELECTRICAL, false),
    SPOF("Single Point of Failure", Department.ELECTRICAL, false),
    ELV_GAP("ELV Gap Assessment", Department.ELV, false),
    DG_ENDURANCE("DG Endurance", Department.ELECTRICAL, false),
    POWER_SYSTEM("Power System Study", Department.ELECTRICAL, false),
    COUPON_TEST("Coupon Test (Corrosion)", Department.MECHANICAL, false),
}

data class ObservationRecommendationRow(
    val id: String = UUID.randomUUID().toString(),
    val observation: String = "",
    val recommendation: String = "",
    val lifeCycleStatus: String = "",
    val equipmentTag: String = ""
)

data class EquipmentRow(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val make: String = "",
    val model: String = "",
    val serialNo: String = "",
    val calDate: String = "",
    val expDate: String = ""
)

data class ReadingHeader(
    val preparedBy: String = "",
    val testedBy: String = "",
    val testerEmail: String = "",
    val witnessedBy: String = "",
    val testDate: String = "",
    val status: TestStatus = TestStatus.PENDING
)
