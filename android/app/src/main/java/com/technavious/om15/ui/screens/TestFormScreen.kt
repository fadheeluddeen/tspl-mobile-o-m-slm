package com.technavious.om15.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.technavious.om15.ai.GeminiMeterReader
import com.technavious.om15.ai.ReadingRequest
import com.technavious.om15.ai.QwenMeterReader
import com.technavious.om15.data.db.TestAssignmentEntity
import com.technavious.om15.data.model.*
import com.technavious.om15.data.repository.TestRepository
import com.technavious.om15.data.schema.*
import com.technavious.om15.data.schema.Row
import com.technavious.om15.export.ExcelExporter
import com.technavious.om15.export.ReportFiles
import com.technavious.om15.export.SavedFile
import com.technavious.om15.ui.components.*
import com.technavious.om15.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Locale

private fun Department.label() = when (this) {
    Department.ELECTRICAL -> "Electrical"
    Department.MECHANICAL -> "Mechanical"
    Department.ELV -> "ELV"
}

fun toneColors(value: String?): Pair<Color, Color>? =
    StatusTone.of(value)?.let { Color(it.fillArgb.toLong(16).toInt()) to Color(it.fontArgb.toLong(16).toInt()) }

/** One line in the form's scrolling list. */
private sealed interface Entry { val key: String }
private class FieldsEntry(val block: FieldsBlock, override val key: String) : Entry
private class SummaryEntry(val block: SummaryBlock, override val key: String) : Entry
private class AddGroupEntry(val set: GroupSet, override val key: String) : Entry
private class GroupEntry(val set: GroupSet, val name: String, val index: Int, override val key: String) : Entry
private class TableEntry(val table: TableSchema, val group: Pair<GroupSet, String>?, val rowCount: Int, override val key: String) : Entry
private class SubGroupEntry(val table: TableSchema, val group: Pair<GroupSet, String>?, val value: String, override val key: String) : Entry
private class RowEntry(val table: TableSchema, val row: Row, val index: Int, val group: Pair<GroupSet, String>?) : Entry {
    override val key get() = "row_${table.id}_${row[ROW_ID]}"
}
private class EmptyEntry(val text: String, override val key: String) : Entry

private fun buildEntries(doc: ReadingsDoc): List<Entry> {
    val out = mutableListOf<Entry>()
    val done = HashSet<GroupSet>()
    fun tableEntries(t: TableSchema, rows: List<Row>, group: Pair<GroupSet, String>?) {
        val gk = group?.second?.let { "_${group.first.label}_$it" } ?: ""
        out += TableEntry(t, group, rows.count { !it.isSectionHeader() }, "t_${t.id}$gk")
        if (rows.isEmpty()) out += EmptyEntry(if (t.canAddRows || t.sectionHeaders) "No rows yet — tap Add Row." else "No data.", "e_${t.id}$gk")
        val sub = t.subGroupCol
        var index = 0
        if (sub != null) {
            rows.groupBy { it[sub].orEmpty() }.forEach { (value, subRows) ->
                out += SubGroupEntry(t, group, value, "s_${t.id}${gk}_$value")
                subRows.forEachIndexed { i, row -> out += RowEntry(t, row, i + 1, group) }
            }
        } else rows.forEach { row -> if (!row.isSectionHeader()) index++; out += RowEntry(t, row, index, group) }
    }
    doc.schema.blocks.forEachIndexed { bi, block ->
        when (block) {
            is FieldsBlock -> out += FieldsEntry(block, "f_$bi")
            is SummaryBlock -> out += SummaryEntry(block, "sum_$bi")
            is TableBlock -> {
                val set = doc.schema.groupSetFor(block.table.id)
                if (set == null) tableEntries(block.table, doc.rows(block.table.id), null)
                else if (done.add(set)) {
                    out += AddGroupEntry(set, "add_${set.label}")
                    doc.groupNames(set).forEachIndexed { gi, name ->
                        out += GroupEntry(set, name, gi, "g_${set.label}_$gi")
                        set.tables.mapNotNull { doc.schema.table(it) }.forEach { t ->
                            tableEntries(t, doc.rows(t.id).filter { it[set.key] == name }, set to name)
                        }
                    }
                }
            }
        }
    }
    return out
}

