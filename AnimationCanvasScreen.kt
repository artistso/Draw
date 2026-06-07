package com.animationstudio.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animationstudio.MainViewModel
import com.animationstudio.DrawingTool
import com.animationstudio.engine.*
import com.animationstudio.tools.BrushEngine
import com.animationstudio.ui.theme.*
import com.animationstudio.util.SPenHelper
import kotlin.math.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AnimationCanvasScreen(
    viewModel: MainViewModel,
    animationEngine: AnimationEngine,
    windowSizeClass: WindowSizeClass
) {
    val currentTool by viewModel.currentTool.collectAsState()
    val brushSize by viewModel.brushSize.collectAsState()
    val brushOpacity by viewModel.brushOpacity.collectAsState()
    val currentColor by viewModel.currentColor.collectAsState()
    val layers by viewModel.layers.collectAsState()
    val currentLayerIndex by viewModel.currentLayerIndex.collectAsState()
    val onionSkinEnabled by viewModel.onionSkinEnabled.collectAsState()
    val currentFrame by viewModel.currentFrame.collectAsState()

    // Drawing state
    val currentStroke = remember { mutableStateListOf<com.animationstudio.engine.PathPoint>() }
    val undoStack = remember { mutableStateListOf<List<com.animationstudio.engine.PathPoint>>() }
    val redoStack = remember { mutableStateListOf<List<com.animationstudio.engine.PathPoint>>() }

    var canvasBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }

    val brushEngine = remember { BrushEngine() }
    val sPenState = remember { mutableStateOf(SPenHelper.PenState()) }

    val isLargeScreen = windowSizeClass.isLargeScreen

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Top toolbar
        CanvasTopToolbar(
            currentTool = currentTool,
            onToolChange = { viewModel.selectTool(it) },
            brushSize = brushSize,
            onBrushSizeChange = { viewModel.setBrushSize(it) },
            brushOpacity = brushOpacity,
            onBrushOpacityChange = { viewModel.setBrushOpacity(it) }
        )

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Side toolbar (only on large screens)
            if (isLargeScreen) {
                CanvasSideToolbar(
                    currentTool = currentTool,
                    onToolSelect = { viewModel.selectTool(it) },
                    currentColor = currentColor,
                    modifier = Modifier.width(56.dp).fillMaxHeight()
                )
            }

            // Main canvas
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Onion skin background layer
                if (onionSkinEnabled) {
                    // Render translucent previous/next frames
                }

                // Drawing canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .pointerInput(currentTool) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val motionEvent = event.changes.firstOrNull()
                                        ?: continue

                                    val position = motionEvent.position
                                    val pressure = motionEvent.pressure

                                    when {
                                        motionEvent.pressed && currentTool in setOf(
                                            DrawingTool.BRUSH, DrawingTool.PENCIL,
                                            DrawingTool.INK_PEN, DrawingTool.AIRBRUSH,
                                            DrawingTool.ERASER, DrawingTool.SMUDGE
                                        ) -> {
                                            currentStroke.add(
                                                PathPoint(
                                                    x = position.x,
                                                    y = position.y,
                                                    pressure = pressure.coerceIn(0.1f, 1f)
                                                )
                                            )
                                        }
                                        !motionEvent.pressed && currentStroke.isNotEmpty() -> {
                                            // Commit stroke to layer
                                            undoStack.add(currentStroke.toList())
                                            redoStack.clear()
                                            currentStroke.clear()
                                        }
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            // Two-finger pan/zoom for canvas navigation
                            detectTransformGestures { _, pan, zoom, _ ->
                                // Handle canvas zoom/pan
                            }
                        }
                ) {
                    canvasSize = Offset(size.width, size.height)

                    // Initialize bitmap if needed
                    if (canvasBitmap == null || canvasBitmap?.width != size.width.toInt()) {
                        canvasBitmap = Bitmap.createBitmap(
                            size.width.toInt().coerceAtLeast(1),
                            size.height.toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                    }

                    // Render existing strokes for current frame on current layer
                    val layer = layers.getOrNull(currentLayerIndex)
                    if (layer != null && layer.visible) {
                        val frameData = layer.frames[currentFrame]
                        if (frameData != null) {
                            for (stroke in frameData.strokePaths) {
                                drawStroke(stroke)
                            }
                        }
                    }

                    // Render onion skin frames
                    if (onionSkinEnabled) {
                        val onionFrames = animationEngine.getOnionSkinFrames(
                            currentFrame,
                            viewModel.totalFrames.value,
                            viewModel.onionSkinFrames.value
                        )
                        for (onion in onionFrames) {
                            val layer = layers.getOrNull(currentLayerIndex)
                            val frameData = layer?.frames?.get(onion.frameIndex)
                            if (frameData != null) {
                                for (stroke in frameData.strokePaths) {
                                    drawStroke(stroke, onion.opacity * 0.3f)
                                }
                            }
                        }
                    }

                    // Render current stroke being drawn
                    if (currentStroke.size >= 2) {
                        drawCurrentStroke(currentStroke, currentColor, brushSize, brushOpacity)
                    }

                    // Brush preview (hover indicator for S Pen)
                    if (sPenState.value.isHovering) {
                        drawCircle(
                            color = Color(currentColor).copy(alpha = 0.3f),
                            radius = brushSize / 2,
                            center = Offset(sPenState.value.hoverX, sPenState.value.hoverY)
                        )
                    }
                }
            }

            // Layer panel on right side
            LayerSidePanel(
                layers = layers,
                currentLayerIndex = currentLayerIndex,
                onLayerSelect = { viewModel.selectLayer(it) },
                onLayerVisibilityToggle = { viewModel.toggleLayerVisibility(it) },
                onAddLayer = { viewModel.addLayer() },
                onRemoveLayer = { viewModel.removeLayer(it) },
                modifier = Modifier.width(220.dp).fillMaxHeight()
            )
        }

        // Frame navigation strip at bottom
        FrameStrip(
            currentFrame = currentFrame,
            totalFrames = viewModel.totalFrames.value,
            onFrameChange = { viewModel.setCurrentFrame(it) },
            onAddFrame = { viewModel.addFrame() },
            onRemoveFrame = { viewModel.removeFrame() }
        )
    }
}

