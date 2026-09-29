package com.technavious.om15.data.db

import androidx.room.*
import com.technavious.om15.data.model.TestType
import com.technavious.om15.data.model.WorkflowStatus

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val clientName: String,
    val address: String,
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "test_assignments",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class TestAssignmentEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val projectName: String,
    val clientName: String,
    val location: String = "",
    val docNo: String = "",
    val testType: String,
    val testName: String,
    val assignedEngineer: String = "",
    val status: String = "IN_PROGRESS",
    val workflowStatus: String = "DRAFT",
    val readingsJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "captured_photos")
data class CapturedPhotoEntity(
    @PrimaryKey val id: String,
    val assignmentId: String,
    val filePath: String,
    val fieldKey: String,
    val capturedAt: Long = System.currentTimeMillis()
)
