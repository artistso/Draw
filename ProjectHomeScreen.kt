package com.animationstudio.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animationstudio.MainViewModel
import com.animationstudio.Screen
import com.animationstudio.data.AppDatabase
import com.animationstudio.data.models.AnimationProject
import com.animationstudio.ui.theme.*
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ProjectHomeScreen(
    viewModel: MainViewModel,
    database: AppDatabase,
    windowSizeClass: WindowSizeClass
) {
    val projects by viewModel.projects.collectAsState()
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }

    val isLargeScreen = windowSizeClass.isLargeScreen
    val columns = if (isLargeScreen) 4 else if (windowSizeClass.widthClass == com.animationstudio.ui.theme.WidthClass.MEDIUM) 3 else 2

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Animation Studio",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Create stunning animations with AI assistance",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { showNewProjectDialog = true },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("+ New Project")
            }
        }

        Spacer(Modifier.height(24.dp))

        // Quick start cards
        if (projects.isEmpty()) {
            // Welcome state
            WelcomeScreen(
                isLargeScreen = isLargeScreen,
                onCreateNew = { showNewProjectDialog = true }
            )
        } else {
            // Project grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(0.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(projects.size) { index ->
                    ProjectCard(
                        project = projects[index],
                        onClick = {
                            viewModel.navigateTo(Screen.CANVAS)
                        }
                    )
                }
            }
        }
    }

    // New project dialog
    if (showNewProjectDialog) {
        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            title = { Text("New Animation Project") },
            text = {
                Column {
                    Text("Create a new animation project to start drawing and animating.",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("Project Name") },
                        placeholder = { Text("My Animation") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createNewProject(newProjectName.ifBlank { "Untitled" })
                        showNewProjectDialog = false
                        viewModel.navigateTo(Screen.CANVAS)
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun WelcomeScreen(isLargeScreen: Boolean, onCreateNew: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎬", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Welcome to Animation Studio",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Your AI-powered animation workspace for Samsung Galaxy Tab S10+",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        // Feature cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FeatureHighlight("🎨", "Draw", "Pressure-\nsensitive\nbrushes")
            Spacer(Modifier.width(16.dp))
            FeatureHighlight("🤖", "AI Tween", "Auto\nin-between\nframes")
            Spacer(Modifier.width(16.dp))
            FeatureHighlight("🦴", "Rig", "Automatic\ncharacter\nrigging")
            Spacer(Modifier.width(16.dp))
            FeatureHighlight("📤", "Export", "GIF, MP4,\nand more")
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onCreateNew,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.height(56.dp).widthIn(min = 200.dp)
        ) {
            Text("✨ Create Your First Animation", fontSize = 18.sp)
        }
    }
}

@Composable
fun FeatureHighlight(emoji: String, title: String, description: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp).width(120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 36.sp)
            Spacer(Modifier.height(8.dp))
            Text(title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
            Text(description,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp)
        }
    }
}

@Composable
fun ProjectCard(
    project: AnimationProject,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Thumbnail placeholder
            Surface(
                modifier = Modifier.fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎬", fontSize = 32.sp)
                        Text(
                            "${project.totalFrames}f",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                project.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${project.canvasWidth}×${project.canvasHeight}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${project.fps}fps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
