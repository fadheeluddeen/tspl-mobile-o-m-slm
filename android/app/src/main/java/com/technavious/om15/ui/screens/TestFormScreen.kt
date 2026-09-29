package com.technavious.om15.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.technavious.om15.ai.GeminiMeterReader
import com.technavious.om15.ai.QwenMeterReader
import com.technavious.om15.data.db.TestAssignmentEntity
import com.technavious.om15.data.model.*
import com.technavious.om15.data.repository.TestRepository
import com.technavious.om15.data.schema.*
import com.technavious.om15.export.ExcelExporter
import com.technavious.om15.ui.components.*
import com.technavious.om15.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun Department.label() = when (this) {
    Department.ELECTRICAL -> "Electrical"
    Department.MECHANICAL -> "Mechanical"
    Department.ELV -> "ELV"
}

/** Green / amber / red colouring for calculated status values (condition, pass/fail, life-cycle status). */
fun statusColors(value: String): Pair<Color, Color>? {
    val v = value.lowercase()
    return when {
        v.isBlank() || v == "..." -> null
        v == "good" || v == "pass" || v == "ok" || v.startsWith("fit for") -> Color(0xFFDCFCE7) to Color(0xFF15803D)
        v == "satisfactory" || v == "warn" || v.startsWith("degrading") -> Color(0xFFFEF3C7) to Color(0xFF92400E)
        v == "unsatisfactory" || v.startsWith("approaching") -> Color(0xFFFFEDD5) to Color(0xFF9A3412)
        v == "unacceptable" || v == "fail" || v.startsWith("high resistance") || v.contains("end-of-life") -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
        else -> null
    }
}