@Composable
fun TestFormScreen(
    assignmentId: String,
    repository: TestRepository,
    onBack: () -> Unit,
    onNavigateToCamera: (String) -> Unit,
    onViewReport: (Uri, String) -> Unit
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
    var groupToDelete by remember { mutableStateOf<Pair<GroupSet, String>?>(null) }
    var downloaded by remember { mutableStateOf<SavedFile?>(null) }
    var uploadTarget by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val cellKey = uploadTarget
        uploadTarget = null
        if (uri == null || cellKey == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.filesDir, "photos").apply { mkdirs() }
                    File(dir, "UPL_${System.currentTimeMillis()}.jpg").also { out ->
                        context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
                    }
                }
                repository.savePhoto(assignmentId, file.absolutePath, cellKey)
                Toast.makeText(context, "Photo uploaded", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Upload failed: ${e.message?.take(100)}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val testType = assignment?.let { runCatching { TestType.valueOf(it.testType) }.getOrNull() }

    LaunchedEffect(assignmentId) {
        val a = repository.getAssignment(assignmentId) ?: return@LaunchedEffect
        assignment = a
        location = a.location
        docNo = a.docNo
        val type = runCatching { TestType.valueOf(a.testType) }.getOrNull() ?: return@LaunchedEffect
        doc = withContext(Dispatchers.Default) { ReadingsDoc.parse(a.readingsJson, schemaFor(type)) }
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

    fun editRow(table: TableSchema, row: Row, key: String, value: String) {
        val d = doc ?: return
        row[key] = value
        if (!row.isSectionHeader()) table.calc(row, d.fields)
        d.schema.fieldCalc(d)
        changed()
    }

    fun addRow(table: TableSchema, group: Pair<GroupSet, String>?, extra: Map<String, String> = emptyMap()) {
        val d = doc ?: return
        val rows = d.rows(table.id)
        val inGroup = group?.let { (set, name) -> rows.count { it[set.key] == name } } ?: rows.size
        val groupExtra = group?.let { mapOf(it.first.key to it.second) }.orEmpty()
        rows += table.newRow(inGroup, d.fields, groupExtra + extra)
        d.schema.fieldCalc(d)
        changed()
    }

    val entries = remember(version, doc) { doc?.let(::buildEntries) ?: emptyList() }
    val (filled, total) = remember(version, doc) { doc?.filledCount() ?: (0 to 0) }

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
                        icon = Icons.Default.Download, height = 48.dp, enabled = !exporting, contentColor = Green700,
                        onClick = {
                            exporting = true
                            scope.launch {
                                try {
                                    save()
                                    val saved = ExcelExporter(context).export(assignmentId, repository)
                                    if (saved != null) downloaded = saved else Toast.makeText(context, "Download failed", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Download failed: ${e.message?.take(120)}", Toast.LENGTH_LONG).show()
                                } finally { exporting = false }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                OMCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Test Header", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        assignment?.let { StatusChip(it.workflowStatus) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OMTextField(label = "Colo / Floor Name", value = location, onValueChange = { location = it },
                            leadingIcon = Icons.Default.LocationOn, modifier = Modifier.weight(1f))
                        OMTextField(label = "DOC NO", value = docNo, onValueChange = { docNo = it },
                            leadingIcon = Icons.AutoMirrored.Filled.Article, modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MeterMono))
                    }
                }
            }
            if (d == null) {
                item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Orange600) } }
            } else items(entries, key = { it.key }) { e ->
                when (e) {
                    is FieldsEntry -> FieldsCard(e.block, d.fields, version) { k, v -> d.fields[k] = v; d.recalc(); changed() }
                    is SummaryEntry -> SummaryCard(e.block.title, e.block.headers, remember(version) { e.block.build(d) })
                    is AddGroupEntry -> AddGroupBar(e.set, d.groupNames(e.set).size) { option -> d.addGroup(e.set, option); changed() }
                    is GroupEntry -> GroupHeader(e.set, e.name, onRename = { new -> d.renameGroup(e.set, e.name, new); changed() },
                        onDelete = { groupToDelete = e.set to e.name })
                    is TableEntry -> TableHeader(e.table, e.rowCount, e.group != null,
                        onAddRow = { addRow(e.table, e.group) },
                        onAddSectionHeader = { addRow(e.table, e.group, mapOf(ROW_HEADER to "1", e.table.columns.first().key to "NEW SECTION")) },
                        onAddSubGroup = e.table.subGroupCol?.takeIf { e.table.canAddRows }?.let { sub ->
                            {
                                val count = d.rows(e.table.id).filter { r -> e.group == null || r[e.group.first.key] == e.group.second }
                                    .map { it[sub] }.distinct().size
                                addRow(e.table, e.group, mapOf(sub to "${e.table.col(sub)?.header?.uppercase() ?: sub} ${count + 1}"))
                            }
                        })
                    is SubGroupEntry -> SubGroupHeader(e.table, e.value, onAdd = if (e.table.canAddRows) {
                        { addRow(e.table, e.group, mapOf(e.table.subGroupCol!! to e.value)) }
                    } else null)
                    is EmptyEntry -> Text(e.text, style = MaterialTheme.typography.bodySmall, color = Slate500, modifier = Modifier.padding(start = 8.dp))
                    is RowEntry -> RowCard(
                        table = e.table, row = e.row, index = e.index, version = version, photos = photos, readingKey = readingKey,
                        hiddenKeys = setOfNotNull(e.group?.first?.key, e.table.subGroupCol),
                        onChange = { k, v -> editRow(e.table, e.row, k, v) },
                        onCamera = { colKey ->
                            scope.launch { save(); onNavigateToCamera(ReadingsDoc.cellKey(e.table.id, e.row[ROW_ID].orEmpty(), colKey)) }
                        },
                        onUpload = { colKey ->
                            uploadTarget = ReadingsDoc.cellKey(e.table.id, e.row[ROW_ID].orEmpty(), colKey)
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onReread = { col, path ->
                            if (readingKey != null) return@RowCard
                            val cellKey = ReadingsDoc.cellKey(e.table.id, e.row[ROW_ID].orEmpty(), col.key)
                            readingKey = cellKey
                            scope.launch {
                                try {
                                    val tag = e.table.columns.firstOrNull { it.type == ColType.TEXT }?.let { e.row[it.key] }.orEmpty()
                                    val reading = readMeter(context, path, col, ReadingRequest(col.header, col.unit,
                                        assignment?.projectName.orEmpty(), tag, testType?.displayName.orEmpty()))
                                    if (reading.isNotBlank()) { editRow(e.table, e.row, col.key, reading); Toast.makeText(context, "${col.header}: $reading", Toast.LENGTH_SHORT).show() }
                                    else Toast.makeText(context, "No reading detected", Toast.LENGTH_SHORT).show()
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "Re-read failed: ${ex.message?.take(100)}", Toast.LENGTH_LONG).show()
                                } finally { readingKey = null }
                            }
                        },
                        onDelete = if (e.row.isFixed()) null else ({ rowToDelete = e.table to e.row })
                    )
                }
            }
        }
    }

    downloaded?.let { file ->
        val name = file.displayPath.substringAfterLast('/')
        AlertDialog(
            onDismissRequest = { downloaded = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp),
            icon = { IconTile(Icons.Default.Download, size = 52.dp) },
            title = { Text("Excel downloaded", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Saved to:", style = MaterialTheme.typography.bodySmall, color = Slate600)
                    Text(file.displayPath, style = MaterialTheme.typography.bodyMedium, fontFamily = MeterMono, color = Slate900)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OMSecondaryButton("Share", { ReportFiles.share(context, file.uri, name) }, icon = Icons.Default.Share, height = 48.dp)
                    OMSecondaryButton("Open with", { ReportFiles.openWith(context, file.uri, name) }, icon = Icons.AutoMirrored.Filled.OpenInNew, height = 48.dp)
                    OMPrimaryButton("View", { downloaded = null; onViewReport(file.uri, name) }, icon = Icons.Default.Visibility, height = 48.dp)
                }
            },
            dismissButton = { TextButton(onClick = { downloaded = null }) { Text("Close", color = Slate600) } }
        )
    }

    rowToDelete?.let { (table, row) ->
        val first = table.columns.firstOrNull { it.type == ColType.TEXT }?.let { row[it.key] }.orEmpty()
        ConfirmDeleteDialog(
            title = "Delete Row?", itemName = first.ifBlank { "this row" }, detail = "Its readings will be removed from ${table.title}.",
            onConfirm = { doc?.let { it.rows(table.id).remove(row); it.schema.fieldCalc(it) }; changed(); rowToDelete = null },
            onDismiss = { rowToDelete = null }
        )
    }

    groupToDelete?.let { (set, name) ->
        ConfirmDeleteDialog(
            title = "Delete ${set.label}?", itemName = name, detail = "All its rows will be removed.",
            onConfirm = { doc?.deleteGroup(set, name); changed(); groupToDelete = null },
            onDismiss = { groupToDelete = null }
        )
    }
}

