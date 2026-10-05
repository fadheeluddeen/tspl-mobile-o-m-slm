package com.technavious.om15.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.technavious.om15.export.ReportFile
import com.technavious.om15.export.ReportFiles
import com.technavious.om15.ui.components.*
import com.technavious.om15.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit, onView: (Uri, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<ReportFile>?>(null) }
    var menuFor by remember { mutableStateOf<ReportFile?>(null) }
    var toDelete by remember { mutableStateOf<ReportFile?>(null) }
    val fmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    suspend fun reload() { files = withContext(Dispatchers.IO) { ReportFiles.list(context) } }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { OMTopBar(title = "Downloaded Reports", subtitle = "Excel files saved in Download · long-press for options", onBack = onBack) }
    ) { padding ->
        val list = files
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Orange600) }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.TableChart,
                    title = "No reports yet",
                    message = "Open a test and tap Download Excel. Downloaded reports appear here.",
                    buttonText = "Back",
                    onClick = onBack
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(list, key = { it.uri.toString() }) { f ->
                    OMCard(onClick = { onView(f.uri, f.name) }, onLongClick = { menuFor = f }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            IconTile(Icons.Default.TableChart, size = 44.dp)
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${fmt.format(Date(f.modifiedMillis))} · ${"%.1f".format(f.sizeBytes / 1024.0)} KB",
                                    style = MaterialTheme.typography.bodySmall, color = Slate600
                                )
                            }
                            OMIconButton(Icons.Default.Visibility, "View", { onView(f.uri, f.name) }, tint = Orange600)
                            OMIconButton(Icons.Default.Share, "Share", { ReportFiles.share(context, f.uri, f.name) })
                            OMIconButton(Icons.AutoMirrored.Filled.OpenInNew, "Open with", { ReportFiles.openWith(context, f.uri, f.name) })
                        }
                    }
                }
            }
        }
    }

    menuFor?.let { f ->
        ModalBottomSheet(onDismissRequest = { menuFor = null }, containerColor = Color.White) {
            Column(Modifier.padding(horizontal = ScreenPadding).padding(bottom = 32.dp)) {
                Text(f.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${fmt.format(Date(f.modifiedMillis))} · ${"%.1f".format(f.sizeBytes / 1024.0)} KB",
                    style = MaterialTheme.typography.bodySmall, color = Slate600)
                Spacer(Modifier.height(12.dp))
                SheetAction(Icons.Default.Visibility, "View", Orange600) { menuFor = null; onView(f.uri, f.name) }
                SheetAction(Icons.Default.Share, "Share", Slate700) { menuFor = null; ReportFiles.share(context, f.uri, f.name) }
                SheetAction(Icons.AutoMirrored.Filled.OpenInNew, "Open with another app", Slate700) { menuFor = null; ReportFiles.openWith(context, f.uri, f.name) }
                HorizontalDivider(color = Slate200, modifier = Modifier.padding(vertical = 4.dp))
                SheetAction(Icons.Default.Delete, "Delete", Red600) { menuFor = null; toDelete = f }
            }
        }
    }

    toDelete?.let { f ->
        ConfirmDeleteDialog(
            title = "Delete Report?",
            itemName = f.name,
            detail = "The Excel file will be removed from the Download folder.",
            onConfirm = {
                toDelete = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { ReportFiles.delete(context, f) }
                    Toast.makeText(context, if (ok) "Report deleted" else "Could not delete this file", Toast.LENGTH_SHORT).show()
                    reload()
                }
            },
            onDismiss = { toDelete = null }
        )
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, tint = tint)
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if (tint == Red600) Red600 else Slate900)
    }
}