private fun DrawScope.drawStroke(stroke: Strokerath, alpha: Float = 1f) {
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

private fun DrawScope.drawCurrentStroke(
    points: List<PathPoint>,
    color: Int,
    size: Float,
    opacity: Float
) {
    if (points.size < 2) return

    val path = Path()
    path.moveTo(points[0].x, points[0].y)

    for (i in 1 until points.size) {
        path.lineTo(points[i].x, points[i].y)
    }

    drawPath(
        path = path.asComposePath(),
        color = Color(color).copy(alpha = opacity),
        style = Stroke(
            width = size * points.last().pressure,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

@Composable
fun CanvasTopToolbar(
    currentTool: DrawingTool,
    onToolChange: (DrawingTool) -> Unit,
    brushSize: Float,
    onBrushSizeChange: (Float) -> Unit,
    brushOpacity: Float,
    onBrushOpacityChange: (Float) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tool buttons
                ToolButton(DrawingTool.BRUSH, "🖌️", currentTool, onToolChange)
                ToolButton(DrawingTool.PENCIL, "✏️", currentTool, onToolChange)
                ToolButton(DrawingTool.INK_PEN, "🖊️", currentTool, onToolChange)
                ToolButton(DrawingTool.AIRBRUSH, "🎯", currentTool, onToolChange)
                ToolButton(DrawingTool.ERASER, "🧹", currentTool, onToolChange)
                ToolButton(DrawingTool.SMUDGE, "👆", currentTool, onToolChange)
                ToolButton(DrawingTool.FILL, "🪣", currentTool, onToolChange)
                ToolButton(DrawingTool.SELECTION, "⬜", currentTool, onToolChange)

                Spacer(Modifier.width(16.dp))

                // Undo/Redo
                IconButton(onClick = { /* Undo */ }) {
                    Text("↩️", fontSize = 18.sp)
                }
                IconButton(onClick = { /* Redo */ }) {
                    Text("↪️", fontSize = 18.sp)
                }

                Spacer(Modifier.width(8.dp))

                // Brush size slider
                Slider(
                    value = brushSize,
                    onValueChange = onBrushSizeChange,
                    valueRange = 0.5f..100f,
                    modifier = Modifier.weight(1f, fill = false).widthIn(min = 80.dp, max = 160.dp)
                )

                Text(
                    text = "${brushSize.toInt()}px",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(36.dp)
                )

                Spacer(Modifier.width(8.dp))

                // Opacity slider
                Slider(
                    value = brushOpacity,
                    onValueChange = onBrushOpacityChange,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f, fill = false).widthIn(min = 60.dp, max = 100.dp)
                )

                Text(
                    text = "${(brushOpacity * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun ToolButton(
    tool: DrawingTool,
    emoji: String,
    currentTool: DrawingTool,
    onClick: (DrawingTool) -> Unit
) {
    Surface(
        onClick = { onClick(tool) },
        shape = RoundedCornerShape(8.dp),
        color = if (currentTool == tool)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 16.sp)
        }
    }
}

@Composable
fun CanvasSideToolbar(
    currentTool: DrawingTool,
    onToolSelect: (DrawingTool) -> Unit,
    currentColor: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SideToolButton("🖌️", DrawingTool.BRUSH, currentTool, onToolSelect)
            SideToolButton("✏️", DrawingTool.PENCIL, currentTool, onToolSelect)
            SideToolButton("🖊️", DrawingTool.INK_PEN, currentTool, onToolSelect)
            SideToolButton("🎯", DrawingTool.AIRBRUSH, currentTool, onToolSelect)
            SideToolButton("🧹", DrawingTool.ERASER, currentTool, onToolSelect)
            Spacer(Modifier.height(16.dp))

            // Color indicator
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(currentColor))
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
    }
}

