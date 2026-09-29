package com.technavious.om15.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.technavious.om15.ai.GeminiMeterReader
import com.technavious.om15.ai.QwenMeterReader
import com.technavious.om15.camera.CameraCapture
import com.technavious.om15.data.model.TestType
import com.technavious.om15.data.repository.TestRepository
import com.technavious.om15.data.schema.Col
import com.technavious.om15.data.schema.ReadingsDoc
import com.technavious.om15.data.schema.schemaFor
import com.technavious.om15.ui.components.OrangeGradient
import com.technavious.om15.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val PanelBg = Color(0xF2020617)

@Composable
fun CameraScreen(
    assignmentId: String,
    fieldKey: String,
    repository: TestRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var isCapturing by remember { mutableStateOf(false) }
    var isReading by remember { mutableStateOf(false) }
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var readingValue by remember { mutableStateOf("") }
    var aiSource by remember { mutableStateOf("") }
    var torchOn by remember { mutableStateOf(false) }
    var gridOn by remember { mutableStateOf(true) }
    var fieldCol by remember { mutableStateOf<Col?>(null) }
    val cell = remember(fieldKey) { ReadingsDoc.splitCellKey(fieldKey) }
    val fieldName = fieldCol?.header ?: cell?.third ?: fieldKey
    val unit = fieldCol?.unit
    val useGemini = remember { GeminiMeterReader.useGemini(context) }
    val cameraCapture = remember { CameraCapture(context) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(assignmentId, fieldKey) {
        val type = repository.getAssignment(assignmentId)?.let { runCatching { TestType.valueOf(it.testType) }.getOrNull() }
        fieldCol = if (type != null && cell != null) schemaFor(type).table(cell.first)?.col(cell.third) else null
    }

    LaunchedEffect(Unit) {
        if (!useGemini) withContext(Dispatchers.IO) { QwenMeterReader.getInstance().loadModel(context) }
    }

    DisposableEffect(Unit) {
        onDispose { cameraCapture.release() }
    }

    suspend fun readWithAi(file: File) {
        if (useGemini) {
            try {
                val bitmap = withContext(Dispatchers.IO) { cameraCapture.loadBitmap(file) }
                val reading = withContext(Dispatchers.IO) {
                    GeminiMeterReader(GeminiMeterReader.getApiKey(context)).readMeterImage(bitmap, fieldName, unit)
                }
                readingValue = reading
                aiSource = if (reading.isBlank()) "No reading detected" else "Gemini AI"
                return
            } catch (e: Exception) {
                Toast.makeText(context, "Gemini error: ${e.message?.take(100)}", Toast.LENGTH_LONG).show()
            }
        }
        val qwen = QwenMeterReader.getInstance()
        if (!qwen.isLoaded()) withContext(Dispatchers.IO) { qwen.loadModel(context) }
        if (qwen.isLoaded()) {
            val reading = withContext(Dispatchers.IO) { qwen.readMeterImage(file.absolutePath, fieldName, unit) }
            readingValue = reading
            aiSource = if (reading.isBlank()) "No reading detected" else "Offline AI"
        } else {
            Toast.makeText(context, "Qwen model could not be loaded.", Toast.LENGTH_LONG).show()
            aiSource = "No model"
        }
    }


    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!hasCameraPermission) {
            Text("Camera permission required", color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else {
            AndroidView(
                factory = { ctx -> PreviewView(ctx).also { cameraCapture.startCamera(it, lifecycleOwner) } },
                modifier = Modifier.fillMaxSize()
            )
            if (gridOn && capturedFile == null) AlignmentReticle(Modifier.align(Alignment.Center))
        }

        // Top bar overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .statusBarsPadding()
                .height(72.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DarkRoundButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Column(Modifier.weight(1f)) {
                Text(
                    "Capture: $fieldName${unit?.let { " ($it)" } ?: ""}",
                    color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1
                )
                Text(
                    if (useGemini) "Gemini AI · Online" else "Qwen VL · Offline",
                    color = Orange500, fontFamily = MeterMono, fontSize = 12.sp
                )
            }
            DarkRoundButton(Icons.Default.FlashlightOn, "Torch", {
                torchOn = !torchOn
                cameraCapture.setTorch(torchOn)
            }, active = torchOn, activeColor = Color(0xFFFBBF24))
            DarkRoundButton(Icons.Default.GridOn, "Alignment guide", { gridOn = !gridOn }, active = gridOn)
        }

        // Bottom: shutter bar or result panel
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            val file = capturedFile
            if (file == null) {
                Box(
                    Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).navigationBarsPadding().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .border(4.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                            .clickable(enabled = !isCapturing && hasCameraPermission) {
                                isCapturing = true
                                scope.launch {
                                    try {
                                        val photo = cameraCapture.capturePhoto()
                                        repository.savePhoto(assignmentId, photo.absolutePath, fieldKey)
                                        capturedFile = photo
                                        readingValue = ""
                                        aiSource = "Photo saved – tap Retry to read"
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isCapturing = false
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(66.dp).clip(CircleShape).background(OrangeGradient), contentAlignment = Alignment.Center) {
                            if (isCapturing) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 3.dp)
                            else Icon(Icons.Default.CameraAlt, "Capture meter photo", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                }
            } else {
                ResultPanel(
                    fieldName = fieldName,
                    unit = unit,
                    source = aiSource,
                    useGemini = useGemini,
                    value = readingValue,
                    onValueChange = { readingValue = it },
                    isReading = isReading,
                    onRetake = { capturedFile = null; readingValue = ""; aiSource = "" },
                    onRetry = {
                        isReading = true
                        aiSource = "Reading..."
                        scope.launch {
                            try { readWithAi(file) } catch (e: Exception) {
                                Toast.makeText(context, "Reading failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                aiSource = "Reading failed"
                            } finally { isReading = false }
                        }
                    },
                    onAccept = {
                        scope.launch {
                            val assignment = repository.getAssignment(assignmentId) ?: return@launch
                            val type = runCatching { TestType.valueOf(assignment.testType) }.getOrNull() ?: return@launch
                            val (tableId, rowId, colKey) = cell ?: return@launch
                            val schema = schemaFor(type)
                            val doc = ReadingsDoc.parse(assignment.readingsJson, schema)
                            val row = doc.findRow(tableId, rowId) ?: run {
                                Toast.makeText(context, "Row no longer exists", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            row[colKey] = readingValue.trim()
                            schema.table(tableId)?.calc?.invoke(row)
                            repository.saveReadingsJson(assignmentId, doc.toJson())
                            Toast.makeText(context, "Saved: $fieldName = ${readingValue.trim()}", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DarkRoundButton(icon: ImageVector, description: String, onClick: () -> Unit, active: Boolean = false, activeColor: Color = Color.White.copy(alpha = 0.3f)) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (active) activeColor else Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = if (active && activeColor != Color.White.copy(alpha = 0.3f)) Slate900 else Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun AlignmentReticle(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth(0.72f)
            .aspectRatio(4f / 3f)
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawRoundRect(
                    color = Orange500.copy(alpha = 0.7f),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 14f)))
                )
                val len = 32.dp.toPx()
                val w = 4.dp.toPx()
                val c = Orange500
                drawLine(c, Offset(0f, 0f), Offset(len, 0f), w); drawLine(c, Offset(0f, 0f), Offset(0f, len), w)
                drawLine(c, Offset(size.width, 0f), Offset(size.width - len, 0f), w); drawLine(c, Offset(size.width, 0f), Offset(size.width, len), w)
                drawLine(c, Offset(0f, size.height), Offset(len, size.height), w); drawLine(c, Offset(0f, size.height), Offset(0f, size.height - len), w)
                drawLine(c, Offset(size.width, size.height), Offset(size.width - len, size.height), w); drawLine(c, Offset(size.width, size.height), Offset(size.width, size.height - len), w)
                val cx = size.width / 2; val cy = size.height / 2; val h = 12.dp.toPx()
                drawLine(c.copy(alpha = 0.8f), Offset(cx - h, cy), Offset(cx + h, cy), stroke)
                drawLine(c.copy(alpha = 0.8f), Offset(cx, cy - h), Offset(cx, cy + h), stroke)
            }
    ) {
        Text(
            "Align LCD meter reading inside box",
            color = Color(0xFFFDBA74), fontFamily = MeterMono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ResultPanel(
    fieldName: String,
    unit: String?,
    source: String,
    useGemini: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    isReading: Boolean,
    onRetake: () -> Unit,
    onRetry: () -> Unit,
    onAccept: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBg, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .border(BorderStroke(1.dp, Orange500.copy(alpha = 0.4f)), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Orange500.copy(alpha = 0.2f))
                    .border(1.dp, Orange500.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(if (useGemini) Icons.Default.AutoAwesome else Icons.Default.Memory, null, tint = Color(0xFFFB923C), modifier = Modifier.size(14.dp))
                Text(source.uppercase(), color = Color(0xFFFDBA74), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("Field: ", color = Slate400, fontSize = 12.sp)
            Text(fieldName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text("DETECTED METER VALUE (TAP TO ADJUST)", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(6.dp))
            if (isReading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Orange500, strokeWidth = 3.dp)
                    Text("Reading meter display...", color = Orange500, fontFamily = MeterMono, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.displayMedium.copy(color = Color.White, fontSize = 44.sp),
                        cursorBrush = SolidColor(Orange500),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (value.isEmpty()) Text("—", style = MaterialTheme.typography.displayMedium.copy(color = Slate600, fontSize = 44.sp))
                            inner()
                        }
                    )
                    unit?.let { Text(it, color = Color(0xFFFB923C), fontFamily = MeterMono, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PanelButton("Retake", Icons.Default.Replay, onRetake, enabled = !isReading, modifier = Modifier.weight(1f),
                bg = Color.White.copy(alpha = 0.1f), border = Color.White.copy(alpha = 0.15f), fg = Color.White)
            PanelButton(if (isReading) "Reading..." else "Retry AI", Icons.Default.Refresh, onRetry, enabled = !isReading, modifier = Modifier.weight(1f),
                bg = Color(0x66431407), border = Orange500.copy(alpha = 0.4f), fg = Color(0xFFFDBA74), busy = isReading)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (!isReading && value.isNotBlank()) OrangeGradient else androidx.compose.ui.graphics.Brush.linearGradient(listOf(Slate700, Slate700)))
                    .clickable(enabled = !isReading && value.isNotBlank(), onClick = onAccept),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text("Accept", color = Color.White, style = MaterialTheme.typography.labelLarge, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun PanelButton(
    text: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean, modifier: Modifier,
    bg: Color, border: Color, fg: Color, busy: Boolean = false
) {
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = fg, strokeWidth = 2.dp)
            else Icon(icon, null, tint = if (enabled) fg else fg.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            Text(text, color = if (enabled) fg else fg.copy(alpha = 0.5f), style = MaterialTheme.typography.labelLarge)
        }
    }
}
