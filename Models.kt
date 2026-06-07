package com.animationstudio.data.models

import androidx.room.*

@Entity(tableName = "animation_projects")
data class AnimationProject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "Untitled",
    val canvasWidth: Int = 1920,
    val canvasHeight: Int = 1080,
    val fps: Int = 12,
    val totalFrames: Int = 24,
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    val onionSkinEnabled: Boolean = true,
    val onionSkinFrames: Int = 2,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val thumbnailPath: String? = null,
    val exportPath: String? = null
)

@Entity(
    tableName = "project_layers",
    foreignKeys = [ForeignKey(
        entity = AnimationProject::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class ProjectLayer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String = "Layer",
    val layerOrder: Int = 0,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1f,
    val blendMode: String = "NORMAL",
    val layerType: String = "REGULAR"
)

@Entity(
    tableName = "project_keyframes",
    foreignKeys = [
        ForeignKey(
            entity = AnimationProject::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProjectLayer::class,
            parentColumns = ["id"],
            childColumns = ["layerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId"), Index("layerId")]
)
data class ProjectKeyframe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val layerId: Long,
    val frameIndex: Int,
    val strokeData: ByteArray? = null,
    val bitmapData: ByteArray? = null,
    val isKeyframe: Boolean = true,
    val easingType: String = "LINEAR"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProjectKeyframe) return false
        return id == other.id && projectId == other.projectId &&
                layerId == other.layerId && frameIndex == other.frameIndex &&
                strokeData.contentEquals(other.strokeData) &&
                bitmapData.contentEquals(other.bitmapData) &&
                isKeyframe == other.isKeyframe && easingType == other.easingType
    }

    override fun hashCode(): Int = id.hashCode()
}

@Entity(tableName = "brush_presets")
data class BrushPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val toolType: String = "BRUSH",
    val size: Float = 5f,
    val opacity: Float = 1f,
    val hardness: Float = 0.7f,
    val spacing: Float = 1f,
    val smoothing: Float = 0.3f,
    val taper: Boolean = true,
    val pressureSensitive: Boolean = true,
    val color: Int = 0xFF000000.toInt()
)

@Entity(tableName = "color_palettes")
data class ColorPalette(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @field:TypeConverters(com.animationstudio.data.Converters::class)
    val colors: List<Int>,
    val isDefault: Boolean = false
)
