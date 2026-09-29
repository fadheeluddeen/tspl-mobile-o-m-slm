package com.technavious.om15.data.model

import java.util.UUID

data class CouponToolRow(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val make: String = "",
    val parameter: String = ""
)

data class CorrosionReadingRow(
    val id: String = UUID.randomUUID().toString(),
    val couponType: String = "Copper (Cu)",
    val oxide: String = "",
    val chloride: String = "",
    val sulfide: String = "",
    val other: String = "",
    val totalAngstroms: Double = 0.0,
    val isaLevel: String = "",
    val remarks: String = ""
)

data class CouponLocation(
    val id: String = UUID.randomUUID().toString(),
    val locationName: String = "",
    val results: List<CorrosionReadingRow> = emptyList(),
    val graphImageUri: String = ""
)

data class CouponTestReading(
    val header: ReadingHeader = ReadingHeader(),
    val toolsUsed: List<CouponToolRow> = emptyList(),
    val locations: List<CouponLocation> = emptyList(),
    val observationsAndRecommendations: List<ObservationRecommendationRow> = emptyList()
)
