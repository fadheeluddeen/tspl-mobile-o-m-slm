package com.technavious.om15.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.technavious.om15.data.db.ProjectEntity
import com.technavious.om15.data.model.Department
import com.technavious.om15.data.model.TestType
import com.technavious.om15.data.repository.TestRepository
import com.technavious.om15.ui.components.*
import com.technavious.om15.ui.theme.*
import kotlinx.coroutines.launch

private fun Department.label() = when (this) {
    Department.ELECTRICAL -> "Electrical"
    Department.MECHANICAL -> "Mechanical"
    Department.ELV -> "ELV"
}

private fun Department.icon(): Pair<ImageVector, Color> = when (this) {
    Department.ELECTRICAL -> Icons.Default.Bolt to Color(0xFFD97706)
    Department.MECHANICAL -> Icons.Default.Settings to Color(0xFF2563EB)
    Department.ELV -> Icons.Default.Router to Color(0xFF059669)
}

@Composable
fun TestSelectScreen(
    projectId: String,
    repository: TestRepository,
    onBack: () -> Unit,
    onTestSelected: (String) -> Unit
) {
    var project by remember { mutableStateOf<ProjectEntity?>(null) }
    var query by remember { mutableStateOf("") }
    var department by remember { mutableStateOf<Department?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(projectId) {
        project = repository.getProject(projectId)
    }

    val all = TestType.entries
    val filtered = all.filter {
        (department == null || it.department == department) &&
            (query.isBlank() || it.displayName.contains(query.trim(), ignoreCase = true))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OMTopBar(
                title = "Select Test Type",
                subtitle = "${all.size} industrial test protocols",
                onBack = onBack,
                actions = {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Orange100)
                            .border(1.dp, Orange200, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Orange600, modifier = Modifier.size(14.dp))
                        Text("AI Reading Available", style = MaterialTheme.typography.labelSmall, color = Orange800)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.padding(horizontal = ScreenPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    placeholder = { Text("Search test types (e.g. Vibration, Earth, Battery)", color = Slate500) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Slate500) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Orange600, unfocusedBorderColor = Slate300,
                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
                    )
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill("All (${all.size})", department == null) { department = null }
                    Department.entries.forEach { d ->
                        FilterPill(d.label(), department == d) { department = d }
                    }
                }
            }
            HorizontalDivider(color = Slate200)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.name }) { testType ->
                    TestTypeCard(testType) {
                        if (creating) return@TestTypeCard
                        creating = true
                        scope.launch {
                            val p = project ?: run { creating = false; return@launch }
                            val assignment = repository.createAssignment(
                                projectId = projectId,
                                projectName = p.name,
                                clientName = p.clientName,
                                testType = testType
                            )
                            onTestSelected(assignment.id)
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No test types match your search", style = MaterialTheme.typography.titleSmall, color = Slate600)
                            TextButton(onClick = { query = ""; department = null }) {
                                Text("Clear filters", color = Orange700, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(OrangeGradient) else Modifier.background(Color.White).border(1.dp, Slate200, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (selected) Color.White else Slate700)
    }
}

@Composable
private fun TestTypeCard(testType: TestType, onClick: () -> Unit) {
    val (deptIcon, deptColor) = testType.department.icon()
    OMCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Slate50)
                            .border(1.dp, Slate200, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(deptIcon, null, tint = deptColor, modifier = Modifier.size(14.dp))
                        Text(testType.department.label(), style = MaterialTheme.typography.labelSmall, color = Slate700)
                    }
                    run {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Orange100)
                                .border(1.dp, Orange200, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, null, tint = Orange600, modifier = Modifier.size(14.dp))
                            Text("AI Camera", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Orange800)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(testType.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Slate400, modifier = Modifier.size(32.dp))
        }
    }
}