// ---------------- building blocks ----------------

private val fieldColors @Composable get() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Orange600, unfocusedBorderColor = Slate300,
    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, cursorColor = Orange600
)

@Composable
private fun FieldsCard(block: FieldsBlock, fields: Fields, version: Int, onChange: (String, String) -> Unit) {
    OMCard {
        Text(block.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        val short = block.cols.filter { it.type != ColType.LONGTEXT }
        short.chunked(3).forEach { chunk ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                chunk.forEach { col -> Box(Modifier.weight(1f)) { ValueCell(col, fields[col.key].orEmpty(), version) { onChange(col.key, it) } } }
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        block.cols.filter { it.type == ColType.LONGTEXT }.forEach { col ->
            Box(Modifier.padding(bottom = 12.dp)) { ValueCell(col, fields[col.key].orEmpty(), version) { onChange(col.key, it) } }
        }
    }
}

@Composable
private fun AddGroupBar(set: GroupSet, count: Int, onAdd: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("${set.label}s", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("$count added", style = MaterialTheme.typography.bodySmall, color = Slate600)
        }
        if (set.options.isEmpty()) OMPrimaryButton("Add ${set.label}", { onAdd("") }, icon = Icons.Default.Add, height = 48.dp)
        else set.options.forEach { opt -> OMPrimaryButton("Add $opt ${set.label}", { onAdd(opt) }, icon = Icons.Default.Add, height = 48.dp) }
    }
}

@Composable
private fun GroupHeader(set: GroupSet, name: String, onRename: (String) -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(OrangeGradient)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(set.label.uppercase(), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = name, onValueChange = onRename, singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.5f), cursorColor = Color.White,
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp)
        )
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete ${set.label}", tint = Color.White) }
    }
}

