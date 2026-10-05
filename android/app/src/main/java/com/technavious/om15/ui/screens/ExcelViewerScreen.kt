package com.technavious.om15.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.technavious.om15.export.ReportFiles
import com.technavious.om15.ui.components.OMIconButton
import com.technavious.om15.ui.components.OMTopBar
import com.technavious.om15.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFPicture
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook

private class ViewCell(
    val text: String,
    val bold: Boolean,
    val italic: Boolean,
    val fill: Color?,
    val fontColor: Color?,
    val align: TextAlign,
    val colSpan: Int = 1,
    val covered: Boolean = false
)

private class ViewSheet(
    val name: String,
    val columnWidths: List<Dp>,
    val rows: List<Pair<Dp, List<ViewCell>>>,
    val pictures: Map<Pair<Int, Int>, ImageBitmap>
)

private fun XSSFColor?.toCompose(): Color? {
    val hex = this?.argbHex ?: return null
    return runCatching { Color(hex.toLong(16).toInt()) }.getOrNull()?.let { if (it.alpha == 0f) it.copy(alpha = 1f) else it }
}

private fun loadWorkbook(context: Context, uri: Uri): List<ViewSheet> {
    val formatter = DataFormatter()
    return context.contentResolver.openInputStream(uri)!!.use { input ->
        XSSFWorkbook(input).use { wb ->
            (0 until wb.numberOfSheets).map { si ->
                val sh = wb.getSheetAt(si) as XSSFSheet
                val lastRow = sh.lastRowNum
                val lastCol = (0..lastRow).maxOfOrNull { sh.getRow(it)?.lastCellNum?.toInt() ?: 0 }?.coerceAtLeast(1) ?: 1
                val widths = (0 until lastCol).map { (sh.getColumnWidthInPixels(it) * 0.9f).coerceIn(40f, 360f).dp }

                val spans = HashMap<Pair<Int, Int>, Int>()
                val covered = HashSet<Pair<Int, Int>>()
                sh.mergedRegions.forEach { m ->
                    spans[m.firstRow to m.firstColumn] = m.lastColumn - m.firstColumn + 1
                    for (r in m.firstRow..m.lastRow) for (c in m.firstColumn..m.lastColumn) {
                        if (r != m.firstRow || c != m.firstColumn) {
                            if (r == m.firstRow) covered += r to c
                        }
                    }
                }

                val pictures = HashMap<Pair<Int, Int>, ImageBitmap>()
                sh.drawingPatriarch?.shapes?.filterIsInstance<XSSFPicture>()?.forEach { pic ->
                    val a = pic.clientAnchor
                    val bytes = pic.pictureData.data
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.let { pictures[a.row1 to a.col1.toInt()] = it.asImageBitmap() }
                }

                val rows = (0..lastRow).map { ri ->
                    val row = sh.getRow(ri)
                    val height = ((row?.heightInPoints ?: sh.defaultRowHeightInPoints) * 1.25f).coerceIn(28f, 160f).dp
                    val cells = (0 until lastCol).map { ci ->
                        val cell = row?.getCell(ci)
                        val style = cell?.cellStyle as? XSSFCellStyle
                        val font = style?.font
                        ViewCell(
                            text = cell?.let { formatter.formatCellValue(it) }.orEmpty(),
                            bold = font?.bold == true,
                            italic = font?.italic == true,
                            fill = style?.takeIf { it.fillPattern != org.apache.poi.ss.usermodel.FillPatternType.NO_FILL }?.fillForegroundColorColor.let { it as? XSSFColor }.toCompose(),
                            fontColor = font?.xssfColor.toCompose(),
                            align = when (style?.alignment) {
                                HorizontalAlignment.LEFT -> TextAlign.Start
                                HorizontalAlignment.RIGHT -> TextAlign.End
                                else -> TextAlign.Center
                            },
                            colSpan = spans[ri to ci] ?: 1,
                            covered = (ri to ci) in covered
                        )
                    }
                    height to cells
                }
                ViewSheet(sh.sheetName, widths, rows, pictures)
            }
        }
    }
}

@Composable
fun ExcelViewerScreen(uri: Uri, fileName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var sheets by remember { mutableStateOf<List<ViewSheet>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableIntStateOf(0) }

    LaunchedEffect(uri) {
        try {
            sheets = withContext(Dispatchers.IO) { loadWorkbook(context, uri) }
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OMTopBar(
                title = fileName,
                subtitle = sheets?.let { "${it.size} ${if (it.size == 1) "sheet" else "sheets"}" } ?: "Opening...",
                onBack = onBack,
                actions = {
                    OMIconButton(Icons.AutoMirrored.Filled.OpenInNew, "Open with another app", { ReportFiles.openWith(context, uri, fileName) })
                    OMIconButton(Icons.Default.Share, "Share", { ReportFiles.share(context, uri, fileName) }, tint = Orange600)
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val list = sheets
            when {
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Could not open this file: $error", color = Red600, modifier = Modifier.padding(24.dp))
                }
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Orange600) }
                else -> {
                    if (list.size > 1) {
                        ScrollableTabRow(
                            selectedTabIndex = selected,
                            containerColor = Color.White,
                            contentColor = Orange700,
                            edgePadding = 16.dp
                        ) {
                            list.forEachIndexed { i, s ->
                                Tab(selected = i == selected, onClick = { selected = i },
                                    text = { Text(s.name, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal) })
                            }
                        }
                    }
                    SheetGrid(list[selected.coerceIn(0, list.lastIndex)])
                }
            }
        }
    }
}

@Composable
private fun SheetGrid(sheet: ViewSheet) {
    val hScroll = rememberScrollState()
    LazyColumn(Modifier.fillMaxSize().background(Color.White).horizontalScroll(hScroll)) {
        itemsIndexed(sheet.rows) { ri, (height, cells) ->
            Row(Modifier.height(height)) {
                var ci = 0
                while (ci < cells.size) {
                    val cell = cells[ci]
                    if (cell.covered) { ci++; continue }
                    val width = (ci until (ci + cell.colSpan).coerceAtMost(cells.size)).fold(0.dp) { acc, c -> acc + sheet.columnWidths[c] }
                    val picture = sheet.pictures[ri to ci]
                    Box(
                        modifier = Modifier
                            .width(width)
                            .fillMaxHeight()
                            .background(cell.fill ?: Color.White)
                            .border(0.5.dp, Slate200)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = when (cell.align) {
                            TextAlign.Start -> Alignment.CenterStart
                            TextAlign.End -> Alignment.CenterEnd
                            else -> Alignment.Center
                        }
                    ) {
                        if (picture != null) {
                            Image(picture, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        } else {
                            Text(
                                cell.text,
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                                fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (cell.italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
                                color = cell.fontColor ?: Slate900,
                                textAlign = cell.align,
                                maxLines = 4
                            )
                        }
                    }
                    ci += cell.colSpan
                }
            }
        }
    }
}
