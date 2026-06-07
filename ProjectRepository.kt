package com.animationstudio.data

import com.animationstudio.data.models.*
import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val dao: ProjectDao) {

    fun getAllProjects(): Flow<List<AnimationProject>> = dao.getAllProjects()

    suspend fun getProject(id: Long): AnimationProject? = dao.getProject(id)

    suspend fun insert(project: AnimationProject): Long = dao.insertProject(project)

    suspend fun update(project: AnimationProject) = dao.updateProject(project)

    suspend fun delete(project: AnimationProject) = dao.deleteProject(project)

    suspend fun getLayers(projectId: Long): List<ProjectLayer> =
        dao.getLayersForProject(projectId)

    suspend fun saveLayers(projectId: Long, layers: List<ProjectLayer>) {
        dao.deleteLayersForProject(projectId)
        dao.insertLayers(layers)
    }

    suspend fun getKeyframes(projectId: Long, layerId: Long): List<ProjectKeyframe> =
        dao.getKeyframes(projectId, layerId)

    suspend fun saveKeyframes(keyframes: List<ProjectKeyframe>) =
        dao.insertKeyframes(keyframes)

    suspend fun saveBrushPreset(preset: BrushPreset) =
        dao.insertBrushPreset(preset)

    fun getBrushPresets(): Flow<List<BrushPreset>> = dao.getBrushPresets()

    suspend fun saveColorPalette(palette: ColorPalette) =
        dao.insertColorPalette(palette)

    fun getColorPalettes(): Flow<List<ColorPalette>> = dao.getColorPalettes()
}