@Composable
private fun TableHeader(
    table: TableSchema, rowCount: Int, inGroup: Boolean,
    onAddRow: () -> Unit, onAddSectionHeader: () -> Unit, onAddSubGroup: (() -> Unit)?
) {
    Row(
        Modifier.fillMaxWidth().padding(top = if (inGroup) 0.dp else 8.dp, start = if (inGroup) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(Orange500))
        Column(Modifier.weight(1f)) {
            Text(table.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("$rowCount ${if (rowCount == 1) "row" else "rows"}", style = MaterialTheme.typography.bodySmall, color = Slate600)
        }
        if (table.sectionHeaders) OMSecondaryButton("Section Header", onAddSectionHeader, icon = Icons.Default.Add, height = 44.dp)
        if (onAddSubGroup != null) OMPrimaryButton("Add ${table.col(table.subGroupCol!!)?.header ?: "Group"}", onAddSubGroup, icon = Icons.Default.Add, height = 44.dp)
        else if (table.canAddRows) OMPrimaryButton("Add Row", onAddRow, icon = Icons.Default.Add, height = 44.dp)
    }
}

@Composable
private fun SubGroupHeader(table: TableSchema, value: String, onAdd: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp).clip(RoundedCornerShape(10.dp)).background(Slate700).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${(table.col(table.subGroupCol!!)?.header ?: "").uppercase()}: ${value.uppercase()}", color = Color.White,
            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onAdd != null) TextButton(onClick = onAdd) { Text("+ Row", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun SummaryCard(title: String, headers: List<String>, rows: List<List<String>>) {
    val head = headers.ifEmpty { rows.firstOrNull().orEmpty() }
    val body = if (headers.isEmpty()) rows.drop(1) else rows
    OMCard(containerColor = Color(0xFFF0F9FF), borderColor = Color(0xFFBAE6FD)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF0369A1), modifier = Modifier.size(18.dp))
            Text("$title · auto", style = MaterialTheme.typography.titleSmall, color = Color(0xFF0369A1))
        }
        Spacer(Modifier.height(10.dp))
        if (body.isEmpty()) {
            Text("Fills in automatically from the readings above.", style = MaterialTheme.typography.bodySmall, color = Slate500)
            return@OMCard
        }
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            val w = 170.dp
            Row { head.forEach { Text(it, Modifier.width(w).background(Slate800).padding(8.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) } }
            body.forEach { cells ->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    cells.forEach { v ->
                        val tone = toneColors(v)
                        Text(v, Modifier.width(w).fillMaxHeight().background(tone?.first ?: Color.White).border(0.5.dp, Slate200).padding(8.dp),
                            fontSize = 12.sp, color = tone?.second ?: Slate900, fontWeight = if (tone != null) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowCard(
    table: TableSchema,
    row: Row,
    index: Int,
    version: Int,
    photos: Map<String, String>,
    readingKey: String?,
    hiddenKeys: Set<String>,
    onChange: (String, String) -> Unit,
    onCamera: (String) -> Unit,
    onUpload: (String) -> Unit,
    onReread: (Col, String) -> Unit,
    onDelete: (() -> Unit)?
) {
    val rowId = row[ROW_ID].orEmpty()
    if (row.isSectionHeader()) {
        val cols = table.columns.take(2)
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp).clip(RoundedCornerShape(12.dp)).background(Slate100).border(1.dp, Slate200, RoundedCornerShape(12.dp)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (row.isFixed()) {
                Text(cols.mapNotNull { row[it.key]?.takeIf { v -> v.isNotBlank() } }.joinToString("   "),
                    fontWeight = FontWeight.Bold, color = Slate900, modifier = Modifier.weight(1f))
            } else {
                OutlinedTextField(
                    value = row[cols.first().key].orEmpty(), onValueChange = { onChange(cols.first().key, it.uppercase()) },
                    label = { Text("Section header") }, singleLine = true, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp), colors = fieldColors,
                    textStyle = MaterialTheme.typography.titleSmall
                )
                onDelete?.let { OMIconButton(Icons.Default.Delete, "Delete section header", it, tint = Slate500) }
            }
        }
        return
    }

    val visible = table.columns.filter { it.key !in hiddenKeys }
    val fixedLabels = visible.filter { it.readOnly }
    val editable = visible.filter { !it.readOnly }
    OMCard(modifier = Modifier.padding(start = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(Slate50).border(1.dp, Slate200, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("$index", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700) }
            val title = fixedLabels.mapNotNull { row[it.key]?.takeIf { v -> v.isNotBlank() } }.joinToString("  ·  ")
                .ifBlank { editable.firstOrNull { it.type == ColType.TEXT }?.let { row[it.key] }.orEmpty() }
            Text(title.ifBlank { "Row $index" }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            onDelete?.let { OMIconButton(Icons.Default.Delete, "Delete row", it, tint = Slate500) }
        }
        Spacer(Modifier.height(12.dp))
        val shortCols = editable.filter { it.type != ColType.LONGTEXT }
        shortCols.chunked(3).forEach { chunk ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                chunk.forEach { col ->
                    val key = ReadingsDoc.cellKey(table.id, rowId, col.key)
                    Box(Modifier.weight(1f)) {
                        ValueCell(
                            col = col, value = row[col.key].orEmpty(), version = version,
                            photoPath = photos[key], reading = readingKey == key,
                            onCamera = { onCamera(col.key) }, onUpload = { onUpload(col.key) }, onReread = { path -> onReread(col, path) }
                        ) { onChange(col.key, it) }
                    }
                }
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        editable.filter { it.type == ColType.LONGTEXT }.forEach { col ->
            Box(Modifier.padding(bottom = 12.dp)) { ValueCell(col, row[col.key].orEmpty(), version) { onChange(col.key, it) } }
        }
    }
}

@Composable
private fun ValueCell(
    col: Col,
    value: String,
    version: Int,
    photoPath: String? = null,
    reading: Boolean = false,
    onCamera: (() -> Unit)? = null,
    onUpload: (() -> Unit)? = null,
    onReread: ((String) -> Unit)? = null,
    onValueChange: (String) -> Unit
) {
    when (col.type) {
        ColType.CALC -> CalcCell(col, value)
        ColType.SELECT -> SelectCell(col, value, onValueChange)
        ColType.CHECK -> CheckCell(col, value == "true") { onValueChange(it.toString()) }
        ColType.IMAGE -> ImageCell(col, photoPath, onCamera, onUpload)
        ColType.DATE -> DateCell(col, value, onValueChange)
        ColType.LONGTEXT -> OutlinedTextField(
            value = value, onValueChange = onValueChange, label = { Text(col.label) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp), minLines = 3,
            shape = RoundedCornerShape(14.dp), colors = fieldColors
        )
        else -> InputCell(col, value, onValueChange, photoPath, reading, onCamera, onUpload, onReread)
    }
}

@Composable
private fun InputCell(
    col: Col,
    value: String,
    onValueChange: (String) -> Unit,
    photoPath: String?,
    reading: Boolean,
    onCamera: (() -> Unit)?,
    onUpload: (() -> Unit)?,
    onReread: ((String) -> Unit)?
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
        trailingIcon = onCamera?.let {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (photoPath != null && onReread != null) {
                        if (reading) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), color = Orange600, strokeWidth = 2.dp)
                        else IconButton(onClick = { onReread(photoPath) }) { Icon(Icons.Default.AutoAwesome, "Re-read ${col.header} with AI", tint = Orange600) }
                    }
                    onUpload?.let { upload -> IconButton(onClick = upload) { Icon(Icons.Default.AddPhotoAlternate, "Upload photo for ${col.header}", tint = Slate500) } }
                    IconButton(onClick = it) { Icon(Icons.Default.CameraAlt, "Photo for ${col.header}", tint = if (photoPath != null) Green700 else Slate500) }
                }
            }
        }
    )
}

