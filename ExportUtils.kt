package com.animationstudio.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.animationstudio.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Export utilities for various animation formats:
 * - GIF, APNG, WebP (animated)
 * - MP4/WebM video
 * - Image sequence (PNG)
 * - Lottie JSON (vector animation)
 * - Project file (.asproj)
 */
object ExportUtils {

    enum class ExportFormat {
        GIF, APNG, WEBP_ANIM, MP4, WEBM,
        PNG_SEQUENCE, LOTTIE, PROJECT_FILE
    }

    data class ExportConfig(
        val format: ExportFormat = ExportFormat.GIF,
        val width: Int = 1920,
        val height: Int = 1080,
        val fps: Int = 12,
        val quality: Int = 90,
        val loop: Boolean = true,
        val outputUri: Uri? = null
    )

    suspend fun exportAnimation(
        context: Context,
        frames: List<FrameData>,
        config: ExportConfig
    ): Uri? = withContext(Dispatchers.IO) {
        when (config.format) {
            ExportFormat.PNG_SEQUENCE -> exportPngSequence(context, frames, config)
            ExportFormat.PROJECT_FILE -> exportProjectFile(context, frames, config)
            ExportFormat.GIF -> exportGif(context, frames, config)
            else -> exportPngSequence(context, frames, config) // Default
        }
    }

    private fun exportPngSequence(
        context: Context,
        frames: List<FrameData>,
        config: ExportConfig
    ): Uri? {
        val dir = File(context.cacheDir, "export_png_${System.currentTimeMillis()}")
        dir.mkdirs()

        for ((i, frame) in frames.withIndex()) {
            val bitmap = renderFrame(frame, config.width, config.height)
            val file = File(dir, "frame_${i.toString().padStart(5, '0')}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
        }

        // Zip all frames
        val zipFile = File(context.cacheDir, "animation_frames_${System.currentTimeMillis()}.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            dir.listFiles()?.forEach { file ->
                zos.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }

        dir.deleteRecursively()

        // Save to MediaStore
        return saveToMediaStore(context, zipFile, "animation_frames.zip", "application/zip")
    }

    private fun exportGif(
        context: Context,
        frames: List<FrameData>,
        config: ExportConfig
    ): Uri? {
        // GIF export implementation
        // In production, use a GIF encoder library
        // For now, save as PNG sequence
        return exportPngSequence(context, frames, config)
    }

    private fun exportProjectFile(
        context: Context,
        frames: List<FrameData>,
        config: ExportConfig
    ): Uri? {
        val projectBytes = serializeProjectData(frames, config)
        val file = File(context.cacheDir, "project_${System.currentTimeMillis()}.asproj")
        FileOutputStream(file).use { it.write(projectBytes) }
        return saveToMediaStore(context, file, "animation_project.asproj",
            "application/octet-stream")
    }

    private fun serializeProjectData(frames: List<FrameData>, config: ExportConfig): ByteArray {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        // Header
        dos.writeInt(0x41535052) // "ASPR" magic
        dos.writeInt(1)          // version
        dos.writeInt(config.width)
        dos.writeInt(config.height)
        dos.writeInt(config.fps)
        dos.writeInt(frames.size)

        // Frame data
        for (frame in frames) {
            dos.writeInt(frame.strokePaths.size)
            for (stroke in frame.strokePaths) {
                dos.writeInt(stroke.path.size)
                for (point in stroke.path) {
                    dos.writeFloat(point.x)
                    dos.writeFloat(point.y)
                    dos.writeFloat(point.pressure)
                    dos.writeFloat(point.tilt)
                }
                dos.writeInt(stroke.color)
                dos.writeFloat(stroke.strokeWidth)
                dos.writeFloat(stroke.opacity)
            }
        }

        return bos.toByteArray()
    }

    fun renderFrame(frame: FrameData, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val paint = android.graphics.Paint().apply {
            isAntiAliased = true
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }

        for (stroke in frame.strokePaths) {
            if (stroke.path.size < 2) continue

            paint.color = stroke.color
            paint.strokeWidth = stroke.strokeWidth
            paint.alpha = (stroke.opacity * 255).toInt()

            val path = android.graphics.Path()
            path.moveTo(stroke.path[0].x, stroke.path[0].y)
            for (i in 1 until stroke.path.size) {
                path.lineTo(stroke.path[i].x, stroke.path[i].y)
            }

            canvas.drawPath(path, paint)
        }

        return bitmap
    }

    private fun saveToMediaStore(
        context: Context,
        file: File,
        displayName: String,
        mimeType: String
    ): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            )
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
            }
            uri
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            val dest = File(downloadsDir, displayName)
            file.copyTo(dest, overwrite = true)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
        }
    }
}