@Composable
fun SideToolButton(
    emoji: String,
    tool: DrawingTool,
    currentTool: DrawingTool,
    onClick: (DrawingTool) -> Unit
) {
    Surface(
        onClick = { onClick(tool) },
        shape = RoundedCornerShape(8.dp),
        color = if (currentTool == tool)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 18.sp)
        }
    }
}

@Composable
fun LayerSidePanel(
    layers: List<AnimationLayer>,
    currentLayerIndex: Int,
    onLayerSelect: (Int) -> Unit,
    onLayerVisibilityToggle: (Int) -> Unit,
    onAddLayer: () -> Unit,
    onRemoveLayer: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Layers", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Row {
                    IconButton(onClick = onAddLayer, modifier = Modifier.size(28.dp)) {
                        Text("➕", fontSize = 12.sp)
                    }
                }
            }

            // Layer list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(layers.reversed().size) { reversedIndex ->
                    val index = layers.lastIndex - reversedIndex
                    val layer = layers[index]
                    LayerItem(
                        layer = layer,
                        isSelected = index == currentLayerIndex,
                        onSelect = { onLayerSelect(index) },
                        onToggleVisibility = { onLayerVisibilityToggle(index) },
                        onRemove = { onRemoveLayer(index) }
                    )
                }
            }
        }
    }
}

@Composable
fun LayerItem(
    layer: AnimationLayer,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleVisibility: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        onClick = onSelect,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else Color.Transparent,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Visibility toggle
            Text(
                if (layer.visible) "👁️" else "🚫",
                fontSize = 14.sp,
                modifier = Modifier.clickable { onToggleVisibility() }
            )

            Text(
                layer.name,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )

            if (isSelected) {
                Text("✏️", fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun FrameStrip(
    currentFrame: Int,
    totalFrames: Int,
    onFrameChange: (Int) -> Unit,
    onAddFrame: () -> Unit,
    onRemoveFrame: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onFrameChange(currentFrame - 1) }) {
                Text("◀️", fontSize = 14.sp)
            }

            // Frame thumbnails
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in 0 until totalFrames) {
                    FrameThumbnail(
                        frameNumber = i + 1,
                        isCurrent = i == currentFrame,
                        onClick = { onFrameChange(i) },
                        isKeyframe = i % 6 == 0
                    )
                }
            }

            IconButton(onClick = { onFrameChange(currentFrame + 1) }) {
                Text("▶️", fontSize = 14.sp)
            }

            IconButton(onClick = onAddFrame) {
                Text("➕", fontSize = 14.sp)
            }

            Text(
                "${currentFrame + 1}/$totalFrames",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun FrameThumbnail(
    frameNumber: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
    isKeyframe: Boolean
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.width(40.dp).height(28.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (isKeyframe) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .padding(2.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                )
            }
            Text(
                "$frameNumber",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
