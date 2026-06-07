package com.animationstudio.data

import androidx.room.*
import com.animationstudio.data.models.*
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [
        AnimationProject::class,
        ProjectLayer::class,
        ProjectKeyframe::class,
        BrushPreset::class,
        ColorPalette::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM animation_projects ORDER BY lastModified DESC")
    fun getAllProjects(): Flow<List<AnimationProject>>

    @Query("SELECT * FROM animation_projects WHERE id = :id")
    suspend fun getProject(id: Long): AnimationProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: AnimationProject): Long

    @Update
    suspend fun updateProject(project: AnimationProject)

    @Delete
    suspend fun deleteProject(project: AnimationProject)

    @Query("DELETE FROM project_layers WHERE projectId = :projectId")
    suspend fun deleteLayersForProject(projectId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayers(layers: List<ProjectLayer>)

    @Query("SELECT * FROM project_layers WHERE projectId = :projectId ORDER BY layerOrder")
    suspend fun getLayersForProject(projectId: Long): List<ProjectLayer>

    @Query("SELECT * FROM project_keyframes WHERE projectId = :projectId AND layerId = :layerId ORDER BY frameIndex")
    suspend fun getKeyframes(projectId: Long, layerId: Long): List<ProjectKeyframe>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeyframes(keyframes: List<ProjectKeyframe>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBrushPreset(preset: BrushPreset)

    @Query("SELECT * FROM brush_presets ORDER BY name")
    fun getBrushPresets(): Flow<List<BrushPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertColorPalette(palette: ColorPalette)

    @Query("SELECT * FROM color_palettes ORDER BY name")
    fun getColorPalettes(): Flow<List<ColorPalette>>
}

class Converters {
    @TypeConverter
    fun fromIntList(value: List<Int>): String = value.joinToString(",")

    @TypeConverter
    fun toIntList(value: String): List<Int> =
        if (value.isEmpty()) emptyList()
        else value.split(",").map { it.toInt() }

    @TypeConverter
    fun fromFloatList(value: List<Float>): String = value.joinToString(",")

    @TypeConverter
    fun toFloatList(value: String): List<Float> =
        if (value.isEmpty()) emptyList()
        else value.split(",").map { it.toFloat() }
}
