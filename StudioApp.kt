package com.socreate

import android.content.ContentValues
import android.graphics.*
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

// ═══════════════════════════════════════════════════════════
// COLOR SYSTEM
// ═══════════════════════════════════════════════════════════
val InkBlack = Color(0xFF0a0a10)
val DeepSlate = Color(0xFF141822)
val MidSurface = Color(0xFF222636)
val LightBorder = Color(0xFF303448)
val PrimaryBlue = Color(0xFF5b8cff)
val TealAccent = Color(0xFF00d4be)
val EmeraldGreen = Color(0xFF2cdd78)
val AmberGold = Color(0xFFf4a838)
val VioletPurple = Color(0xFF8e64f8)
val RoseRed = Color(0xFFff5c7a)
val CyanBright = Color(0xFF00c8ff)
val SlateGray = Color(0xFF8c9bac)
val TextPrimary = Color(0xFFe4e6f0)
val TextSecondary = Color(0xFFa2a6bc)
val TextMuted = Color(0xFF5e627a)

// ═══════════════════════════════════════════════════════════
// DATA MODELS
// ═══════════════════════════════════════════════════════════
data class StrokePoint(val x: Float, val y: Float, val p: Float = 1f, val t: Float = 0f)
data class DrawStroke(
    val pts: List<StrokePoint>, val color: Int = 0xFF000000.toInt(),
    val size: Float = 8f, val opacity: Float = 1f, val hardness: Float = 0.8f,
    val tool: String = "BRUSH", val scatter: Float = 0f, val texture: Int = 0,
    val layerId: Int = 0, val frameIdx: Int = 0, val isTween: Boolean = false,
    val blendMode: String = "NORMAL"
)
data class AnimLayer(val id: Int, val name: String, val visible: Boolean = true,
                     val locked: Boolean = false, val opacity: Float = 1f,
                     val blendMode: String = "NORMAL",
                     val frames: MutableMap<Int, MutableList<DrawStroke>> = mutableMapOf())
data class RigBone(val id: String, val name: String, val parentId: String?,
                   val from: RigJoint, val to: RigJoint,
                   val length: Float, val angle: Float = 0f, val stiffness: Float = 1f)
data class RigJoint(val id: String, val name: String, val x: Float, val y: Float,
                    val endpoint: Boolean = false, val radius: Float = 8f)
data class IKChain(val id: String, val name: String, val boneIds: List<String>,
                   val targetX: Float, val targetY: Float)
data class KeyframeData(val frameIndex: Int, val layerSnapshots: Map<Int, List<DrawStroke>>,
                        val color: Int = 0xFFf4a838.toInt(), val label: String = "")
data class ProjectData(val name: String, val width: Int = 1920, val height: Int = 1080,
                       val fps: Int = 12, val totalFrames: Int = 24,
                       val layers: List<SerializedLayer> = emptyList(),
                       val keyframes: List<Int> = emptyList(),
                       val tweenFrames: List<Int> = emptyList(),
                       val savedAt: Long = System.currentTimeMillis())
data class SerializedLayer(val id: Int, val name: String, val visible: Boolean,
                           val frames: Map<Int, List<SerializedStroke>> = emptyMap())
data class SerializedStroke(val pts: List<Pair<Float, Float>>, val color: Int,
                            val size: Float, val opacity: Float, val tool: String,
                            val pressures: List<Float> = emptyList())

// ═══════════════════════════════════════════════════════════
// STROKE STABILIZER + PRESSURE MAPPER
// ═══════════════════════════════════════════════════════════
class Stabilizer(var strength: Float = 0.3f) {
    private var prev: StrokePoint? = null
    private var velX = 0f; private var velY = 0f
    fun filter(x: Float, y: Float, dtSec: Float = 0.016f): StrokePoint {
        if (prev == null) { prev = StrokePoint(x, y); return prev!! }
        val cutoff = 1f / (strength * 120f + 1e-6f)
        val alpha = 1f / (1f + cutoff * dtSec)
        val sx = prev!!.x + alpha * (x - prev!!.x)
        val sy = prev!!.y + alpha * (y - prev!!.y)
        velX = velX * 0.7f + (sx - prev!!.x) / dtSec * 0.3f
        velY = velY * 0.7f + (sy - prev!!.y) / dtSec * 0.3f
        prev = StrokePoint(sx, sy)
        return prev!!
    }
    fun reset() { prev = null; velX = 0f; velY = 0f }
}

object PressureCurve {
    fun remap(raw: Float, curve: List<Float> = listOf(0f,0f,0.25f,0.25f,0.5f,0.5f,0.75f,0.75f,1f,1f)): Float {
        if (curve.size < 4) return raw
        for (i in 0 until curve.size - 2 step 2) {
            if (raw <= curve[i + 2]) {
                val t = (raw - curve[i]) / (curve[i + 2] - curve[i] + 1e-6f)
                return curve[i + 1] + (curve[i + 3] - curve[i + 1]) * t.coerceIn(0f, 1f)
            }
        }
        return curve.last()
    }
}

