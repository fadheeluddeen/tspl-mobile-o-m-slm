package com.technavious.om15.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.technavious.om15.data.db.ProjectEntity
import com.technavious.om15.data.repository.TestRepository
import com.technavious.om15.ui.components.*
import com.technavious.om15.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    repository: TestRepository,
    onCreateProject: () -> Unit,
    onProjectClick: (String) -> Unit,
    onSettings: () -> Unit = {},
    onReports: () -> Unit = {}
) {
    val projects by repository.getAllProjects().collectAsState(initial = emptyList())
    val assignments by repository.getAllAssignments().collectAsState(initial = emptyList())
    val testCounts = remember(assignments) { assignments.groupingBy { it.projectId }.eachCount() }
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<ProjectEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OMTopBar(
                title = "O&M",
                subtitle = "Field Engineering & Testing Suite",
                leading = {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(OrangeGradient),
                        contentAlignment = Alignment.Center
                    ) { Text("O", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp) }
                },
                actions = {
                    OMTag("TECHNAVIOUS")
                    OMSecondaryButton("Reports", onReports, icon = Icons.Default.TableChart, height = 48.dp, contentColor = Green700)
                    OMIconButton(Icons.Default.Settings, "Settings", onSettings)
                }
            )
        },
        floatingActionButton = {
            if (projects.isNotEmpty()) OMPrimaryButton("New Project", onCreateProject, icon = Icons.Default.Add, height = 60.dp)
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.padding(horizontal = ScreenPadding, vertical = 16.dp)) {
                SectionHeader(
                    title = "Active Industrial Projects",
                    subtitle = "${projects.size} ${if (projects.size == 1) "facility project" else "facility projects"} configured"
                )
            }
            HorizontalDivider(color = Slate200)

            if (projects.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(ScreenPadding), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.Folder,
                        title = "No projects yet",
                        message = "Tap the + New Project button to create your first site audit project.",
                        buttonText = "Create New Project",
                        onClick = onCreateProject
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 24.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            testCount = testCounts[project.id] ?: 0,
                            onClick = { onProjectClick(project.id) },
                            onDelete = { toDelete = project }
                        )
                    }
                }
            }
        }
    }

    toDelete?.let { project ->
        ConfirmDeleteDialog(
            title = "Delete Project?",
            itemName = project.name,
            detail = "All associated meter test logs and readings will be permanently removed.",
            onConfirm = {
                scope.launch { repository.deleteProject(project.id) }
                toDelete = null
            },
            onDismiss = { toDelete = null }
        )
    }
}

private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

@Composable
fun ProjectCard(project: ProjectEntity, testCount: Int, onClick: () -> Unit, onDelete: () -> Unit) {
    OMCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OMTag("$testCount ${if (testCount == 1) "Test Log" else "Test Logs"}")
                    Text(dateFormat.format(Date(project.createdAt)), style = MaterialTheme.typography.bodySmall, fontFamily = MeterMono, color = Slate600)
                }
                Spacer(Modifier.height(8.dp))
                Text(project.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                IconLine(Icons.Default.Apartment, project.clientName, MaterialTheme.typography.bodyMedium, Slate700)
                if (project.address.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    IconLine(Icons.Default.LocationOn, project.address, MaterialTheme.typography.bodySmall, Slate600)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OMIconButton(Icons.Default.Delete, "Delete ${project.name}", onDelete, tint = Slate500)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Slate400, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun IconLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, style: androidx.compose.ui.text.TextStyle, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = Slate500, modifier = Modifier.size(16.dp))
        Text(text, style = style, color = color, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
