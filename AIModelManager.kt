package com.animationstudio.ai

import android.content.Context
import android.content.res.AssetFileDescriptor
import com.animationstudio.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Manages TensorFlow Lite models for AI-assisted animation features.
 * Models include:
 * - Tweening model: Optical flow for in-between frame generation
 * - Pose detection: Body landmark detection for auto-rigging
 * - Style model: Art style recognition and suggestion
 */
class AIModelManager(private val context: Context) {

    private var tweeningInterpreter: Interpreter? = null
    private var poseInterpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    private val isGpuSupported: Boolean by lazy {
        CompatibilityList().isDelegateSupportedOnThisDevice
    }

    /**
     * Initialize AI models
     */
    suspend fun initializeModels() = withContext(Dispatchers.IO) {
        try {
            if (isGpuSupported) {
                gpuDelegate = GpuDelegate(
                    GpuDelegate.Options()
                        .setQuantizedModelsAllowed(true)
                        .setPrecisionLossAllowed(true)
                )
            }

            // Initialize tweening model if available
            loadTweeningModel()

            // Initialize pose model if available
            loadPoseModel()
        } catch (e: Exception) {
            // Models will be downloaded on first use or use algorithmic fallback
            e.printStackTrace()
        }
    }

    private fun loadTweeningModel() {
        try {
            val modelFile = getModelFile("tweening_model.tflite")
            if (modelFile != null && modelFile.exists()) {
                val options = Interpreter.Options().apply {
                    if (gpuDelegate != null) {
                        addDelegate(gpuDelegate!!)
                    }
                    setNumThreads(4)
                    setUseNNAPI(true)
                }
                tweeningInterpreter = Interpreter(modelFile, options)
            }
        } catch (_: Exception) {
            // Model not bundled; algorithmic approach will be used
        }
    }

    private fun loadPoseModel() {
        try {
            val modelFile = getModelFile("pose_detection.tflite")
            if (modelFile != null && modelFile.exists()) {
                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                }
                poseInterpreter = Interpreter(modelFile, options)
            }
        } catch (_: Exception) {
            // Model not bundled
        }
    }

    fun isTweeningModelLoaded(): Boolean = tweeningInterpreter != null
    fun isPoseModelLoaded(): Boolean = poseInterpreter != null

    /**
     * Predict optical flow motion field between two frames
     */
    fun predictMotionField(frameA: FrameData, frameB: FrameData, t: Float): MotionField? {
        val interpreter = tweeningInterpreter ?: return null

        return try {
            // Convert frames to tensors
            val inputTensorA = frameToTensor(frameA)
            val inputTensorB = frameToTensor(frameB)
            val timeTensor = floatArrayOf(t)

            val inputA = TensorBuffer.createFixedSize(intArrayOf(1, 256, 256, 3),
                org.tensorflow.lite.DataType.FLOAT32)
            val inputB = TensorBuffer.createFixedSize(intArrayOf(1, 256, 256, 3),
                org.tensorflow.lite.DataType.FLOAT32)

            // Output motion field
            val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 256, 256, 2),
                org.tensorflow.lite.DataType.FLOAT32)

            interpreter.runForMultipleInputsOutputs(
                arrayOf(inputTensorA, inputTensorB, timeTensor),
                mapOf(0 to outputBuffer.buffer)
            )

            val outputArray = outputBuffer.floatArray
            MotionField(256, 256, outputArray)
        } catch (e: Exception) {
            null
        }
    }

    private fun frameToTensor(frame: FrameData): FloatArray {
        // Convert frame to normalized float array [0, 1]
        val tensor = FloatArray(256 * 256 * 3) { 0f }

        // Rasterize strokes to tensor
        for (stroke in frame.strokePaths) {
            for (point in stroke.path) {
                val x = (point.x * 256).toInt().coerceIn(0, 255)
                val y = (point.y * 256).toInt().coerceIn(0, 255)
                val idx = (y * 256 + x) * 3
                if (idx + 2 < tensor.size) {
                    tensor[idx] = 1f     // R - stroke presence
                    tensor[idx + 1] = point.pressure  // G - pressure
                    tensor[idx + 2] = 0f  // B
                }
            }
        }

        return tensor
    }

    private fun getModelFile(filename: String): File? {
        return try {
            val modelsDir = File(context.filesDir, "models")
            modelsDir.mkdirs()
            val modelFile = File(modelsDir, filename)

            if (modelFile.exists()) {
                modelFile
            } else {
                // Check assets
                try {
                    val afd: AssetFileDescriptor = context.assets.openFd("models/$filename")
                    afd.use {
                        modelFile.outputStream().use { output ->
                            afd.createInputStream().copyTo(output)
                        }
                    }
                    modelFile
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun close() {
        tweeningInterpreter?.close()
        poseInterpreter?.close()
        gpuDelegate?.close()
    }
}