@Composable
fun TestFormScreen(
    assignmentId: String,
    repository: TestRepository,
    onBack: () -> Unit,
    onNavigateToCamera: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var assignment by remember { mutableStateOf<TestAssignmentEntity?>(null) }
    var doc by remember { mutableStateOf<ReadingsDoc?>(null) }
    var version by remember { mutableIntStateOf(0) }
    val photos = remember { mutableStateMapOf<String, String>() }
    var location by remember { mutableStateOf("") }
    var docNo by remember { mutableStateOf("") }
    var readingKey by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var rowToDelete by remember { mutableStateOf<Pair<TableSchema, Row>?>(null) }

    val testType = assignment?.let { runCatching { TestType.valueOf(it.testType) }.getOrNull() }
    val schema = remember(testType) { testType?.let(::schemaFor) }

    LaunchedEffect(assignmentId) {
        val a = repository.getAssignment(assignmentId) ?: return@LaunchedEffect
        assignment = a
        location = a.location
        docNo = a.docNo
        val type = runCatching { TestType.valueOf(a.testType) }.getOrNull() ?: return@LaunchedEffect
        doc = ReadingsDoc.parse(a.readingsJson, schemaFor(type))
    }

    LaunchedEffect(assignmentId) {
        repository.getPhotosByAssignment(assignmentId).collect { list ->
            list.forEach { if (File(it.filePath).exists()) photos[it.fieldKey] = it.filePath }
        }
    }

    suspend fun save() {
        val d = doc ?: return
        repository.saveReadingsJson(assignmentId, d.toJson())
        repository.updateHeader(assignmentId, location.trim(), docNo.trim())
        repository.updateWorkflowStatus(assignmentId, WorkflowStatus.SUBMITTED)
        assignment = repository.getAssignment(assignmentId)
    }

    fun changed() { version++ }

    val (filled, total) = remember(version, doc) { doc?.let { d -> schema?.let { d.filledCount(it) } } ?: (0 to 0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OMTopBar(
                title = testType?.displayName ?: "Test",
                subtitle = testType?.let { "${it.displayName} · ${it.department.label()}" },
                onBack = onBack,
                actions = {
                    OMSecondaryButton(
                        text = if (exporting) "Downloading..." else "Download Excel",
                        icon = Icons.Default.Download,
                        height = 48.dp,
                        enabled = !exporting,
                        contentColor = Green700,
                        onClick = {
                            exporting = true
                            scope.launch {
                                try {
                                    save()
                                    val saved = ExcelExporter(context).export(assignmentId, repository)
                                    Toast.makeText(context, if (saved != null) "Downloaded: ${saved.displayPath}" else "Download failed", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Download failed: ${e.message?.take(120)}", Toast.LENGTH_LONG).show()
                                } finally {
                                    exporting = false
                                }
                            }
                        }
                    )
                    OMPrimaryButton("Save", icon = Icons.Default.Save, height = 48.dp, onClick = {
                        scope.launch { save(); Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() }
                    })
                }
            )
        },
        bottomBar = {
            if (total > 0) {
                Surface(color = Color.White, shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp).padding(horizontal = ScreenPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Completion:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = Slate600)
                        LinearProgressIndicator(
                            progress = { filled.toFloat() / total },
                            modifier = Modifier.width(160.dp).height(10.dp).clip(RoundedCornerShape(50)),
                            color = Orange600, trackColor = Slate200, drawStopIndicator = {}
                        )
                        Text("$filled / $total", style = MaterialTheme.typography.labelMedium, fontFamily = MeterMono, color = Slate800)
                        Spacer(Modifier.weight(1f))
                        OMPrimaryButton("Save Changes", icon = Icons.Default.Save, height = 48.dp, onClick = {
                            scope.launch { save(); Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() }
                        })
                    }
                }
            }
        }
    ) { padding ->
        val d = doc
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OMCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepBadge("1")
                        Spacer(Modifier.width(10.dp))
                        Text("Header Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        assignment?.let { StatusChip(it.workflowStatus) }
                    }
                    HorizontalDivider(color = Slate200, modifier = Modifier.padding(vertical = 14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OMTextField(
                            label = "Location", value = location, onValueChange = { location = it },
                            placeholder = "e.g. Basement 2, Chiller Plant Room",
                            leadingIcon = Icons.Default.LocationOn, modifier = Modifier.weight(1f)
                        )
                        OMTextField(
                            label = "Doc No.", value = docNo, onValueChange = { docNo = it },
                            placeholder = "e.g. TECH-VA-2026-01",
                            leadingIcon = Icons.AutoMirrored.Filled.Article, modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MeterMono)
                        )
                    }
                }
            }

            if (d != null && schema != null) {
                schema.tables.forEachIndexed { tIndex, table ->
                    val rows = d.rows(table.id)
                    item(key = "hdr_${table.id}_$version") {
                        Row(Modifier.padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            StepBadge("${tIndex + 2}")
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(table.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${rows.size} ${if (rows.size == 1) "row" else "rows"} · ${table.columns.count { it.isInput }} inputs per row",
                                    style = MaterialTheme.typography.bodySmall, color = Slate600)
                            }
                            OMPrimaryButton("Add Row", icon = Icons.Default.Add, height = 48.dp, onClick = {
                                rows += table.newRow(rows.size)
                                changed()
                            })
                        }
                    }
                    if (rows.isEmpty()) {
                        item(key = "empty_${table.id}_$version") {
                            OMCard(containerColor = Orange50, borderColor = Orange200) {
                                Text("No rows yet — tap Add Row for each item you measure.", style = MaterialTheme.typography.bodyMedium, color = Orange800)
                            }
                        }
                    }
                    items(rows, key = { "${table.id}_${it[ROW_ID]}" }) { row ->
                        val rowId = row[ROW_ID].orEmpty()
                        RowCard(
                            index = rows.indexOf(row) + 1,
                            table = table,
                            row = row,
                            version = version,
                            photos = photos,
                            readingKey = readingKey,
                            onChange = { key, value ->
                                row[key] = value
                                table.calc(row)
                                changed()
                            },
                            onCamera = { colKey ->
                                scope.launch {
                                    save()
                                    onNavigateToCamera(ReadingsDoc.cellKey(table.id, rowId, colKey))
                                }
                            },
                            onReread = { col, path ->
                                if (readingKey != null) return@RowCard
                                val cellKey = ReadingsDoc.cellKey(table.id, rowId, col.key)
                                readingKey = cellKey
                                scope.launch {
                                    try {
                                        val reading = readMeter(context, path, col.label)
                                        if (reading.isNotBlank()) {
                                            row[col.key] = reading
                                            table.calc(row)
                                            changed()
                                            Toast.makeText(context, "${col.header}: $reading", Toast.LENGTH_SHORT).show()
                                        } else Toast.makeText(context, "No reading detected", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Re-read failed: ${e.message?.take(100)}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        readingKey = null
                                    }
                                }
                            },
                            onDelete = { rowToDelete = table to row }
                        )
                    }
                }
            }
        }
    }

    rowToDelete?.let { (table, row) ->
        val first = table.columns.firstOrNull { it.type == ColType.TEXT }?.let { row[it.key] }.orEmpty()
        ConfirmDeleteDialog(
            title = "Delete Row?",
            itemName = first.ifBlank { "this row" },
            detail = "Its readings will be removed from ${table.title}.",
            onConfirm = {
                doc?.rows(table.id)?.remove(row)
                changed()
                rowToDelete = null
            },
            onDismiss = { rowToDelete = null }
        )
    }
}

