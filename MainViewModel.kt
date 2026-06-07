package com.animationstudio

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animationstudio.data.ProjectRepository
import com.animationstudio.data.models.AnimationProject
import com.animationstudio.engine.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val repository = ProjectRepository(AnimationApp.instance.database.projectDao())

    // Project state
    private val _currentProject = MutableStateFlow<AnimationProject?>(null)
    val currentProject: StateFlow<AnimationProject?> = _currentProject.asStateFlow()

    private val _projects = MutableStateFlow<List<AnimationProject>>(emptyList())
    val projects: StateFlow<List<AnimationProject>> = _projects.asStateFlow()

    // Canvas state
    private val _currentTool = MutableStateFlow(DrawingTool.BRUSH)
    val currentTool: StateFlow<DrawingTool> = _currentTool.asStateFlow()

    private val _brushSize = MutableStateFlow(5f)
    val brushSize: StateFlow<Float> = _brushSize.asStateFlow()

    private val _brushOpacity = MutableStateFlow(1f)
    val brushOpacity: StateFlow<Float> = _brushOpacity.asStateFlow()

    private val _currentColor = MutableStateFlow(0xFF000000.toInt())
    val currentColor: StateFlow<Int> = _currentColor.asStateFlow()

    // Layer state
    private val _layers = MutableStateFlow<List<AnimationLayer>>(emptyList())
    val layers: StateFlow<List<AnimationLayer>> = _layers.asStateFlow()

    private val _currentLayerIndex = MutableStateFlow(0)
    val currentLayerIndex: StateFlow<Int> = _currentLayerIndex.asStateFlow()

    // Timeline state
    private val _currentFrame = MutableStateFlow(0)
    val currentFrame: StateFlow<Int> = _currentFrame.asStateFlow()

    private val _totalFrames = MutableStateFlow(24)
    val totalFrames: StateFlow<Int> = _totalFrames.asStateFlow()

    private val _fps = MutableStateFlow(12)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Onion skinning
    private val _onionSkinEnabled = MutableStateFlow(true)
    val onionSkinEnabled: StateFlow<Boolean> = _onionSkinEnabled.asStateFlow()

    private val _onionSkinFrames = MutableStateFlow(2)
    val onionSkinFrames: StateFlow<Int> = _onionSkinFrames.asStateFlow()

    // AI features state
    private val _aiTweeningEnabled = MutableStateFlow(true)
    val aiTweeningEnabled: StateFlow<Boolean> = _aiTweeningEnabled.asStateFlow()

    private val _motionSmoothingStrength = MutableStateFlow(0.5f)
    val motionSmoothingStrength: StateFlow<Float> = _motionSmoothingStrength.asStateFlow()

    // Screen state
    private val _currentScreen = MutableStateFlow(Screen.CANVAS)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllProjects().collect { projectList ->
                _projects.value = projectList
            }
        }
        initializeDefaultLayers()
    }

    private fun initializeDefaultLayers() {
        val defaultLayers = listOf(
            AnimationLayer(name = "Background", type = LayerType.REGULAR, visible = true),
            AnimationLayer(name = "Line Art", type = LayerType.REGULAR, visible = true),
            AnimationLayer(name = "Color", type = LayerType.REGULAR, visible = true)
        )
        _layers.value = defaultLayers
    }

    // Tool actions
    fun selectTool(tool: DrawingTool) { _currentTool.value = tool }
    fun setBrushSize(size: Float) { _brushSize.value = size.coerceIn(0.5f, 200f) }
    fun setBrushOpacity(opacity: Float) { _brushOpacity.value = opacity.coerceIn(0f, 1f) }
    fun setColor(color: Int) { _currentColor.value = color }

    // Layer actions
    fun addLayer() {
        val newLayer = AnimationLayer(
            name = "Layer ${_layers.value.size + 1}",
            type = LayerType.REGULAR
        )
        _layers.value = _layers.value + newLayer
        _currentLayerIndex.value = _layers.value.lastIndex
    }

    fun removeLayer(index: Int) {
        if (_layers.value.size > 1) {
            _layers.value = _layers.value.toMutableList().also { it.removeAt(index) }
            if (_currentLayerIndex.value >= _layers.value.size) {
                _currentLayerIndex.value = _layers.value.lastIndex
            }
        }
    }

    fun selectLayer(index: Int) { _currentLayerIndex.value = index }
    fun toggleLayerVisibility(index: Int) {
        _layers.value = _layers.value.toMutableList().also {
            it[index] = it[index].copy(visible = !it[index].visible)
        }
    }

    fun moveLayer(from: Int, to: Int) {
        val mutable = _layers.value.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        _layers.value = mutable
    }

    // Timeline actions
    fun setCurrentFrame(frame: Int) {
        _currentFrame.value = frame.coerceIn(0, _totalFrames.value - 1)
    }

    fun addFrame() { _totalFrames.value += 1 }
    fun removeFrame() {
        if (_totalFrames.value > 1) {
            _totalFrames.value -= 1
            if (_currentFrame.value >= _totalFrames.value) {
                _currentFrame.value = _totalFrames.value - 1
            }
        }
    }

    fun setFps(fps: Int) { _fps.value = fps.coerceIn(1, 60) }
    fun togglePlayback() { _isPlaying.value = !_isPlaying.value }

    // Onion skin
    fun toggleOnionSkin() { _onionSkinEnabled.value = !_onionSkinEnabled.value }
    fun setOnionSkinFrames(count: Int) {
        _onionSkinFrames.value = count.coerceIn(0, 5)
    }

    // AI
    fun toggleAITweening() { _aiTweeningEnabled.value = !_aiTweeningEnabled.value }
    fun setMotionSmoothingStrength(strength: Float) {
        _motionSmoothingStrength.value = strength.coerceIn(0f, 1f)
    }

    // Navigation
    fun navigateTo(screen: Screen) { _currentScreen.value = screen }

    // File handling
    fun handleFileOpen(uri: Uri) {
        viewModelScope.launch {
            // Handle imported files
        }
    }

    fun createNewProject(name: String) {
        viewModelScope.launch {
            val project = AnimationProject(name = name)
            repository.insert(project)
            _currentProject.value = project
        }
    }
}

enum class Screen {
    HOME, CANVAS, ANIMATION, TIMELINE, EXPORT
}

enum class DrawingTool {
    BRUSH, PENCIL, INK_PEN, AIRBRUSH, ERASER, SMUDGE, FILL, SELECTION,
    LINE, RECTANGLE, CIRCLE, MOVE, ZOOM
}
