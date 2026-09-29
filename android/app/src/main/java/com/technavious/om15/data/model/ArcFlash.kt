package com.technavious.om15.data.model

import java.util.UUID

data class ArcFlashPPE(
    val id: String = UUID.randomUUID().toString(),
    val category: String = "",
    val calRating: String = "",
    val clothing: String = "",
    val faceProtection: String = "",
    val gloveClass: String = ""
)

data class GloveClassReading(
    val id: String = UUID.randomUUID().toString(),
    val gloveClass: String = "",
    val acProof: String = "",
    val maxAc: String = "",
    val dcProof: String = "",
    val maxDc: String = ""
)

data class ArcFlashWorkingDistance(
    val id: String = UUID.randomUUID().toString(),
    val equipClass: String = "",
    val mm: String = "",
    val inch: String = "",
    val busGap: String = ""
)

data class ArcFlashStudyResult(
    val id: String = UUID.randomUUID().toString(),
    val busId: String = "",
    val nomKv: String = "",
    val type: String = "",
    val bolted: String = "",
    val arcing: String = "",
    val fct: String = "",
    val energy: String = "",
    val afb: String = "",
    val level: String = ""
)

data class ArcFlashReading(
    val header: ReadingHeader = ReadingHeader(),
    val ppeCategories: List<ArcFlashPPE> = emptyList(),
    val gloveClasses: List<GloveClassReading> = emptyList(),
    val workingDistances: List<ArcFlashWorkingDistance> = emptyList(),
    val studyResults: List<ArcFlashStudyResult> = emptyList(),
    val observations: String = "",
    val recommendations: String = ""
)
