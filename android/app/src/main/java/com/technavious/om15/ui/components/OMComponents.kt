package com.technavious.om15.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.technavious.om15.ui.theme.*

val OrangeGradient = Brush.linearGradient(listOf(Orange500, Orange700))
val ScreenPadding = 24.dp

@Composable
fun OMTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(72.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (onBack != null) {
                OMIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            }
            leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                        color = Slate600, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

@Composable
fun OMIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Slate700,
    enabled: Boolean = true,
    size: Dp = 48.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(Slate50)
            .border(1.dp, Slate200, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = if (enabled) tint else Slate400, modifier = Modifier.size(24.dp))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OMCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    borderColor: Color = Slate200,
    containerColor: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val click = when {
        onClick == null && onLongClick == null -> Modifier
        else -> Modifier.clip(shape).combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    }
    Surface(
        modifier = modifier.fillMaxWidth().then(click),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun OMPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(height)
            .shadow(if (enabled) 6.dp else 0.dp, shape, ambientColor = Orange600, spotColor = Orange600)
            .clip(shape)
            .background(if (enabled) OrangeGradient else Brush.linearGradient(listOf(Slate300, Slate300)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, fontSize = MaterialTheme.typography.titleMedium.fontSize)
        }
    }
}

@Composable
fun OMSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentColor: Color = Slate800,
    height: Dp = 56.dp
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, Slate300),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = contentColor)
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun OMTag(text: String, container: Color = Orange100, content: Color = Orange800, border: Color = Orange200) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun StatusChip(status: String) {
    val completed = status.equals("COMPLETED", true) || status.equals("APPROVED", true) || status.equals("SUBMITTED", true)
    val bg = if (completed) Color(0xFFD1FAE5) else Color(0xFFFEF3C7)
    val fg = if (completed) Color(0xFF065F46) else Color(0xFF92400E)
    val br = if (completed) Color(0xFF6EE7B7) else Color(0xFFFCD34D)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, br, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(if (completed) Icons.Default.CheckCircle else Icons.Default.Schedule, null, tint = fg, modifier = Modifier.size(14.dp))
        Text(status.lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
fun IconTile(icon: ImageVector, size: Dp = 44.dp, gradient: Boolean = true) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(if (gradient) OrangeGradient else Brush.linearGradient(listOf(Orange100, Orange100))),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (gradient) Color.White else Orange600, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, buttonText: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 380.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .drawBehind {
                drawRoundRect(
                    color = Orange200,
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)))
                )
            }
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(Orange100).border(1.dp, Orange200, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Orange600, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Slate600, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 380.dp))
        Spacer(Modifier.height(24.dp))
        OMPrimaryButton(buttonText, onClick, height = 48.dp, icon = Icons.Default.Add)
    }
}

@Composable
fun ConfirmDeleteDialog(title: String, itemName: String, detail: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Red100),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Warning, null, tint = Red600, modifier = Modifier.size(28.dp)) }
        },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Text("Are you sure you want to delete “$itemName”? $detail", style = MaterialTheme.typography.bodyMedium, color = Slate600)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Red600)
            ) { Text("Delete", style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate200)
            ) { Text("Cancel", style = MaterialTheme.typography.labelLarge, color = Slate700) }
        }
    )
}

@Composable
fun OMTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.titleSmall, color = Slate800)
            if (required) Text(" *", style = MaterialTheme.typography.titleSmall, color = Red600)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            placeholder = placeholder?.let { { Text(it, color = Slate400) } },
            leadingIcon = leadingIcon?.let { { Icon(it, null, tint = Slate500) } },
            trailingIcon = trailing,
            isError = error != null,
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            textStyle = textStyle,
            visualTransformation = visualTransformation,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Orange600,
                unfocusedBorderColor = Slate300,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                cursorColor = Orange600
            )
        )
        when {
            error != null -> Text(error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Red600)
            helper != null -> Text(helper, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = Slate600)
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Slate600)
        }
        trailing?.invoke()
    }
}
