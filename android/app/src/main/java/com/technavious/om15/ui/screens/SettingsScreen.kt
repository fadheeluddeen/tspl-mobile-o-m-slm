package com.technavious.om15.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.technavious.om15.ai.GeminiMeterReader
import com.technavious.om15.ai.QwenMeterReader
import com.technavious.om15.ui.components.*
import com.technavious.om15.ui.theme.*

private val Amber50 = Color(0xFFFFFBEB)
private val Amber300 = Color(0xFFFCD34D)
private val Amber800 = Color(0xFF92400E)
private val Emerald50 = Color(0xFFECFDF5)
private val Emerald300 = Color(0xFF6EE7B7)
private val Emerald800 = Color(0xFF065F46)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(GeminiMeterReader.getApiKey(context)) }
    var saved by remember { mutableStateOf(true) }
    var showKey by remember { mutableStateOf(false) }
    var useGemini by remember { mutableStateOf(GeminiMeterReader.useGemini(context)) }
    val qwenReady = remember { QwenMeterReader.modelsPresent(context) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    fun setMode(gemini: Boolean) {
        if (gemini == useGemini) return
        useGemini = gemini
        GeminiMeterReader.setUseGemini(context, gemini)
        Toast.makeText(context, if (gemini) "Gemini AI (online)" else "Qwen VL (offline)", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OMTopBar(
                title = "Settings",
                subtitle = "Technavious Field Engine",
                onBack = onBack,
                actions = { if (version.isNotBlank()) OMTag("v$version", container = Slate100, content = Slate700, border = Slate200) }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                OMCard {
                    CardTitle(Icons.Default.AutoAwesome, "AI Reading Mode")
                    Text(
                        "Select the engine used to read industrial meter displays.",
                        style = MaterialTheme.typography.bodySmall, color = Slate600, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ModeOption(
                            icon = Icons.Default.AutoAwesome, title = "Gemini AI (Online)",
                            description = "Cloud recognition with the highest accuracy. Needs internet.",
                            selected = useGemini, modifier = Modifier.weight(1f)
                        ) { setMode(true) }
                        ModeOption(
                            icon = Icons.Default.Memory, title = "Qwen VL (Offline)",
                            description = "Runs on the tablet — works in basements and sites without signal.",
                            selected = !useGemini, modifier = Modifier.weight(1f)
                        ) { setMode(false) }
                    }
                }

                if (!qwenReady) {
                    StatusCard(
                        icon = Icons.Default.Warning, iconTint = Amber800,
                        bg = Amber50, border = Amber300,
                        title = "Offline Model Files Missing",
                        body = "The Qwen VL model files were not found on this tablet. Offline AI reading is unavailable until they are copied to Android/data/com.technavious.om15/files/models/.",
                        textColor = Amber800
                    )
                } else if (!useGemini) {
                    StatusCard(
                        icon = Icons.Default.CheckCircle, iconTint = Emerald800,
                        bg = Emerald50, border = Emerald300,
                        title = "Offline Model Installed & Ready",
                        body = "Qwen2.5-VL 3B is available on this tablet for offline meter reading.",
                        textColor = Emerald800,
                        trailing = { OMTag("Ready", container = Color(0xFFA7F3D0), content = Emerald800, border = Emerald300) }
                    )
                }

                OMCard {
                    CardTitle(Icons.Default.Key, "Gemini API Key")
                    Text(
                        "Used for cloud meter reading when Gemini (Online) mode is active.",
                        style = MaterialTheme.typography.bodySmall, color = Slate600, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )
                    OMTextField(
                        label = "API Key",
                        value = apiKey,
                        onValueChange = { apiKey = it; saved = false },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MeterMono),
                        trailing = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (showKey) "Hide key" else "Show key", tint = Slate500)
                            }
                        },
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation()
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, tint = Slate600, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stored only on this tablet", style = MaterialTheme.typography.bodySmall, color = Slate600, modifier = Modifier.weight(1f))
                        OMPrimaryButton(
                            text = if (saved) "Key Active" else "Save New Key",
                            icon = if (saved) Icons.Default.CheckCircle else Icons.Default.Save,
                            enabled = !saved,
                            height = 48.dp,
                            onClick = {
                                GeminiMeterReader.saveApiKey(context, apiKey.trim())
                                saved = true
                                Toast.makeText(context, "API key saved", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = Orange600, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ModeOption(icon: ImageVector, title: String, description: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .heightIn(min = 120.dp)
            .clip(shape)
            .background(if (selected) Orange50 else Color.White)
            .border(2.dp, if (selected) Orange500 else Slate200, shape)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (selected) Orange100 else Slate100),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = if (selected) Orange700 else Slate700, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .background(if (selected) Orange600 else Color.White)
                    .border(2.dp, if (selected) Orange600 else Slate400, CircleShape),
                contentAlignment = Alignment.Center
            ) { if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodySmall, color = Slate600)
    }
}

@Composable
private fun StatusCard(
    icon: ImageVector, iconTint: Color, bg: Color, border: Color,
    title: String, body: String, textColor: Color,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.7f)).border(1.dp, border, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = textColor)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = textColor, fontSize = 12.sp)
        }
        trailing?.invoke()
    }
}