@Composable
private fun StepBadge(text: String) {
    Box(
        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Orange100).border(1.dp, Orange200, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) { Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Orange800) }
}

@Composable
private fun RowCard(
    index: Int,
    table: TableSchema,
    row: Row,
    version: Int,
    photos: Map<String, String>,
    readingKey: String?,
    onChange: (String, String) -> Unit,
    onCamera: (String) -> Unit,
    onReread: (Col, String) -> Unit,
    onDelete: () -> Unit
) {
    val rowId = row[ROW_ID].orEmpty()
    val title = table.columns.firstOrNull { it.type == ColType.TEXT }?.let { row[it.key] }.orEmpty()
    OMCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(Slate50).border(1.dp, Slate200, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("$index", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700) }
            Text(title.ifBlank { "Row $index" }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            OMIconButton(Icons.Default.Delete, "Delete row", onDelete, tint = Slate500)
        }
        Spacer(Modifier.height(12.dp))
        table.columns.chunked(3).forEach { chunk ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                chunk.forEach { col ->
                    val key = ReadingsDoc.cellKey(table.id, rowId, col.key)
                    Box(Modifier.weight(1f)) {
                        when (col.type) {
                            ColType.CALC -> CalcCell(col, row[col.key].orEmpty())
                            ColType.SELECT -> SelectCell(col, row[col.key].orEmpty()) { onChange(col.key, it) }
                            else -> InputCell(
                                col = col,
                                value = row[col.key].orEmpty(),
                                onValueChange = { onChange(col.key, it) },
                                photoPath = photos[key],
                                reading = readingKey == key,
                                onCamera = { onCamera(col.key) },
                                onReread = { path -> onReread(col, path) }
                            )
                        }
                    }
                }
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private val fieldColors @Composable get() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Orange600, unfocusedBorderColor = Slate300,
    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, cursorColor = Orange600
)

@Composable
private fun InputCell(
    col: Col,
    value: String,
    onValueChange: (String) -> Unit,
    photoPath: String?,
    reading: Boolean,
    onCamera: () -> Unit,
    onReread: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(col.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (col.type == ColType.NUMBER) KeyboardType.Decimal else KeyboardType.Text),
        textStyle = if (col.type == ColType.NUMBER) MaterialTheme.typography.titleMedium.copy(fontFamily = MeterMono, fontWeight = FontWeight.Bold)
        else MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors,
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (photoPath != null) {
                    if (reading) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), color = Orange600, strokeWidth = 2.dp)
                    else IconButton(onClick = { onReread(photoPath) }) {
                        Icon(Icons.Default.AutoAwesome, "Re-read ${col.header} with AI", tint = Orange600)
                    }
                }
                IconButton(onClick = onCamera) {
                    Icon(Icons.Default.CameraAlt, "Photo for ${col.header}", tint = if (photoPath != null) Green700 else Slate500)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectCell(col: Col, value: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(col.label, maxLines = 1) },
            placeholder = { Text("Select", color = Slate400) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            col.options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); open = false })
            }
        }
    }
}

@Composable
private fun CalcCell(col: Col, value: String) {
    val colors = statusColors(value)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors?.first ?: Slate100)
            .border(BorderStroke(1.dp, Slate200), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("${col.label} · auto", fontSize = 11.sp, color = Slate500, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = MeterMono),
            fontWeight = FontWeight.Bold,
            color = colors?.second ?: Slate900,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

private suspend fun readMeter(context: Context, imagePath: String, label: String): String {
    if (GeminiMeterReader.useGemini(context)) {
        try {
            val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(imagePath) } ?: return ""
            return withContext(Dispatchers.IO) {
                GeminiMeterReader(GeminiMeterReader.getApiKey(context)).readMeterImage(bitmap, label, null)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Gemini error: ${e.message?.take(100)} - trying offline AI", Toast.LENGTH_LONG).show()
        }
    }
    val qwen = QwenMeterReader.getInstance()
    if (!qwen.isLoaded()) withContext(Dispatchers.IO) { qwen.loadModel(context) }
    if (!qwen.isLoaded()) return ""
    return withContext(Dispatchers.IO) { qwen.readMeterImage(imagePath, label, null) }
}
