package com.animationstudio.ui.screens

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animationstudio.MainViewModel
import com.animationstudio.ui.theme.*
import com.animationstudio.util.ExportUtils
import kotlinx.coroutines.launch

@Composable
fun ExportScreen(
    viewModel: MainViewModel,
    windowSizeClass: WindowSizeClass
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var exportFormat by remember { mutableStateOf(ExportUtils.ExportFormat.GIF) }
    var exportQuality by remember { mutableIntStateOf(90) }
    var exportFps by remember { mutableIntStateOf(viewModel.fps.value) }
    var exportWidth by remember { mutableIntStateOf(1920) }
    var exportHeight by remember { mutableIntStateOf(1080) }
    var isExporting by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var lastExportUri by remember { mutableStateOf<String?>(null) }

    val totalFrames = viewModel.totalFrames.value
    val currentFps = viewModel.fps.value

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "📤 Export Animation",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "Export your animation in various formats optimized for sharing, web, or video editing",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        // Format selection
        Card(
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Export Format",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FormatCard("GIF", "🎞️", "Animated GIF\nBest for web",
                        exportFormat == ExportUtils.ExportFormat.GIF,
                        { exportFormat = ExportUtils.ExportFormat.GIF },
                        Modifier.weight(1f))
                    FormatCard("MP4", "🎥", "Video file\nHigh quality",
                        exportFormat == ExportUtils.ExportFormat.MP4,
                        { exportFormat = ExportUtils.ExportFormat.MP4 },
                        Modifier.weight(1f))
                    FormatCard("PNG", "🖼️", "Image sequence\nFrame by frame",
                        exportFormat == ExportUtils.ExportFormat.PNG_SEQUENCE,
                        { exportFormat = ExportUtils.ExportFormat.PNG_SEQUENCE },
                        Modifier.weight(1f))
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FormatCard("WEBM", "🌐", "Web video\nModern codec",
                        exportFormat == ExportUtils.ExportFormat.WEBM,
                        { exportFormat = ExportUtils.ExportFormat.WEBM },
                        Modifier.weight(1f))
                    FormatCard("LOTTIE", "✨", "Vector anim\nScalable",
                        exportFormat == ExportUtils.ExportFormat.LOTTIE,
                        { exportFormat = ExportUtils.ExportFormat.LOTTIE },
                        Modifier.weight(1f))
                    FormatCard("Project", "💾", ".asproj\nBackup file",
                        exportFormat == ExportUtils.ExportFormat.PROJECT_FILE,
                        { exportFormat = ExportUtils.ExportFormat.PROJECT_FILE },
                        Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Settings
        Card(
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Export Settings",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)

                Spacer(Modifier.height(16.dp))

                // Quality
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Quality", style = MaterialTheme.typography.bodyMedium)
                    Text("${exportQuality}%",
                        style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = exportQuality.toFloat(),
                    onValueChange = { exportQuality = it.toInt() },
                    valueRange = 10f..100f,
                    steps = 8
                )

                // FPS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Frame Rate", style = MaterialTheme.typography.bodyMedium)
                    Text("$exportFps FPS",
                        style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = exportFps.toFloat(),
                    onValueChange = { exportFps = it.toInt() },
                    valueRange = 1f..60f,
                    steps = 10
                )

                // Resolution
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Resolution", style = MaterialTheme.typography.bodyMedium)
                    Text("$exportWidth × $exportHeight",
                        style = MaterialTheme.typography.labelMedium)
                }

                Spacer(Modifier.height(8.dp))

                // Resolution presets
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResolutionPreset("HD", 1280, 720, exportWidth, exportHeight) { w, h ->
                        exportWidth = w; exportHeight = h
                    }
                    ResolutionPreset("Full HD", 1920, 1080, exportWidth, exportHeight) { w, h ->
                        exportWidth = w; exportHeight = h
                    }
                    ResolutionPreset("4K", 3840, 2160, exportWidth, exportHeight) { w, h ->
                        exportWidth = w; exportHeight = h
                    }
                    ResolutionPreset("Custom", exportWidth, exportHeight, exportWidth, exportHeight) { _, _ -> }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Export info
        Card(
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Animation Summary",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold)
                    Text("$totalFrames frames @ ${exportFps}fps",
                        style = MaterialTheme.typography.bodySmall)
                    Text("${exportWidth}×${exportHeight} pixels",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Duration: ${"%.1f".format(totalFrames.toFloat() / exportFps)}s",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Format: ${exportFormat.name}",
                        style = MaterialTheme.typography.bodySmall)
                }

                // Estimated file size
                Column(horizontalAlignment = Alignment.End) {
                    Text("Est. Size",
                        style = MaterialTheme.typography.labelSmall)
                    Text(
                        when (exportFormat) {
                            ExportUtils.ExportFormat.GIF -> "~${totalFrames * 0.5}MB"
                            ExportUtils.ExportFormat.MP4 -> "~${totalFrames * 0.2}MB"
                            ExportUtils.ExportFormat.PNG_SEQUENCE -> "~${totalFrames * 1.5}MB"
                            else -> "Variable"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Export button
        Button(
            onClick = {
                scope.launch {
                    isExporting = true
                    try {
                        val uri = ExportUtils.exportAnimation(
                            context,
                            emptyList(), // Pass actual frames
                            ExportUtils.ExportConfig(
                                format = exportFormat,
                                width = exportWidth,
                                height = exportHeight,
                                fps = exportFps,
                                quality = exportQuality
                            )
                        )
                        lastExportUri = uri?.toString()
                        showSuccess = true
                    } finally {
                        isExporting = false
                    }
                }
            },
            enabled = !isExporting,
            modifier = Modifier.height(60.dp).widthIn(min = 280.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(12.dp))
                Text("Exporting...", fontSize = 18.sp)
            } else {
                Text("📤 Export Animation", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (showSuccess && lastExportUri != null) {
            Spacer(Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "✅ Export complete! File saved to Downloads",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun FormatCard(
    name: String,
    icon: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = if (selected)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 28.sp)
            Text(name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold)
            Text(description,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ResolutionPreset(
    name: String,
    width: Int,
    height: Int,
    currentWidth: Int,
    currentHeight: Int,
    onSelect: (Int, Int) -> Unit
) {
    FilterChip(
        selected = currentWidth == width && currentHeight == height,
        onClick = { onSelect(width, height) },
        label = { Text(name, fontSize = 11.sp) }
    )
}