@Composable
private fun ImageCell(col: Col, photoPath: String?, onCamera: (() -> Unit)?, onUpload: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (photoPath != null) Color.White else Orange50)
            .border(1.dp, if (photoPath != null) Slate300 else Orange200, RoundedCornerShape(14.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (photoPath != null) {
            AsyncImage(model = File(photoPath), contentDescription = col.header, contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
            Column(Modifier.weight(1f)) {
                Text(col.header, fontSize = 12.sp, color = Slate600)
                Text("Photo attached", fontSize = 12.sp, color = Green700, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(col.header, color = Orange800, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
        }
        onUpload?.let { IconButton(onClick = it) { Icon(Icons.Default.AddPhotoAlternate, "Upload ${col.header}", tint = Orange600) } }
        onCamera?.let { IconButton(onClick = it) { Icon(Icons.Default.CameraAlt, "Capture ${col.header}", tint = Orange600) } }
    }
}

@Composable
private fun CheckCell(col: Col, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
            .background(if (checked) Color(0xFFDCFCE7) else Slate100).border(1.dp, Slate200, RoundedCornerShape(14.dp))
            .clickable { onChange(!checked) }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(col.header, modifier = Modifier.weight(1f), color = Slate800)
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Green700))
    }
}

@Composable
private fun DateCell(col: Col, value: String, onValueChange: (String) -> Unit) {
    val context = LocalContext.current
    fun pick() {
        val cal = Calendar.getInstance()
        runCatching {
            val (y, m, d) = value.split("-").map { it.toInt() }
            cal.set(y, m - 1, d)
        }
        DatePickerDialog(context, { _, y, m, d -> onValueChange(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)) },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(col.label, maxLines = 1) },
        placeholder = { Text("YYYY-MM-DD", color = Slate400) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = fieldColors,
        trailingIcon = { IconButton(onClick = ::pick) { Icon(Icons.Default.CalendarMonth, "Pick date", tint = Orange600) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectCell(col: Col, value: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val tone = toneColors(value)
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true,
            label = { Text(col.label, maxLines = 1) },
            placeholder = { Text("Select", color = Slate400) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = tone?.second ?: Slate900, fontWeight = if (tone != null) FontWeight.Bold else FontWeight.Normal),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Orange600, unfocusedBorderColor = Slate300,
                focusedContainerColor = tone?.first ?: Color.White, unfocusedContainerColor = tone?.first ?: Color.White
            )
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("—", color = Slate500) }, onClick = { onSelect(""); open = false })
            col.options.forEach { opt -> DropdownMenuItem(text = { Text(opt.replace('_', ' ')) }, onClick = { onSelect(opt); open = false }) }
        }
    }
}

