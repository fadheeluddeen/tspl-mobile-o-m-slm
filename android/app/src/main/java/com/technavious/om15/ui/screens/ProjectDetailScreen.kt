package com.technavious.om15.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.technavious.om15.data.db.ProjectEntity
import com.technavious.om15.data.db.TestAssignmentEntity
import com.technavious.om15.data.repository.TestRepository
import com.technavious.om15.ui.components.*
import com.technavious.om15.ui.theme.*
import kotlinx.coroutines.launch
import com.technavious.om15.data.model.TestType
import com.technavious.om15.data.schema.ReadingsDoc
import com.technavious.om15.data.schema.schemaFor

@Composable
fun ProjectDetailScreen(
    projectId: String,
    repository: TestRepository,
    onBack: () -> Unit,
    onAddTest: () -> Unit,
    onTestClick: (String) -> Unit
) {
    var project by remember { mutableStateOf<ProjectEntity?>(null) }
    val assignments by repository.getAssignmentsByProject(projectId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<TestAssignmentEntity?>(null) }

    LaunchedEffect(projectId) {
        project = repository.getProject(projectId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OMTopBar(
                title = project?.name ?: "Project",
                subtitle = project?.clientName,
                onBack = onBack,
                actions = {
                    OMTag(
                        "${assignments.size} ${if (assignments.size == 1) "Test" else "Tests"}",
                        container = Slate100, content = Slate800, border = Slate200
                    )
                }
            )
        },
        floatingActionButton = {
            if (assignments.isNotEmpty()) OMPrimaryButton("Add Test", onAddTest, icon = Icons.Default.Add, height = 60.dp)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            project?.let { p ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Apartment, null, tint = Slate500, modifier = Modifier.size(16.dp))
                    Text(p.clientName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Slate800, maxLines = 1)
                    if (p.address.isNotBlank()) {
                        Text("·", color = Slate400)
                        Icon(Icons.Default.LocationOn, null, tint = Slate500, modifier = Modifier.size(16.dp))
                        Text(p.address, style = MaterialTheme.typography.bodySmall, color = Slate600, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(color = Slate200)
            }

            if (assignments.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(ScreenPadding), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.Assignment,
                        title = "No tests yet",
                        message = "Tap + Add Test to choose an industrial test type and record meter readings.",
                        buttonText = "Add Test",
                        onClick = onAddTest
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 24.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(assignments, key = { it.id }) { assignment ->
                        AssignmentCard(
                            assignment = assignment,
                            onClick = { onTestClick(assignment.id) },
                            onDelete = { toDelete = assignment }
                        )
                    }
                }
            }
        }
    }

    toDelete?.let { a ->
        ConfirmDeleteDialog(
            title = "Delete Test Log?",
            itemName = a.testName,
            detail = "All captured meter photos and recorded data points will be lost.",
            onConfirm = {
                scope.launch { repository.deleteAssignment(a.id) }
                toDelete = null
            },
            onDismiss = { toDelete = null }
        )
    }
}

private fun countReadings(assignment: TestAssignmentEntity): Int {
    val type = runCatching { TestType.valueOf(assignment.testType) }.getOrNull() ?: return 0
    val schema = schemaFor(type)
    return ReadingsDoc.parse(assignment.readingsJson, schema).filledCount(schema).first
}

@Composable
fun AssignmentCard(assignment: TestAssignmentEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val readingCount = remember(assignment.readingsJson) { countReadings(assignment) }
    OMCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusChip(assignment.workflowStatus)
                    if (assignment.docNo.isNotBlank()) {
                        Text(assignment.docNo, style = MaterialTheme.typography.bodySmall, fontFamily = MeterMono, color = Slate600)
                    }
                    if (readingCount > 0) {
                        Text(
                            "· $readingCount ${if (readingCount == 1) "reading recorded" else "readings recorded"}",
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = Slate600
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(assignment.testName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (assignment.location.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Location: ${assignment.location}",
                        style = MaterialTheme.typography.bodySmall, color = Slate600, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            OMIconButton(Icons.Default.Delete, "Delete ${assignment.testName}", onDelete, tint = Slate500)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Slate400, modifier = Modifier.size(32.dp))
        }
    }
}
