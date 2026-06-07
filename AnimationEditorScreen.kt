package com.animationstudio.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animationstudio.MainViewModel
import com.animationstudio.ai.AIModelManager
import com.animationstudio.ai.AITweeningEngine
import com.animationstudio.ai.EasingType
import com.animationstudio.ai.MotionSmoother
import com.animationstudio.engine.*
import com.animationstudio.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AnimationEditorScreen(
    viewModel: MainViewModel,
    animationEngine: AnimationEngine,
    aiModelManager: AIModelManager,
    windowSizeClass: WindowSizeClass
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentFrame by viewModel.currentFrame.collectAsState()
    val totalFrames by viewModel.totalFrames.collectAsState()
    val fps by viewModel.fps.collectAsState()
    val onionSkinEnabled by viewModel.onionSkinEnabled.collectAsState()
    val onionSkinFrames by viewModel.onionSkinFrames.collectAsState()
    val aiTweeningEnabled by viewModel.aiTweeningEnabled.collectAsState()
    val motionSmoothingStrength by viewModel.motionSmoothingStrength.collectAsState()
    val layers by viewModel.layers.collectAsState()

    val scope = rememberCoroutineScope()
    val tweeningEngine = remember { AITweeningEngine(aiModelManager) }
    val motionSmoother = remember { MotionSmoother() }

    // AI operation state
    var aiOperationInProgress by remember { mutableStateOf(false) }
    var selectedEasingType by remember { mutableStateOf(EasingType.SMOOTH) }
    var numInBetweens by remember { mutableStateOf(3) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with AI controls
            AIFeatureBar(
                aiTweeningEnabled = aiTweeningEnabled,
                onToggleAI = { viewModel.toggleAITweening() },
                motionSmoothingStrength = motionSmoothingStrength,
                onSmoothingChange = { viewModel.setMotionSmoothingStrength(it) },
                numInBetweens = numInBetweens,
                onInBetweensChange = { numInBetweens = it },
                selectedEasing = selectedEasingType,
                onEasingChange = { selectedEasingType = it }
            )

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Main preview area
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    // Animation preview
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Preview canvas
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw current frame
                            val layer = layers.getOrNull(viewModel.currentLayerIndex.value)
                            val frameData = layer?.frames?.get(currentFrame)
                            if (frameData != null) {
                                for (stroke in frameData.strokePaths) {
                                    drawStrokePreview(stroke)
                                }
                            }

                            // Onion skin
                            if (onionSkinEnabled) {
                                val onionFrames = animationEngine.getOnionSkinFrames(
                                    currentFrame, totalFrames, onionSkinFrames
                                )
                                for (onion in onionFrames) {
                                    val onionData = layer?.frames?.get(onion.frameIndex)
                                    if (onionData != null) {
                                        for (stroke in onionData.strokePaths) {
                                            drawStrokePreview(stroke, onion.opacity * 0.25f)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Playback controls
                    PlaybackControls(
                        isPlaying = isPlaying,
                        onPlayPause = { viewModel.togglePlayback() },
                        currentFrame = currentFrame,
                        totalFrames = totalFrames,
                        fps = fps,
                        onFpsChange = { viewModel.setFps(it) },
                        onFrameChange = { viewModel.setCurrentFrame(it) },
                        onFirstFrame = { viewModel.setCurrentFrame(0) },
                        onLastFrame = { viewModel.setCurrentFrame(totalFrames - 1) }
                    )
                }

                // AI tools panel
                AIToolsPanel(
                    windowSizeClass = windowSizeClass,
                    tweeningEnabled = aiTweeningEnabled,
                    operationInProgress = aiOperationInProgress,
                    numInBetweens = numInBetweens,
                    easingType = selectedEasingType,
                    onGenerateInBetweens = {
                        scope.launch {
                            aiOperationInProgress = true
                            try {
                                // Get current frame keyframes
                                val layer = layers.getOrNull(viewModel.currentLayerIndex.value)
                                val frameA = layer?.frames?.get(currentFrame)
                                val frameB = layer?.frames?.get(currentFrame + numInBetweens + 1)

                                if (frameA != null && frameB != null) {
                                    val inBetweens = tweeningEngine.generateInBetweens(
                                        frameA, frameB, numInBetweens, selectedEasingType
                                    )
                                    // Insert in-between frames
                                    inBetweens.forEachIndexed { index, frame ->
                                        viewModel.setCurrentFrame(currentFrame + index + 1)
                                        // Add frame to layer
                                    }
                                }
                            } finally {
                                aiOperationInProgress = false
                            }
                        }
                    },
                    onSmoothMotion = {
                        // Apply motion smoothing
                    },
                    onAutoRig = {
                        // Launch auto-rigging
                    },
                    onGenerateKeyframes = {
                        // Generate keyframes from drawing
                    }
                )
            }
        }
    }
}

private fun DrawScope.drawStrokePreview(stroke: Strokerath, alpha: Float = 1f) {
    if (stroke.path.size < 2) return

    val path = Path()
    path.moveTo(stroke.path[0].x, stroke.path[0].y)
    for (i in 1 until stroke.path.size) {
        path.lineTo(stroke.path[i].x, stroke.path[i].y)
    }

    drawPath(
        path = path.asComposePath(),
        color = Color(stroke.color).copy(alpha = stroke.opacity * alpha),
        style = Stroke(
            width = stroke.strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

@Composable
fun AIFeatureBar(
    aiTweeningEnabled: Boolean,
    onToggleAI: () -> Unit,
    motionSmoothingStrength: Float,
    onSmoothingChange: (Float) -> Unit,
    numInBetweens: Int,
    onInBetweensChange: (Int) -> Unit,
    selectedEasing: EasingType,
    onEasingChange: (EasingType) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎬 Animation Editor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)

                // AI toggle
                FilterChip(
                    selected = aiTweeningEnabled,
                    onClick = onToggleAI,
                    label = { Text("🤖 AI Tweening") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Smoothing:", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = motionSmoothingStrength,
                    onValueChange = onSmoothingChange,
                    modifier = Modifier.weight(1f, fill = false).widthIn(min = 80.dp, max = 150.dp)
                )
                Text("${(motionSmoothingStrength * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall)

                Spacer(Modifier.width(8.dp))

                Text("In-betweens:", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (n in listOf(1, 2, 3, 5)) {
                        FilterChip(
                            selected = numInBetweens == n,
                            onClick = { onInBetweensChange(n) },
                            label = { Text("$n") },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Text("Easing:", style = MaterialTheme.typography.labelSmall)
                EasingDropdown(selectedEasing, onEasingChange)
            }
        }
    }
}

@Composable
fun EasingDropdown(selected: EasingType, onSelect: (EasingType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.height(32.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when (selected) {
                        EasingType.LINEAR -> "Linear"
                        EasingType.SMOOTH -> "Smooth"
                        EasingType.EASE_IN -> "Ease In"
                        EasingType.EASE_OUT -> "Ease Out"
                        EasingType.EASE_IN_OUT -> "Ease In/Out"
                        EasingType.BOUNCE -> "Bounce"
                        EasingType.ELASTIC -> "Elastic"
                    },
                    fontSize = 11.sp
                )
                Text("▾", fontSize = 10.sp)
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EasingType.values().forEach { easing ->
                DropdownMenuItem(
                    text = {
                        Text(when (easing) {
                            EasingType.LINEAR -> "📏 Linear"
                            EasingType.SMOOTH -> "🔄 Smooth"
                            EasingType.EASE_IN -> "🐌 Ease In"
                            EasingType.EASE_OUT -> "🚀 Ease Out"
                            EasingType.EASE_IN_OUT -> "⚡ Ease In/Out"
                            EasingType.BOUNCE -> "🏀 Bounce"
                            EasingType.ELASTIC -> "🪀 Elastic"
                        })
                    },
                    onClick = {
                        onSelect(easing)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    currentFrame: Int,
    totalFrames: Int,
    fps: Int,
    onFpsChange: (Int) -> Unit,
    onFrameChange: (Int) -> Unit,
    onFirstFrame: () -> Unit,
    onLastFrame: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onFirstFrame) { Text("⏮️", fontSize = 16.sp) }
                IconButton(onClick = { onFrameChange(currentFrame - 1) }) {
                    Text("⏪", fontSize = 16.sp)
                }

                // Play/Pause button
                Surface(
                    onClick = onPlayPause,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (isPlaying) "⏸️" else "▶️",
                            fontSize = 20.sp
                        )
                    }
                }

                IconButton(onClick = { onFrameChange(currentFrame + 1) }) {
                    Text("⏩", fontSize = 16.sp)
                }
                IconButton(onClick = onLastFrame) { Text("⏭️", fontSize = 16.sp) }

                Spacer(Modifier.width(16.dp))

                // FPS selector
                Text("FPS:", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (f in listOf(8, 12, 24, 30, 60)) {
                        FilterChip(
                            selected = fps == f,
                            onClick = { onFpsChange(f) },
                            label = { Text("$f", fontSize = 10.sp) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    "Frame ${currentFrame + 1}/$totalFrames",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            // Frame scrubber
            Slider(
                value = currentFrame.toFloat(),
                onValueChange = { onFrameChange(it.toInt()) },
                valueRange = 0f..(totalFrames - 1).toFloat(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun AIToolsPanel(
    windowSizeClass: WindowSizeClass,
    tweeningEnabled: Boolean,
    operationInProgress: Boolean,
    numInBetweens: Int,
    easingType: EasingType,
    onGenerateInBetweens: () -> Unit,
    onSmoothMotion: () -> Unit,
    onAutoRig: () -> Unit,
    onGenerateKeyframes: () -> Unit
) {
    val panelWidth = if (windowSizeClass.isLargeScreen) 280.dp else 220.dp

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.width(panelWidth).fillMaxHeight()
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🤖 AI Tools",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)

            Divider()

            // Auto Tweening
            AIToolCard(
                title = "Auto In-Betweening",
                description = "Generate $numInBetweens frames between keyframes using ${easingType.name.lowercase()} easing",
                icon = "🔄",
                buttonText = "Generate In-Betweens",
                enabled = tweeningEnabled && !operationInProgress,
                loading = operationInProgress,
                onClick = onGenerateInBetweens
            )

            // Motion Smoothing
            AIToolCard(
                title = "Motion Smoothing",
                description = "Intelligently smooth stroke trajectories across all frames",
                icon = "✨",
                buttonText = "Apply Smoothing",
                enabled = !operationInProgress,
                loading = false,
                onClick = onSmoothMotion
            )

            // Auto Rigging
            AIToolCard(
                title = "AI Character Rigging",
                description = "Automatically detect body parts and create bone structure",
                icon = "🦴",
                buttonText = "Auto-Rig Character",
                enabled = !operationInProgress,
                loading = false,
                onClick = onAutoRig
            )

            // Keyframe Generation
            AIToolCard(
                title = "Keyframe Generation",
                description = "Create keyframes from imported drawings or detects key poses",
                icon = "🔑",
                buttonText = "Gen Keyframes",
                enabled = !operationInProgress,
                loading = false,
                onClick = onGenerateKeyframes
            )

            Spacer(Modifier.weight(1f))

            // Tips
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("💡 Tips",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("• Draw keyframes first\n• Use onion skin for reference\n• AI works best with clear poses\n• Adjust easing for style",
                        style = MaterialTheme.typography.labelSmall,
                        lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
fun AIToolCard(
    title: String,
    description: String,
    icon: String,
    buttonText: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(buttonText, fontSize = 12.sp)
                }
            }
        }
    }
}