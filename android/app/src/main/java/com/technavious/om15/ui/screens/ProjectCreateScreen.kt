package com.technavious.om15.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.technavious.om15.data.repository.TestRepository
import com.technavious.om15.ui.components.*
import com.technavious.om15.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ProjectCreateScreen(
    repository: TestRepository,
    onBack: () -> Unit,
    onCreated: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var clientName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var clientError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { OMTopBar(title = "New Project", subtitle = "Technavious Field O&M", onBack = onBack) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                OMCard(containerColor = Orange50, borderColor = Orange200) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(Icons.AutoMirrored.Filled.Article, size = 40.dp)
                        Column {
                            Text("Site Audit Work Order", style = MaterialTheme.typography.titleSmall, color = Orange800)
                            Text(
                                "All test logs, thermal scans and meter readings will be grouped under this facility record.",
                                style = MaterialTheme.typography.bodySmall, color = Orange800
                            )
                        }
                    }
                }

                OMTextField(
                    label = "Project Name", required = true, value = name,
                    onValueChange = { name = it; nameError = null },
                    placeholder = "e.g. Jurong Island Substation 66kV O&M",
                    helper = "Unique facility or contract identifier",
                    error = nameError
                )
                OMTextField(
                    label = "Client Name", required = true, value = clientName,
                    onValueChange = { clientName = it; clientError = null },
                    placeholder = "e.g. ExxonMobil Asia Pacific Pte Ltd",
                    leadingIcon = Icons.Default.Apartment,
                    error = clientError
                )
                OMTextField(
                    label = "Site Address", value = address,
                    onValueChange = { address = it },
                    placeholder = "e.g. 100 Pioneer Road, Jurong Island",
                    leadingIcon = Icons.Default.LocationOn,
                    singleLine = false, minLines = 3
                )

                OMPrimaryButton(
                    text = "Create Project",
                    icon = Icons.Default.CheckCircle,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        nameError = if (name.isBlank()) "Project name is required" else null
                        clientError = if (clientName.isBlank()) "Client name is required" else null
                        if (nameError == null && clientError == null) {
                            saving = true
                            scope.launch {
                                val project = repository.createProject(name.trim(), clientName.trim(), address.trim())
                                onCreated(project.id)
                            }
                        }
                    }
                )
            }
        }
    }
}
