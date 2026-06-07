package com.animationstudio.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animationstudio.MainViewModel
import com.animationstudio.ai.EasingType
import com.animationstudio.engine.AnimationEngine
import com.animationstudio.ui.theme.*

/**
 * Advanced timeline editor with layer-based animation tracks,
 * keyframe manipulation, and AI-assisted features.
 */
@Composable
fun TimelineScreen(
    viewModel: MainViewModel,
    animationEngine: AnimationEngine,
    windowSizeClass: WindowSizeClass
) {
    val layers by viewModel.layers.collectAsState()
    val currentFrame by viewModel.currentFrame.collectAsState()
    val totalFrames by viewModel.totalFrames.collectAsState()
    val fps by viewModel.fps.collectAsState()
    val currentLayerIndex by viewModel.currentLayerIndex.collectAsState()
    val onionSkinEnabled by viewModel.onionSkinEnabled.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Timeline header
        TimelineHeader(viewModel)

        // Timeline content
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Layer labels
            Column(
                modifier = Modifier.width(180.dp).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            ) {
                // Header
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Layers", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = { viewModel.addLayer() }) {
                            Text("+", fontSize = 16.sp)
                        }
                    }
                }

                LazyColumn {
                    items(layers.size) { index ->
                        val layer = layers[index]
                        TimelineLayerLabel(
                            layerName = layer.name,
                            isVisible = layer.visible,
                            isSelected = index == currentLayerIndex,
                            isLocked = layer.locked,
                            onSelect = { viewModel.selectLayer(index) },
                            onToggleVisibility = { viewModel.toggleLayerVisibility(index) }
                        )
                    }
                }
            }

            // Tracks area
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .horizontalScroll(scrollState)
            ) {
                // Frame ruler
                FrameRuler(
                    totalFrames = totalFrames,
                    currentFrame = currentFrame,
                    fps = fps,
                    modifier = Modifier.fillMaxWidth().height(28.dp)
                )

                // Layer tracks
                Column(modifier = Modifier.fillMaxSize()) {
                    layers.forEachIndexed { index, layer ->
                        TimelineTrack(
                            layerIndex = index,
                            totalFrames = totalFrames,
                            currentFrame = currentFrame,
                            isSelected = index == currentLayerIndex,
                            onFrameClick = { frame -> viewModel.setCurrentFrame(frame) },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        )
                    }
                }

                // Playhead
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        // Bottom toolbar
        TimelineBottomToolbar(viewModel)
    }
}

@Composable
fun TimelineHeader(viewModel: MainViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⏱️ Timeline", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = viewModel.onionSkinEnabled.value,
                    onClick = { viewModel.toggleOnionSkin() },
                    label = { Text("👻 Onion Skin") }
                )
                FilterChip(
                    selected = viewModel.aiTweeningEnabled.value,
                    onClick = { viewModel.toggleAITweening() },
                    label = { Text("🤖 AI Tween") }
                )
            }
        }
    }
}

@Composable
fun TimelineLayerLabel(
    layerName: String,
    isVisible: Boolean,
    isSelected: Boolean,
    isLocked: Boolean,
    onSelect: () -> Unit,
    onToggleVisibility: () -> Unit
) {
    Surface(
        onClick = onSelect,
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                if (isVisible) "👁️" else "🚫",
                fontSize = 12.sp,
                modifier = Modifier.clickable { onToggleVisibility() }
            )
            Text(
                if (isLocked) "🔒" else "📄",
                fontSize = 11.sp
            )
            Text(
                layerName,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun FrameRuler(
    totalFrames: Int,
    currentFrame: Int,
    fps: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        val frameWidth = 40f
        for (i in 0 until totalFrames) {
            val x = i * frameWidth
            val isSecond = i % fps == 0

            // Tick mark
            drawLine(
                color = Color.Gray.copy(alpha = if (isSecond) 1f else 0.5f),
                start = Offset(x + frameWidth / 2, if (isSecond) 0f else 8f),
                end = Offset(x + frameWidth / 2, size.height),
                strokeWidth = if (isSecond) 2f else 1f
            )

            // Highlight current frame
            if (i == currentFrame) {
                drawRect(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    topLeft = Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(frameWidth, size.height)
                )
            }
        }
    }
}

@Composable
fun TimelineTrack(
    layerIndex: Int,
    totalFrames: Int,
    currentFrame: Int,
    isSelected: Boolean,
    onFrameClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val frameWidth = 40.dp

    Row(modifier = modifier) {
        for (i in 0 until totalFrames) {
            Box(
                modifier = Modifier
                    .width(frameWidth)
                    .fillMaxHeight()
                    .background(
                        if (i == currentFrame && isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else if (i % 6 == 0)
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        else
                            Color.Transparent
                    )
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    .clickable { onFrameClick(i) },
                contentAlignment = Alignment.Center
            ) {
                // Keyframe indicator
                if (i % 6 == 0) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(2.dp)
                            )
                    )
                }

                // AI-generated frame indicator
                if (i % 6 != 0 && i > 0) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun TimelineBottomToolbar(viewModel: MainViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { viewModel.addFrame() }) {
                Text("➕ Add Frame")
            }
            TextButton(onClick = { viewModel.removeFrame() }) {
                Text("🗑️ Remove")
            }
            TextButton(onClick = { /* Duplicate current frame */ }) {
                Text("📋 Duplicate")
            }
            TextButton(onClick = { /* Insert blank */ }) {
                Text("📄 Insert Blank")
            }
            TextButton(onClick = { /* Reverse frames */ }) {
                Text("🔄 Reverse")
            }
        }
    }
}