// ═══════════════════════════════════════════════════════════
// EASING ENGINE
// ═══════════════════════════════════════════════════════════
object Easing {
    fun apply(t: Float, type: String): Float = when (type) {
        "linear" -> t
        "smooth" -> t * t * (3 - 2 * t)
        "easein" -> t * t
        "easeout" -> 1 - (1 - t) * (1 - t)
        "easeinout" -> if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2).let { it * it } / 2
        "bounce" -> {
            val n = 7.5625f; val d = 2.75f
            when { t < 1/d -> n*t*t; t < 2/d -> { val tt=t-1.5f/d; n*tt*tt+0.75f }
                t < 2.5f/d -> { val tt=t-2.25f/d; n*tt*tt+0.9375f }
                else -> { val tt=t-2.625f/d; n*tt*tt+0.984375f } }
        }
        "elastic" -> if (t == 0f || t == 1f) t else 2.0.pow(-10 * t).toFloat() *
                sin((t * 10 - 0.75f) * (2 * PI).toFloat() / 3) + 1
        "backin" -> { val c = 1.70158f; t * t * ((c + 1) * t - c) }
        "backout" -> { val c = 1.70158f; val t1 = t - 1; t1 * t1 * ((c + 1) * t1 + c) + 1 }
        else -> t
    }
    fun interpolateStrokes(sa: List<DrawStroke>, sb: List<DrawStroke>, t: Float): List<DrawStroke> {
        if (sa.isEmpty()) return sb.map { it.copy(opacity = it.opacity * t) }
        if (sb.isEmpty()) return sa.map { it.copy(opacity = it.opacity * (1 - t)) }
        val ml = maxOf(sa.size, sb.size)
        return (0 until ml).map { i ->
            val a = sa[i % sa.size]; val b = sb[i % sb.size]
            val mp = maxOf(a.pts.size, b.pts.size)
            val pts = (0 until mp).map { p ->
                val pa = a.pts[(p * a.pts.size / mp).coerceIn(0, a.pts.lastIndex)]
                val pb = b.pts[(p * b.pts.size / mp).coerceIn(0, b.pts.lastIndex)]
                StrokePoint(pa.x + (pb.x - pa.x) * t, pa.y + (pb.y - pa.y) * t, (pa.p + pb.p) / 2)
            }
            val ca = Color(a.color); val cb = Color(b.color)
            val ic = android.graphics.Color.argb(
                ((ca.alpha + (cb.alpha - ca.alpha) * t) * 255).toInt().coerceIn(0, 255),
                ((ca.red + (cb.red - ca.red) * t) * 255).toInt().coerceIn(0, 255),
                ((ca.green + (cb.green - ca.green) * t) * 255).toInt().coerceIn(0, 255),
                ((ca.blue + (cb.blue - ca.blue) * t) * 255).toInt().coerceIn(0, 255)
            )
            DrawStroke(pts, ic, a.size + (b.size - a.size) * t,
                a.opacity + (b.opacity - a.opacity) * t,
                a.hardness + (b.hardness - a.hardness) * t,
                if (t < 0.5f) a.tool else b.tool, isTween = true)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// VIEWMODEL — All application state
// ═══════════════════════════════════════════════════════════
class StudioState : ViewModel() {
    // Drawing
    var tool by mutableStateOf("BRUSH"); var size by mutableStateOf(8f)
    var opacity by mutableStateOf(1f); var hardness by mutableStateOf(0.8f)
    var stabilizer by mutableStateOf(0.3f); var color by mutableStateOf(0xFF000000.toInt())
    var scatter by mutableStateOf(0f); var dualBrush by mutableStateOf(false)

    // Animation
    var currentFrame by mutableStateOf(0); var totalFrames by mutableStateOf(24)
    var fps by mutableStateOf(12); var playing by mutableStateOf(false)

    // Modifiers
    var shiftHeld by mutableStateOf(false); var ctrlHeld by mutableStateOf(false)
    var altHeld by mutableStateOf(false); var stylusOnly by mutableStateOf(false)

    // Onion
    var onionEnabled by mutableStateOf(true); var onionCount by mutableStateOf(2)
    var onionPrevColor by mutableStateOf(0xFF3b82f6.toInt())
    var onionNextColor by mutableStateOf(0xFFef4444.toInt())

    // Viewport
    var zoom by mutableStateOf(1f); var panX by mutableStateOf(0f); var panY by mutableStateOf(0f)

    // Grid + Perspective
    var showGrid by mutableStateOf(false); var gridSize by mutableStateOf(64f)
    var gridSnap by mutableStateOf(false)
    var showPerspBox by mutableStateOf(true); var perspDepth by mutableStateOf(0.6f)
    var perspVP by mutableStateOf(Pair(0.5f, 0.4f))

    // Layers
    var layers by mutableStateOf(listOf(
        AnimLayer(0,"Background"), AnimLayer(1,"Line Art"),
        AnimLayer(2,"Color"), AnimLayer(3,"Effects"), AnimLayer(4,"Overlay")
    ))
    var currentLayer by mutableStateOf(1)

    // Keyframes + Tweens
    var keyframes by mutableStateOf(mutableSetOf(0, 6, 12, 18))
    var tweenFrames by mutableStateOf(mutableSetOf<Int>())
    var selectedFrames by mutableStateOf(mutableSetOf<Int>())
    var multiFrameMode by mutableStateOf(false)

    // Selection + Transform
    var selBounds by mutableStateOf<Rect?>(null)
    var selStrokes by mutableStateOf<List<DrawStroke>>(emptyList())
    var transformMode by mutableStateOf("") // "move", "scale", "rotate"

    // Rigging
    var showRig by mutableStateOf(false); var rigBones by mutableStateOf<List<RigBone>>(emptyList())
    var rigJoints by mutableStateOf<List<RigJoint>>(emptyList())
    var rigIKChains by mutableStateOf<List<IKChain>>(emptyList())
    var selectedJoint by mutableStateOf<String?>(null)

    // Audio
    var audioEnabled by mutableStateOf(false); var audioDuration by mutableStateOf(0f)

    // Pressure curve
    var pressureCurve by mutableStateOf(listOf(0f,0f,0.25f,0.25f,0.5f,0.5f,0.75f,0.75f,1f,1f))

    // Easing selection
    var currentEasing by mutableStateOf("smooth"); var tweenCount by mutableStateOf(3)

    // Panels
    var showLayers by mutableStateOf(false); var showRigPanel by mutableStateOf(false)
    var showSettings by mutableStateOf(false); var showGallery by mutableStateOf(false)

    // Undo/Redo
    private val undoStack = mutableListOf<UndoEntry>(); private val redoStack = mutableListOf<UndoEntry>()
    data class UndoEntry(val frame: Int, val layerIdx: Int, val strokes: List<DrawStroke>,
                         val rigBones: List<RigBone>, val rigJoints: List<RigJoint>)

    // Projects
    var projects by mutableStateOf<List<ProjectData>>(emptyList())
    var currentProjectName by mutableStateOf("Untitled")

    // Toast
    var toastMsg by mutableStateOf(""); var toastVisible by mutableStateOf(false)

    fun strokesFor(frame: Int, layerIdx: Int): List<DrawStroke> =
        layers.getOrNull(layerIdx)?.frames?.get(frame) ?: emptyList()

    fun commitStroke(stroke: DrawStroke) {
        saveUndo()
        layers[currentLayer].frames.getOrPut(currentFrame) { mutableListOf() }.add(stroke)
    }

    fun goFrame(i: Int) { currentFrame = ((i % totalFrames) + totalFrames) % totalFrames }
    fun addFrame() { totalFrames++ }
    fun removeFrame() { if (totalFrames > 1) {
        layers.forEach { it.frames.remove(totalFrames - 1) }; totalFrames--; goFrame(currentFrame)
    }}
    fun togglePlay() { playing = !playing }
    fun toggleKey() = if (currentFrame in keyframes) keyframes.remove(currentFrame) else keyframes.add(currentFrame)
    fun clearFrame() { saveUndo(); layers.forEach { it.frames.remove(currentFrame) } }
    fun dupFrame() {
        totalFrames++; val ni = totalFrames - 1
        layers.forEach { l -> l.frames[currentFrame]?.let { l.frames[ni] = it.map { it.copy() }.toMutableList() } }
        if (currentFrame in keyframes) keyframes.add(ni); goFrame(ni)
    }

    private fun saveUndo() {
        val layer = layers[currentLayer]
        val st = layer.frames[currentFrame]?.map { it.copy() } ?: emptyList()
        undoStack.add(UndoEntry(currentFrame, currentLayer, st, rigBones.toList(), rigJoints.toList()))
        if (undoStack.size > 200) undoStack.removeFirst(); redoStack.clear()
    }
    fun undo() {
        val e = undoStack.removeLastOrNull() ?: return
        val layer = layers[e.layerIdx]
        val cur = layer.frames.getOrPut(e.frame) { mutableListOf() }.map { it.copy() }
        redoStack.add(UndoEntry(e.frame, e.layerIdx, cur, rigBones.toList(), rigJoints.toList()))
        layer.frames[e.frame] = e.strokes.map { it.copy() }.toMutableList()
        rigBones = e.rigBones; rigJoints = e.rigJoints; currentFrame = e.frame; toast("Undo")
    }
    fun redo() {
        val e = redoStack.removeLastOrNull() ?: return
        val layer = layers[e.layerIdx]
        val cur = layer.frames.getOrPut(e.frame) { mutableListOf() }.map { it.copy() }
        undoStack.add(UndoEntry(e.frame, e.layerIdx, cur, rigBones.toList(), rigJoints.toList()))
        layer.frames[e.frame] = e.strokes.map { it.copy() }.toMutableList()
        rigBones = e.rigBones; rigJoints = e.rigJoints; currentFrame = e.frame; toast("Redo")
    }

    // ── SELECTION ──
    fun selectAll() {
        val all = mutableListOf<DrawStroke>()
        for (layer in layers) layer.frames[currentFrame]?.let { all.addAll(it) }
        selStrokes = all; computeSelBounds(); toast("All selected")
    }
    fun selectClear() { selStrokes = emptyList(); selBounds = null; toast("Cleared") }
    fun selectDelete() {
        if (selBounds == null) { toast("Nothing selected"); return }
        saveUndo()
        for (layer in layers) {
            layer.frames[currentFrame]?.let { strokes ->
                strokes.removeAll { s -> s.pts.any { p -> p.x >= selBounds!!.left - 2 && p.x <= selBounds!!.right + 2 && p.y >= selBounds!!.top - 2 && p.y <= selBounds!!.bottom + 2 } }
            }
        }
        selBounds = null; selStrokes = emptyList(); toast("Deleted")
    }
    fun selectMove(dx: Float, dy: Float) {
        if (selBounds == null) return; saveUndo()
        for (layer in layers) {
            layer.frames[currentFrame]?.let { strokes ->
                for (s in strokes) {
                    if (s.pts.any { p -> p.x >= selBounds!!.left - 1 && p.x <= selBounds!!.right + 1 && p.y >= selBounds!!.top - 1 && p.y <= selBounds!!.bottom + 1 })
                        for (pt in s.pts) { pt = StrokePoint(pt.x + dx, pt.y + dy, pt.p, pt.t) }
                }
            }
        }
        selBounds = selBounds!!.translate(dx, dy)
    }
    fun selectScale(fx: Float, fy: Float) {
        if (selBounds == null) return
        val cx = selBounds!!.center.x; val cy = selBounds!!.center.y; saveUndo()
        for (layer in layers) {
            layer.frames[currentFrame]?.let { strokes ->
                for (s in strokes) {
                    if (s.pts.any { p -> p.x >= selBounds!!.left - 1 && p.x <= selBounds!!.right + 1 && p.y >= selBounds!!.top - 1 && p.y <= selBounds!!.bottom + 1 })
                        for (pt in s.pts) {
                            pt = StrokePoint(cx + (pt.x - cx) * fx, cy + (pt.y - cy) * fy, pt.p, pt.t)
                        }
                }
            }
        }
        computeSelBounds()
    }
    private fun computeSelBounds() {
        val all = mutableListOf<Pair<Float,Float>>()
        for (s in selStrokes) for (p in s.pts) all.add(p.x to p.y)
        if (all.isEmpty()) { selBounds = null; return }
        selBounds = Rect(all.minOf{it.first}, all.minOf{it.second}, all.maxOf{it.first}, all.maxOf{it.second})
    }

    // ── RIGGING ──
    fun autoRig() {
        val allPts = mutableListOf<StrokePoint>()
        for (layer in layers) layer.frames[currentFrame]?.let { for (s in it) allPts.addAll(s.pts) }
        if (allPts.size < 10) { toast("Draw more to auto-rig!"); return }
        val xs = allPts.map{it.x}; val ys = allPts.map{it.y}
        val minX=xs.min(); val maxX=xs.max(); val minY=ys.min(); val maxY=ys.max()
        val cx=(minX+maxX)/2; val bH=maxY-minY; val bW=maxX-minX
        val joints = listOf(
            RigJoint("j0","Head",cx,minY+bH*0.12f,true),
            RigJoint("j1","Neck",cx,minY+bH*0.22f),
            RigJoint("j2","Spine",cx,minY+bH*0.42f),
            RigJoint("j3","Hip",cx,minY+bH*0.55f),
            RigJoint("j4","L Shoulder",minX+bW*0.15f,minY+bH*0.25f),
            RigJoint("j5","R Shoulder",maxX-bW*0.15f,minY+bH*0.25f),
            RigJoint("j6","L Elbow",minX+bW*0.06f,minY+bH*0.42f),
            RigJoint("j7","R Elbow",maxX-bW*0.06f,minY+bH*0.42f),
            RigJoint("j8","L Wrist",minX+bW*0.03f,minY+bH*0.58f,true),
            RigJoint("j9","R Wrist",maxX-bW*0.03f,minY+bH*0.58f,true),
            RigJoint("j10","L Hip",cx-bW*0.1f,minY+bH*0.55f),
            RigJoint("j11","R Hip",cx+bW*0.1f,minY+bH*0.55f),
            RigJoint("j12","L Knee",cx-bW*0.08f,minY+bH*0.75f),
            RigJoint("j13","R Knee",cx+bW*0.08f,minY+bH*0.75f),
            RigJoint("j14","L Ankle",cx-bW*0.06f,maxY,true),
            RigJoint("j15","R Ankle",cx+bW*0.06f,maxY,true),
        )
        val bones = listOf(
            RigBone("b0","Spine",null,joints[3],joints[2],60f),
            RigBone("b1","Neck","b0",joints[2],joints[1],40f),
            RigBone("b2","Head","b1",joints[1],joints[0],30f),
            RigBone("b3","L Clavicle","b1",joints[1],joints[4],25f),
            RigBone("b4","R Clavicle","b1",joints[1],joints[5],25f),
            RigBone("b5","L UpperArm","b3",joints[4],joints[6],35f),
            RigBone("b6","L Forearm","b5",joints[6],joints[8],30f),
            RigBone("b7","R UpperArm","b4",joints[5],joints[7],35f),
            RigBone("b8","R Forearm","b7",joints[7],joints[9],30f),
            RigBone("b9","L Thigh",null,joints[10],joints[12],45f),
            RigBone("b10","L Shin","b9",joints[12],joints[14],40f),
            RigBone("b11","R Thigh",null,joints[11],joints[13],45f),
            RigBone("b12","R Shin","b11",joints[13],joints[15],40f),
        )
        rigJoints=joints; rigBones=bones; showRig=true; toast("Rigged!")
    }
    fun solveIK(chainId: String, targetX: Float, targetY: Float) {
        val chain = rigIKChains.find { it.id == chainId } ?: return
        val bones = rigBones.toMutableList(); val joints = rigJoints.toMutableList()
        for (iter in 0 until 20) {
            for (boneId in chain.boneIds.reversed()) {
                val bi = bones.indexOfFirst{it.id==boneId}; if (bi<0) continue
                val bone = bones[bi]
                val ei = joints.indexOfFirst{it.id==bone.to.id}; if (ei<0) continue
                val base = joints.indexOfFirst{it.id==bone.from.id}; if (base<0) continue
                val ex=joints[ei].x-bone.from.x; val ey=joints[ei].y-bone.from.y
                val tx=targetX-bone.from.x; val ty=targetY-bone.from.y
                val eLen=sqrt(ex*ex+ey*ey); val tLen=sqrt(tx*tx+ty*ty)
                if (eLen<0.001f||tLen<0.001f) continue
                val cosA=(ex*tx+ey*ty)/(eLen*tLen); val angle=acos(cosA.coerceIn(-1f,1f))
                val cross=ex*ty-ey*tx; val rot=if(cross>0)angle else -angle
                // Apply rotation to all children
                val children=getChildIds(bone.to.id,bones,emptySet())
                for (cj in children) {
                    val ji=joints.indexOfFirst{it.id==cj}; if (ji<0) continue
                    val rx=joints[ji].x-bone.from.x; val ry=joints[ji].y-bone.from.y
                    val c=cos(rot); val s=sin(rot)
                    joints[ji]=joints[ji].copy(x=bone.from.x+rx*c-ry*s,y=bone.from.y+rx*s+ry*c)
                }
            }
            // Check convergence
            val ee=joints.find{it.id==bones.find{bb->bb.id==chain.boneIds.last()}?.to?.id}
            if (ee!=null) {
                val dx=targetX-ee.x; val dy=targetY-ee.y
                if (sqrt(dx*dx+dy*dy)<1f) break
            }
        }
        rigBones=bones; rigJoints=joints
    }
    private fun getChildIds(pid: String, bones: List<RigBone>, visited: Set<String>): Set<String> {
        val r=visited.toMutableSet(); r.add(pid)
        for (b in bones) { if (b.parentId==pid&&b.to.id !in r) r.addAll(getChildIds(b.to.id,bones,r)) }
        return r
    }

    // ── TWEENING ──
    fun genTweens(easeType: String = currentEasing, count: Int = tweenCount) {
        if (keyframes.size<2) { toast("Need 2+ keyframes!"); return }
        val keys=keyframes.sorted(); var cnt=0
        for (k in 0 until keys.size-1) {
            val fa=keys[k]; val fb=keys[k+1]; val gap=fb-fa; if (gap<=1) continue
            for (ib in 1..minOf(count,gap-1)) {
                val tf=fa+ib; val t=Easing.apply(ib.toFloat()/(count+1),easeType)
                for (layer in layers) {
                    val sa=layer.frames[fa]?:emptyList(); val sb=layer.frames[fb]?:emptyList()
                    if (sa.isEmpty()&&sb.isEmpty()) continue
                    layer.frames.getOrPut(tf){ mutableListOf() }.clear()
                    layer.frames[tf]!!.addAll(Easing.interpolateStrokes(sa,sb,t))
                }
                tweenFrames.add(tf); cnt++
            }
        }
        toast("$cnt tween frames ($easeType)")
    }
    fun clearTweens() { for (tf in tweenFrames) layers.forEach{it.frames.remove(tf)}; tweenFrames.clear(); toast("Tweens cleared") }

    // ── MULTI-FRAME ──
    fun mfToggle(frame: Int) = if (frame in selectedFrames) selectedFrames.remove(frame) else selectedFrames.add(frame)
    fun mfClear() { selectedFrames.clear() }
    fun mfDelete() {
        val sorted = selectedFrames.sortedDescending()
        for (fi in sorted) { layers.forEach { it.frames.remove(fi) }; keyframes.remove(fi); tweenFrames.remove(fi) }
        selectedFrames.clear(); totalFrames = (0 until totalFrames).count { i ->
            layers.any { it.frames.containsKey(i) } || i < sorted.minOrNull() ?: totalFrames
        }.coerceAtLeast(1)
        goFrame(currentFrame)
    }

    // ── PROJECTS ──
    fun saveProject() {
        val serLayers = layers.map { l ->
            SerializedLayer(l.id, l.name, l.visible,
                l.frames.mapValues{(_,v)->v.map{s->SerializedStroke(s.pts.map{it.x to it.y},s.color,s.size,s.opacity,s.tool,s.pts.map{it.p})}})
        }
        val proj = ProjectData(currentProjectName, 1920, 1080, fps, totalFrames, serLayers,
            keyframes.toList(), tweenFrames.toList())
        projects = (projects.filter{it.name!=currentProjectName} + proj).takeLast(50)
        toast("Saved: $currentProjectName")
    }
    fun loadProject(idx: Int) {
        val p = projects.getOrNull(idx) ?: return
        currentProjectName = p.name; fps = p.fps; totalFrames = p.totalFrames; currentFrame = 0
        layers = p.layers.map { sl ->
            AnimLayer(sl.id, sl.name, sl.visible, frames = sl.frames.mapValues { (_, v) ->
                v.map { ss -> DrawStroke(ss.pts.map{StrokePoint(it.first,it.second,ss.pressures.getOrElse(ss.pts.indexOf(it)){1f})}, ss.color, ss.size, ss.opacity, tool = ss.tool) }.toMutableList()
            }.toMutableMap())
        }
        keyframes = p.keyframes.toMutableSet(); tweenFrames = p.tweenFrames.toMutableSet()
        toast("Loaded: ${p.name}")
    }
    fun exportGIF(context: android.content.Context) {
        kotlinx.coroutines.MainScope().launch {
            val origCf = currentFrame; val origPlay = playing; playing = false
            toast("Rendering ${totalFrames} frames...")
            val bitmaps = mutableListOf<Bitmap>()
            // Render each frame to a bitmap — simplified for Compose
            // In production, use a PixelCopy or Canvas approach
            for (i in 0 until totalFrames) { currentFrame = i; bitmaps.add(Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888)) }
            currentFrame = origCf; if (origPlay) { playing = true }
            // Save as PNG sequence
            val dir = File(context.cacheDir, "export_${System.currentTimeMillis()}"); dir.mkdirs()
            bitmaps.forEachIndexed { i, bmp ->
                FileOutputStream(File(dir, "frame_${i.toString().padStart(5,'0')}.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
            }
            toast("Exported to ${dir.absolutePath}")
        }
    }

    // ── TOAST ──
    fun toast(msg: String) { toastMsg = msg; toastVisible = true }
}

// ═══════════════════════════════════════════════════════════
// MAIN STUDIO COMPOSABLE
// ═══════════════════════════════════════════════════════════
@Composable
fun StudioApp(vm: StudioState = viewModel()) {
    val ctx = LocalContext.current
    val currentStroke = remember { mutableStateListOf<StrokePoint>() }
    var currentPressure by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomScale by remember { mutableStateOf(1f) }
    val stabilizer = remember { Stabilizer(vm.stabilizer) }
    val coroutineScope = rememberCoroutineScope()
    var lastStabTime by remember { mutableStateOf(System.nanoTime()) }

    LaunchedEffect(vm.stabilizer) { stabilizer.strength = vm.stabilizer }
    LaunchedEffect(vm.playing, vm.fps) {
        if (vm.playing) { while (vm.playing) { delay((1000/vm.fps).toLong()); vm.currentFrame = (vm.currentFrame+1)%vm.totalFrames } }
    }
    // Toast auto-dismiss
    LaunchedEffect(vm.toastVisible) { if (vm.toastVisible) { delay(1800); vm.toastVisible = false } }

    Box(modifier = Modifier.fillMaxSize().background(InkBlack)) {
        // ═══ CANVAS ═══
        Canvas(modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomScale = (zoomScale * zoom).coerceIn(0.08f, 10f); panOffset += pan
                }
            }
            .pointerInput(vm.tool, vm.stylusOnly) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(); val change = event.changes.firstOrNull()?:continue
                        if (vm.stylusOnly && change.type != PointerType.Stylus) continue
                        val pos = change.position
                        val rawPress = change.pressure.coerceIn(0.1f, 1f)
                        val press = PressureCurve.remap(rawPress, vm.pressureCurve)
                        val now = System.nanoTime(); val dt = ((now - lastStabTime) / 1e9f).coerceAtMost(0.1f)
                        lastStabTime = now
                        val st = stabilizer.filter(pos.x, pos.y, dt)

                        // Liquify: deform existing strokes
                        if (vm.tool == "LIQUIFY" && change.pressed) {
                            vm.saveUndo()
                            val r2 = (vm.size*5).let{it*it}
                            for (layer in vm.layers) layer.frames[vm.currentFrame]?.forEach { s ->
                                for (pt in s.pts) {
                                    val dx=pt.x-st.x; val dy=pt.y-st.y; val d2=dx*dx+dy*dy
                                    if (d2<r2&&d2>0.25f) {
                                        val f=(1-d2/r2)*vm.size*0.5f
                                        pt = StrokePoint(pt.x+dx/sqrt(d2)*f, pt.y+dy/sqrt(d2)*f, pt.p, pt.t)
                                    }
                                }
                            }
                            continue
                        }

                        // Normal drawing
                        if (change.pressed && currentStroke.isEmpty()) {
                            currentStroke.add(st); currentPressure = press
                        } else if (!change.pressed && currentStroke.isNotEmpty()) {
                            val pts = currentStroke.toList()
                            // Scatter
                            val finalPts = if (vm.scatter > 0f) pts.map { p ->
                                val a = (Math.random()*2*PI).toFloat()
                                val r = (Math.random()*vm.scatter*vm.size).toFloat()
                                StrokePoint(p.x+cos(a)*r, p.y+sin(a)*r, p.p, p.t)
                            } else pts
                            vm.commitStroke(DrawStroke(finalPts, vm.color, vm.size * currentPressure,
                                vm.opacity, vm.hardness, vm.tool, vm.scatter, layerId = vm.currentLayer,
                                frameIdx = vm.currentFrame))
                            currentStroke.clear()
                        } else if (currentStroke.isNotEmpty()) {
                            currentStroke.add(st); currentPressure = (currentPressure+press)/2
                        }
                    }
                }
            }
        ) {
            val w=size.width; val h=size.height
            withTransform({ translate(w/2,h/2); scale(zoomScale,zoomScale,Offset.Zero); translate(-w/2+panOffset.x,-h/2+panOffset.y) }) {
                // Perspective box
                if (vm.showPerspBox) drawPerspectiveBox(w, h, vm.perspDepth, vm.perspVP)
                // Grid
                if (vm.showGrid) {
                    for (x in 0..w.toInt() step vm.gridSize.toInt())
                        drawLine(Color.White.copy(alpha=0.06f),Offset(x.toFloat(),0f),Offset(x.toFloat(),h),0.5f)
                    for (y in 0..h.toInt() step vm.gridSize.toInt())
                        drawLine(Color.White.copy(alpha=0.06f),Offset(0f,y.toFloat()),Offset(w,y.toFloat()),0.5f)
                }
                // Onion skin
                if (vm.onionEnabled) {
                    for (o in 1..vm.onionCount) {
                        val pv=(vm.currentFrame-o+vm.totalFrames)%vm.totalFrames
                        val nx=(vm.currentFrame+o)%vm.totalFrames
                        val op=(1f-o.toFloat()/(vm.onionCount+1))*0.35f
                        if (pv!=vm.currentFrame) for (s in vm.strokesFor(pv,vm.currentLayer)) drawStroke(s,op,Color(vm.onionPrevColor))
                        if (nx!=vm.currentFrame) for (s in vm.strokesFor(nx,vm.currentLayer)) drawStroke(s,op,Color(vm.onionNextColor))
                    }
                }
                // Render all visible layers
                for ((_,layer) in vm.layers.withIndex()) {
                    if (!layer.visible) continue
                    for (s in vm.strokesFor(vm.currentFrame,layer.id))
                        drawStroke(s,s.opacity,Color(s.color))
                }
                // Current stroke preview
                if (currentStroke.size>=2) {
                    val path=Path().apply{ moveTo(currentStroke[0].x,currentStroke[0].y); for(i in 1 until currentStroke.size) lineTo(currentStroke[i].x,currentStroke[i].y) }
                    drawPath(path,Color(vm.color).copy(alpha=vm.opacity),style=Stroke(width=vm.size*currentPressure,cap=StrokeCap.Round,join=StrokeJoin.Round))
                }
                // Selection box
                vm.selBounds?.let { b ->
                    drawRect(Color(0xFF5b8cff).copy(alpha=0.15f),b.topLeft,b.size)
                    drawRect(Color(0xFF5b8cff),b.topLeft,b.size,style=Stroke(2f))
                    // Handles
                    val hs=listOf(b.topLeft,Offset(b.center.x,b.top),Offset(b.right,b.top),Offset(b.right,b.center.y),Offset(b.right,b.bottom),Offset(b.center.x,b.bottom),Offset(b.left,b.bottom),Offset(b.left,b.center.y))
                    for (h in hs) { drawCircle(Color.White,5f,h); drawCircle(Color(0xFF5b8cff),5f,h,style=Stroke(1.5f)) }
                }
                // Rig overlay
                if (vm.showRig) {
                    for (bone in vm.rigBones) drawLine(RoseRed.copy(alpha=0.6f),Offset(bone.from.x,bone.from.y),Offset(bone.to.x,bone.to.y),3f)
                    for (joint in vm.rigJoints) {
                        drawCircle(if(joint.endpoint)AmberGold else CyanBright,if(joint.endpoint)10f else 5f,Offset(joint.x,joint.y))
                        if (joint.id==vm.selectedJoint) drawCircle(Color.White.copy(alpha=0.3f),14f,Offset(joint.x,joint.y))
                    }
                    for (chain in vm.rigIKChains) drawCircle(AmberGold,8f,Offset(chain.targetX,chain.targetY),style=Stroke(2f))
                }
            }
        }

        // ═══ TOP BAR ═══
        Surface(modifier=Modifier.align(Alignment.TopCenter).fillMaxWidth(),color=DeepSlate.copy(alpha=0.88f),tonalElevation=4.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                Text("SoCreate",color=PrimaryBlue,fontWeight=FontWeight.Bold,fontSize=14.sp)
                Spacer(Modifier.width(12.dp))
                Text("FPS:",fontSize=9.sp,color=TextMuted)
                listOf(8,12,24,30,60).forEach{ f -> FilterChip(selected=vm.fps==f,onClick={vm.fps=f},label={Text("$f",fontSize=8.sp)},modifier=Modifier.height(20.dp)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick={vm.goFrame(vm.currentFrame-1)}){Text("◀",fontSize=12.sp)}
                IconButton(onClick={vm.togglePlay()}){Text(if(vm.playing)"⏸" else "▶",fontSize=16.sp,color=TealAccent)}
                TextButton(onClick={vm.goFrame(vm.currentFrame+1)}){Text("▶",fontSize=12.sp)}
                Spacer(Modifier.width(4.dp))
                Text("${vm.currentFrame+1}/${vm.totalFrames}",fontSize=10.sp,color=TextSecondary)
                TextButton(onClick={vm.addFrame()}){Text("+",fontSize=14.sp)}
                TextButton(onClick={vm.removeFrame()}){Text("-",fontSize=14.sp)}
                TextButton(onClick={vm.toggleKey()}){Text("◆",fontSize=12.sp,color=if(vm.currentFrame in vm.keyframes)AmberGold else TextMuted)}
                TextButton(onClick={vm.dupFrame()}){Text("⧉",fontSize=12.sp)}
                TextButton(onClick={vm.clearFrame()}){Text("⌫",fontSize=12.sp)}
                Spacer(Modifier.width(8.dp))
                // Easing selector
                var expEase by remember{mutableStateOf(false)}
                Box{ TextButton(onClick={expEase=!expEase}){Text(vm.currentEasing,fontSize=9.sp,color=TealAccent)}
                    DropdownMenu(expanded=expEase,onDismissRequest={expEase=false}){
                        listOf("smooth","linear","easein","easeout","easeinout","bounce","elastic","backin","backout").forEach{e->
                            DropdownMenuItem(text={Text(e,fontSize=11.sp)},onClick={vm.currentEasing=e;expEase=false})
                        }
                    }
                }
                TextButton(onClick={vm.genTweens()}){Text("↻Tween",fontSize=9.sp,color=CyanBright)}
            }
        }

        // ═══ MODIFIER BAR (right) ═══
        Surface(modifier=Modifier.align(Alignment.CenterEnd).padding(end=8.dp),shape=RoundedCornerShape(12.dp),color=DeepSlate.copy(alpha=0.85f),tonalElevation=6.dp) {
            Column(Modifier.padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                ModKey("⇧","Shift",vm.shiftHeld,RoseRed){vm.shiftHeld=!vm.shiftHeld}
                ModKey("⌃","Ctrl",vm.ctrlHeld,CyanBright){vm.ctrlHeld=!vm.ctrlHeld}
                ModKey("⎇","Alt",vm.altHeld,AmberGold){vm.altHeld=!vm.altHeld}
                Divider(color=LightBorder.copy(alpha=0.3f))
                ModKey("↩","Undo",TextSecondary){vm.undo()}
                ModKey("↪","Redo",TextSecondary){vm.redo()}
                Divider(color=LightBorder.copy(alpha=0.3f))
                ModKey("✎","Stylus",if(vm.stylusOnly)PrimaryBlue else TextMuted){vm.stylusOnly=!vm.stylusOnly}
                ModKey("⊞","Grid",if(vm.showGrid)EmeraldGreen else TextMuted){vm.showGrid=!vm.showGrid}
                ModKey("◫","Box",if(vm.showPerspBox)RoseRed else TextMuted){vm.showPerspBox=!vm.showPerspBox}
                ModKey("⊠","Sel",TextSecondary){vm.selectAll()}
                ModKey("⊗","Del",RoseRed){vm.selectDelete()}
            }
        }

        // ═══ BOTTOM TOOL ORBS ═══
        Column(modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            // Draw settings bar
            Surface(modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),color=DeepSlate.copy(alpha=0.78f),shape=RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=5.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                    Text("SZ",fontSize=7.sp,color=TextMuted)
                    Slider(vm.size,{vm.size=it},valueRange=0.5f..120f,modifier=Modifier.weight(1f).height(18.dp))
                    Text("${vm.size.toInt()}",fontSize=8.sp,color=TextSecondary)
                    Text("OP",fontSize=7.sp,color=TextMuted)
                    Slider(vm.opacity,{vm.opacity=it},valueRange=0.05f..1f,modifier=Modifier.weight(1f).height(18.dp))
                    Text("H",fontSize=7.sp,color=TextMuted)
                    Slider(vm.hardness,{vm.hardness=it},valueRange=0.05f..1f,modifier=Modifier.weight(1f).height(18.dp))
                    Text("ST",fontSize=7.sp,color=TextMuted)
                    Slider(vm.stabilizer,{vm.stabilizer=it},valueRange=0f..1f,modifier=Modifier.weight(1f).height(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            // Tool orbs
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                ToolOrb("◉",PrimaryBlue,vm.tool=="BRUSH"){vm.tool="BRUSH"}
                ToolOrb("◇",PrimaryBlue,vm.tool=="PENCIL"){vm.tool="PENCIL"}
                ToolOrb("◆",PrimaryBlue,vm.tool=="INK"){vm.tool="INK"}
                ToolOrb("○",PrimaryBlue,vm.tool=="AIRBRUSH"){vm.tool="AIRBRUSH"}
                ToolOrb("◌",RoseRed,vm.tool=="ERASER"){vm.tool="ERASER"}
                ToolOrb("〜",VioletPurple,vm.tool=="LIQUIFY"){vm.tool="LIQUIFY"}
                ToolOrb("◑",AmberGold,vm.onionEnabled){vm.onionEnabled=!vm.onionEnabled}
                ToolOrb("⊞",EmeraldGreen,vm.currentLayer==1){vm.currentLayer=(vm.currentLayer+1)%vm.layers.size}
                ToolOrb("◎",TealAccent,vm.showRig){if(vm.rigJoints.isEmpty())vm.autoRig() else vm.showRig=!vm.showRig}
                ToolOrb("↻",CyanBright,vm.tweenFrames.isNotEmpty()){vm.genTweens()}
                ToolOrb("◈",SlateGray,vm.showGallery){
                    if(vm.projects.isEmpty()) vm.saveProject(); vm.showGallery=!vm.showGallery
                }
                ToolOrb("💾",RoseRed,false){vm.saveProject()}
            }
        }

        // ═══ GALLERY PANEL (toggleable) ═══
        if (vm.showGallery) {
            Surface(modifier=Modifier.align(Alignment.Center).width(320.dp).heightIn(max=400.dp),shape=RoundedCornerShape(16.dp),color=DeepSlate,tonalElevation=12.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text("socreate Gallery",fontWeight=FontWeight.Bold,fontSize=14.sp,color=SlateGray)
                    Spacer(Modifier.height(8.dp))
                    if (vm.projects.isEmpty()) {
                        Text("No saved projects",fontSize=11.sp,color=TextMuted,modifier=Modifier.padding(vertical=24.dp))
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            itemsIndexed(vm.projects.takeLast(20)){idx,p->
                                Surface(onClick={vm.loadProject(idx);vm.showGallery=false},color=MidSurface,shape=RoundedCornerShape(8.dp),modifier=Modifier.fillMaxWidth().padding(vertical=2.dp)) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(p.name,fontWeight=FontWeight.SemiBold,fontSize=12.sp,color=TextPrimary)
                                        Text("${p.totalFrames}f @${p.fps}fps · ${p.layers.size} layers · ${p.keyframes.size} keys",fontSize=9.sp,color=TextMuted)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick={vm.saveProject()}){Text("💾 Save",fontSize=10.sp)}
                        TextButton(onClick={vm.exportGIF(ctx)}){Text("📤 Export",fontSize=10.sp)}
                        TextButton(onClick={vm.showGallery=false}){Text("Close",fontSize=10.sp)}
                    }
                }
            }
        }

        // ═══ LAYERS PANEL ═══
        if (vm.showLayers) {
            Surface(modifier=Modifier.align(Alignment.CenterStart).width(200.dp).fillMaxHeight(0.5f),shape=RoundedCornerShape(topEnd=16.dp,bottomEnd=16.dp),color=DeepSlate.copy(alpha=0.9f)) {
                Column(Modifier.padding(10.dp)) {
                    Text("LAYERS",fontSize=9.sp,fontWeight=FontWeight.Bold,color=EmeraldGreen,letterSpacing=1.sp)
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(vm.layers){idx,l->
                            Surface(onClick={vm.currentLayer=idx},color=if(idx==vm.currentLayer)EmeraldGreen.copy(alpha=0.15f) else Color.Transparent,shape=RoundedCornerShape(6.dp),modifier=Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(horizontal=6.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Text(if(l.visible)"●" else "○",fontSize=10.sp,color=if(l.visible)EmeraldGreen else TextMuted)
                                    Text(l.name,fontSize=10.sp,color=TextPrimary,modifier=Modifier.weight(1f).padding(start=6.dp))
                                    if(l.locked) Text("🔒",fontSize=8.sp)
                                }
                            }
                        }
                    }
                    TextButton(onClick={vm.showLayers=false}){Text("Close",fontSize=9.sp)}
                }
            }
        }

        // ═══ TOAST ═══
        if (vm.toastVisible) {
            Surface(modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=140.dp),shape=RoundedCornerShape(20.dp),color=MidSurface) {
                Text(vm.toastMsg,modifier=Modifier.padding(horizontal=16.dp,vertical=8.dp),fontSize=11.sp,color=TextPrimary)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// UI COMPONENTS
// ═══════════════════════════════════════════════════════════
@Composable
fun ToolOrb(icon: String, color: Color, active: Boolean, onClick: () -> Unit) {
    Box(modifier=Modifier.size(42.dp).shadow(if(active)10.dp else 4.dp,CircleShape).clip(CircleShape).background(color).clickable(onClick=onClick),contentAlignment=Alignment.Center) {
        Text(icon,fontSize=18.sp,color=Color.White)
    }
}
@Composable
fun ModKey(icon: String, label: String, color: Color, onClick: () -> Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(6.dp),color=MidSurface,modifier=Modifier.width(42.dp).height(36.dp)) {
        Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Text(icon,fontSize=13.sp,color=color); Text(label,fontSize=5.sp,color=TextMuted)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// DRAWING FUNCTIONS
// ═══════════════════════════════════════════════════════════
fun DrawScope.drawStroke(s: DrawStroke, opacity: Float, tint: Color) {
    if (s.pts.size<2) return
    val path=Path().apply{ moveTo(s.pts[0].x,s.pts[0].y); for(i in 1 until s.pts.size) lineTo(s.pts[i].x,s.pts[i].y) }
    val alpha=(opacity*s.opacity).coerceIn(0f,1f)
    if (s.tool=="ERASER") {
        drawPath(path,Color.White.copy(alpha=alpha),style=Stroke(width=s.size*2,cap=StrokeCap.Round,join=StrokeJoin.Round),blendMode=BlendMode.Clear)
    } else {
        val w=s.size; val hw=w*s.hardness
        // Hardness via inner stroke
        if (hw>0.5f && hw<w) {
            drawPath(path,tint.copy(alpha=alpha*0.5f),style=Stroke(width=w,cap=StrokeCap.Round,join=StrokeJoin.Round))
            drawPath(path,tint.copy(alpha=alpha),style=Stroke(width=hw,cap=StrokeCap.Round,join=StrokeJoin.Round))
        } else {
            drawPath(path,tint.copy(alpha=alpha),style=Stroke(width=w,cap=StrokeCap.Round,join=StrokeJoin.Round))
        }
    }
}
fun DrawScope.drawPerspectiveBox(w:Float,h:Float,depth:Float,vp:Pair<Float,Float>) {
    val cx=w*vp.first; val cy=h*vp.second
    val fgHalf=w*0.44f; val mgHalf=w*0.22f; val bgHalf=w*0.10f
    val fgY=h*0.88f; val mgY=h*0.60f; val bgY=h*0.10f
    val fl=cx-fgHalf; val fr=cx+fgHalf; val ml=cx-mgHalf; val mr=cx+mgHalf
    val bl=cx-bgHalf; val br=cx+bgHalf
    // Horizon
    drawLine(Color(0xFF404860),Offset(0f,cy),Offset(w,cy),1.5f)
    // Back wall
    val bw=Path().apply{ moveTo(bl,bgY); lineTo(br,bgY); lineTo(br,mgY); lineTo(bl,mgY); close() }
    drawPath(bw,Color(0xFF1a1e30).copy(alpha=0.45f)); drawPath(bw,Color(0xFF405070).copy(alpha=0.15f),style=Stroke(1.5f))
    // Side walls
    val lw=Path().apply{ moveTo(bl,bgY); lineTo(bl,mgY); lineTo(fl,fgY); lineTo(fl,mgY); close() }
    drawPath(lw,Color(0xFF222840).copy(alpha=0.35f)); drawPath(lw,Color(0xFF506080).copy(alpha=0.15f),style=Stroke(1.5f))
    val rw=Path().apply{ moveTo(br,bgY); lineTo(br,mgY); lineTo(fr,fgY); lineTo(fr,mgY); close() }
    drawPath(rw,Color(0xFF1e2438).copy(alpha=0.35f)); drawPath(rw,Color(0xFF506080).copy(alpha=0.15f),style=Stroke(1.5f))
    // Ground
    val g=Path().apply{ moveTo(fl,fgY); lineTo(fr,fgY); lineTo(mr,mgY); lineTo(ml,mgY); close() }
    drawPath(g,Color(0xFF1c2034).copy(alpha=0.3f)); drawPath(g,Color(0xFF505878).copy(alpha=0.12f),style=Stroke(1f))
    // Labels
    drawContext.canvas.nativeCanvas.drawText("FOREGROUND",fl+8,fgY-8,android.graphics.Paint().apply{color=0x30FFFFFF.toInt();textSize=24f;isFakeBoldText=true})
    drawContext.canvas.nativeCanvas.drawText("BACKGROUND",bl+8,bgY+20,android.graphics.Paint().apply{color=0x25FFFFFF.toInt();textSize=24f;isFakeBoldText=true})
    drawLine(Color(0xFF505878).copy(alpha=0.25f),Offset(cx,bgY),Offset(cx,fgY),1f)
    drawCircle(RoseRed.copy(alpha=0.5f),5f,Offset(cx,cy))
}
