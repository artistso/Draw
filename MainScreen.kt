package com.animationstudio.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.animationstudio.MainViewModel
import com.animationstudio.Screen
import com.animationstudio.ai.AIModelManager
import com.animationstudio.data.AppDatabase
import com.animationstudio.engine.AnimationEngine
import com.animationstudio.ui.theme.WindowSizeClass

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    database: AppDatabase,
    aiModelManager: AIModelManager,
    animationEngine: AnimationEngine,
    windowSizeClass: WindowSizeClass
) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            Screen.HOME -> ProjectHomeScreen(viewModel, database, windowSizeClass)
            Screen.CANVAS -> AnimationCanvasScreen(
                viewModel = viewModel,
                animationEngine = animationEngine,
                windowSizeClass = windowSizeClass
            )
            Screen.ANIMATION -> AnimationEditorScreen(
                viewModel = viewModel,
                animationEngine = animationEngine,
                aiModelManager = aiModelManager,
                windowSizeClass = windowSizeClass
            )
            Screen.TIMELINE -> TimelineScreen(viewModel, animationEngine, windowSizeClass)
            Screen.EXPORT -> ExportScreen(viewModel, windowSizeClass)
        }

        // Navigation bar at bottom
        BottomNavigationBar(
            currentScreen = currentScreen,
            onNavigate = { viewModel.navigateTo(it) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun BottomNavigationBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentScreen == Screen.HOME,
            onClick = { onNavigate(Screen.HOME) },
            icon = { Text("🏠", style = MaterialTheme.typography.titleMedium) },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = currentScreen == Screen.CANVAS,
            onClick = { onNavigate(Screen.CANVAS) },
            icon = { Text("🎨", style = MaterialTheme.typography.titleMedium) },
            label = { Text("Draw") }
        )
        NavigationBarItem(
            selected = currentScreen == Screen.ANIMATION,
            onClick = { onNavigate(Screen.ANIMATION) },
            icon = { Text("🎬", style = MaterialTheme.typography.titleMedium) },
            label = { Text("Animate") }
        )
        NavigationBarItem(
            selected = currentScreen == Screen.TIMELINE,
            onClick = { onNavigate(Screen.TIMELINE) },
            icon = { Text("⏱️", style = MaterialTheme.typography.titleMedium) },
            label = { Text("Timeline") }
        )
        NavigationBarItem(
            selected = currentScreen == Screen.EXPORT,
            onClick = { onNavigate(Screen.EXPORT) },
            icon = { Text("📤", style = MaterialTheme.typography.titleMedium) },
            label = { Text("Export") }
        )
    }
}