@Composable
private fun CalcCell(col: Col, value: String) {
    val colors = toneColors(value)
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
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium.copy(fontFamily = MeterMono), fontWeight = FontWeight.Bold,
            color = colors?.second ?: Slate900, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Reads the photo with Gemini (structured transcription) or, offline, with Qwen. Shows any review warnings. */
private suspend fun readMeter(context: Context, photoPath: String, col: Col, request: ReadingRequest): String {
    val imagePath = com.technavious.om15.camera.CameraCapture.boxFileFor(File(photoPath)).takeIf { it.exists() }?.absolutePath ?: photoPath
    if (GeminiMeterReader.useGemini(context)) {
        try {
            val bitmap = withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(imagePath, it) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
                BitmapFactory.decodeFile(imagePath, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return ""
            val (x, choice) = withContext(Dispatchers.IO) {
                GeminiMeterReader(context, GeminiMeterReader.getApiKey(context)).read(bitmap, request)
                    .also { (x, _) -> File("$imagePath.ai.json").writeText(x.rawJson) }
            }
            if (choice.reviewReasons.isNotEmpty()) {
                Toast.makeText(context, "Check ${col.header}: ${choice.reviewReasons.first()}", Toast.LENGTH_LONG).show()
            }
            return choice.value
        } catch (e: Exception) {
            val why = if (e is GeminiMeterReader.GeminiUnavailableException) e.message else "Online AI error: ${e.message?.take(80)}"
            Toast.makeText(context, "$why — reading offline instead", Toast.LENGTH_LONG).show()
        }
    }
    val qwen = QwenMeterReader.getInstance()
    if (!qwen.isLoaded()) withContext(Dispatchers.IO) { qwen.loadModel(context) }
    if (!qwen.isLoaded()) return ""
    return withContext(Dispatchers.IO) { qwen.readMeterImage(imagePath, col.header, col.unit) }
}
