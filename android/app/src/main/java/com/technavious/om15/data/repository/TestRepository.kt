package com.technavious.om15.data.repository

import com.google.gson.Gson
import com.technavious.om15.data.db.*
import com.technavious.om15.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TestRepository(private val db: AppDatabase) {
    @PublishedApi internal val gson = Gson()
    private val projectDao = db.projectDao()
    private val assignmentDao = db.testAssignmentDao()
    private val photoDao = db.capturedPhotoDao()

    // Projects
    fun getAllProjects(): Flow<List<ProjectEntity>> = projectDao.getAllActive()
    suspend fun getProject(id: String) = projectDao.getById(id)
    suspend fun createProject(name: String, clientName: String, address: String): ProjectEntity {
        val entity = ProjectEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            clientName = clientName,
            address = address
        )
        projectDao.insert(entity)
        return entity
    }
    suspend fun updateProject(project: ProjectEntity) = projectDao.update(project)

    // Test Assignments
    fun getAllAssignments(): Flow<List<TestAssignmentEntity>> = assignmentDao.getAll()
    fun getAssignmentsByProject(projectId: String) = assignmentDao.getByProject(projectId)
    suspend fun getAssignment(id: String) = assignmentDao.getById(id)

    suspend fun createAssignment(
        projectId: String,
        projectName: String,
        clientName: String,
        testType: TestType,
        location: String = ""
    ): TestAssignmentEntity {
        val entity = TestAssignmentEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            projectName = projectName,
            clientName = clientName,
            testType = testType.name,
            testName = testType.displayName,
            location = location
        )
        assignmentDao.insert(entity)
        return entity
    }

    suspend fun saveReadings(assignmentId: String, readings: Any) {
        val json = gson.toJson(readings)
        assignmentDao.updateReadings(assignmentId, json)
    }

    inline fun <reified T> getReadings(assignmentEntity: TestAssignmentEntity): T? {
        return try {
            gson.fromJson(assignmentEntity.readingsJson, T::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveReadingsJson(assignmentId: String, json: String) {
        assignmentDao.updateReadings(assignmentId, json)
    }

    suspend fun updateHeader(assignmentId: String, location: String, docNo: String) {
        val current = assignmentDao.getById(assignmentId) ?: return
        assignmentDao.update(current.copy(location = location, docNo = docNo))
    }

    suspend fun updateWorkflowStatus(assignmentId: String, status: WorkflowStatus) {
        assignmentDao.updateWorkflowStatus(assignmentId, status.name)
    }

    suspend fun deleteAssignment(assignmentId: String) {
        val assignment = assignmentDao.getById(assignmentId) ?: return
        assignmentDao.delete(assignment)
    }

    suspend fun deleteProject(projectId: String) {
        val project = projectDao.getById(projectId) ?: return
        projectDao.delete(project)
    }

    // Photos
    fun getPhotosByAssignment(assignmentId: String) = photoDao.getByAssignment(assignmentId)
    suspend fun getLatestPhoto(assignmentId: String, fieldKey: String) = photoDao.getLatestByField(assignmentId, fieldKey)
    suspend fun savePhoto(assignmentId: String, filePath: String, fieldKey: String): CapturedPhotoEntity {
        val entity = CapturedPhotoEntity(
            id = UUID.randomUUID().toString(),
            assignmentId = assignmentId,
            filePath = filePath,
            fieldKey = fieldKey
        )
        photoDao.insert(entity)
        return entity
    }
}
