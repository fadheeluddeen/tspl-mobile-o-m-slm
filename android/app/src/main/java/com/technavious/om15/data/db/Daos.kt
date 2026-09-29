package com.technavious.om15.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    fun getAllActive(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)
}

@Dao
interface TestAssignmentDao {
    @Query("SELECT * FROM test_assignments ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<TestAssignmentEntity>>

    @Query("SELECT * FROM test_assignments WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun getByProject(projectId: String): Flow<List<TestAssignmentEntity>>

    @Query("SELECT * FROM test_assignments WHERE id = :id")
    suspend fun getById(id: String): TestAssignmentEntity?

    @Query("SELECT * FROM test_assignments WHERE testType = :testType ORDER BY updatedAt DESC")
    fun getByTestType(testType: String): Flow<List<TestAssignmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assignment: TestAssignmentEntity)

    @Update
    suspend fun update(assignment: TestAssignmentEntity)

    @Query("UPDATE test_assignments SET readingsJson = :json, updatedAt = :now WHERE id = :id")
    suspend fun updateReadings(id: String, json: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE test_assignments SET workflowStatus = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateWorkflowStatus(id: String, status: String, now: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(assignment: TestAssignmentEntity)
}

@Dao
interface CapturedPhotoDao {
    @Query("SELECT * FROM captured_photos WHERE assignmentId = :assignmentId")
    fun getByAssignment(assignmentId: String): Flow<List<CapturedPhotoEntity>>

    @Query("SELECT * FROM captured_photos WHERE assignmentId = :assignmentId AND fieldKey = :fieldKey ORDER BY capturedAt DESC LIMIT 1")
    suspend fun getLatestByField(assignmentId: String, fieldKey: String): CapturedPhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: CapturedPhotoEntity)

    @Delete
    suspend fun delete(photo: CapturedPhotoEntity)
}
