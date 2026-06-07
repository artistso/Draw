package com.animationstudio.ai

import android.graphics.*
import com.animationstudio.engine.*
import kotlin.math.*

/**
 * AI-Assisted Character Rigging System
 *
 * Automatically detects body parts, creates bone structures,
 * and enables inverse kinematics for natural character animation.
 */
class CharacterRigger(private val modelManager: AIModelManager) {

    data class Rig(
        val bones: List<Bone>,
        val joints: List<Joint>,
        val ikChains: List<IKChain>,
        val controlPoints: List<ControlPoint>,
        val name: String = "Character Rig"
    )

    data class Bone(
        val id: String,
        val name: String,
        val parentId: String?,
        val from: Joint,
        val to: Joint,
        val length: Float,
        val rotation: Float = 0f,
        val stiffness: Float = 1f,
        val constraints: BoneConstraints = BoneConstraints()
    )

    data class Joint(
        val id: String,
        val name: String,
        val position: PointF,
        val radius: Float = 8f,
        val isEndpoint: Boolean = false
    )

    data class IKChain(
        val id: String,
        val name: String,
        val boneIds: List<String>,
        val target: PointF,
        val iterations: Int = 20,
        val tolerance: Float = 0.1f
    )

    data class ControlPoint(
        val id: String,
        val name: String,
        val position: PointF,
        val controlsBoneIds: List<String>,
        val type: ControlType
    )

    data class BoneConstraints(
        val minAngle: Float = -PI.toFloat(),
        val maxAngle: Float = PI.toFloat(),
        val minLength: Float = 0.5f,
        val maxLength: Float = 2f,
        val fixedPosition: Boolean = false
    )

    enum class ControlType {
        POSITION, ROTATION, SCALE, IK_TARGET
    }

    /**
     * Analyze a character drawing and create an automatic rig
     */
    fun autoRigFromDrawing(
        characterStrokes: List<StrokePath>,
        canvasWidth: Float,
        canvasHeight: Float
    ): Rig {
        val allPoints = characterStrokes.flatMap { it.path }

        // Step 1: Detect pose landmarks using simplified body part detection
        val landmarks = detectBodyLandmarks(allPoints, canvasWidth, canvasHeight)

        // Step 2: Build skeletal structure
        val (bones, joints) = buildSkeleton(landmarks)

        // Step 3: Create IK chains
        val ikChains = createIKChains(bones, joints)

        // Step 4: Generate control points
        val controlPoints = createControlPoints(joints, bones)

        return Rig(
            bones = bones,
            joints = joints,
            ikChains = ikChains,
            controlPoints = controlPoints,
            name = "Auto-Rigged Character"
        )
    }

    data class BodyLandmarks(
        val head: PointF?,
        val neck: PointF?,
        val leftShoulder: PointF?,
        val rightShoulder: PointF?,
        val leftElbow: PointF?,
        val rightElbow: PointF?,
        val leftWrist: PointF?,
        val rightWrist: PointF?,
        val spine: PointF?,
        val hip: PointF?,
        val leftHip: PointF?,
        val rightHip: PointF?,
        val leftKnee: PointF?,
        val rightKnee: PointF?,
        val leftAnkle: PointF?,
        val rightAnkle: PointF?
    )

    /**
     * Detect body landmarks using geometric heuristics
     * In production, this would use pose estimation ML model
     */
    private fun detectBodyLandmarks(
        points: List<PathPoint>,
        width: Float,
        height: Float
    ): BodyLandmarks {
        if (points.isEmpty()) return BodyLandmarks(null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null)

        // Cluster points and identify extremities
        val sortedX = points.map { it.x }.sorted()
        val sortedY = points.map { it.y }.sorted()

        val cx = width / 2
        val cy = height / 2
        val topY = sortedY.first()
        val bottomY = sortedY.last()
        val leftX = sortedX.first()
        val rightX = sortedX.last()

        // Heuristic body proportion detection
        val bodyHeight = bottomY - topY
        val bodyWidth = rightX - leftX

        // Head is the topmost cluster
        val headY = topY + bodyHeight * 0.08f
        val headX = cx

        // Shoulders at ~15% from top
        val shoulderY = topY + bodyHeight * 0.15f

        // Elbows at ~35% from top
        val elbowY = topY + bodyHeight * 0.35f

        // Wrists at ~50% from top (or at side extremities)
        val wristY = topY + bodyHeight * 0.5f

        // Spine center
        val spineY = topY + bodyHeight * 0.25f

        // Hips at ~55% 
        val hipY = topY + bodyHeight * 0.55f

        // Knees at ~75%
        val kneeY = topY + bodyHeight * 0.75f

        // Ankles at bottom
        val ankleY = bottomY

        // Get actual closest points for each landmark
        return BodyLandmarks(
            head = findClosestPoint(points, headX, headY),
            neck = findClosestPoint(points, cx, shoulderY - bodyHeight * 0.03f),
            leftShoulder = findClosestPoint(points, leftX + bodyWidth * 0.1f, shoulderY),
            rightShoulder = findClosestPoint(points, rightX - bodyWidth * 0.1f, shoulderY),
            leftElbow = findClosestPoint(points, leftX + bodyWidth * 0.05f, elbowY),
            rightElbow = findClosestPoint(points, rightX - bodyWidth * 0.05f, elbowY),
            leftWrist = findClosestPoint(points, leftX + bodyWidth * 0.02f, wristY),
            rightWrist = findClosestPoint(points, rightX - bodyWidth * 0.02f, wristY),
            spine = findClosestPoint(points, cx, spineY),
            hip = findClosestPoint(points, cx, hipY),
            leftHip = findClosestPoint(points, cx - bodyWidth * 0.12f, hipY),
            rightHip = findClosestPoint(points, cx + bodyWidth * 0.12f, hipY),
            leftKnee = findClosestPoint(points, cx - bodyWidth * 0.1f, kneeY),
            rightKnee = findClosestPoint(points, cx + bodyWidth * 0.1f, kneeY),
            leftAnkle = findClosestPoint(points, cx - bodyWidth * 0.08f, ankleY),
            rightAnkle = findClosestPoint(points, cx + bodyWidth * 0.08f, ankleY)
        )
    }

    private fun findClosestPoint(points: List<PathPoint>, x: Float, y: Float): PointF {
        return points.minByOrNull {
            (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y)
        }?.let { PointF(it.x, it.y) } ?: PointF(x, y)
    }

    /**
     * Build a complete skeleton from landmarks
     */
    private fun buildSkeleton(landmarks: BodyLandmarks): Pair<List<Bone>, List<Joint>> {
        val joints = mutableListOf<Joint>()
        val bones = mutableListOf<Bone>()
        var jointId = 0

        fun addJoint(name: String, point: PointF?, endpoint: Boolean = false): Joint? {
            if (point == null) return null
            val joint = Joint("j${jointId++}", name, point, isEndpoint = endpoint)
            joints.add(joint)
            return joint
        }

        // Create all joints
        val head = addJoint("Head", landmarks.head, true)
        val neck = addJoint("Neck", landmarks.neck)
        val lShoulder = addJoint("Left Shoulder", landmarks.leftShoulder)
        val rShoulder = addJoint("Right Shoulder", landmarks.rightShoulder)
        val lElbow = addJoint("Left Elbow", landmarks.leftElbow)
        val rElbow = addJoint("Right Elbow", landmarks.rightElbow)
        val lWrist = addJoint("Left Wrist", landmarks.leftWrist, true)
        val rWrist = addJoint("Right Wrist", landmarks.rightWrist, true)
        val spine = addJoint("Spine", landmarks.spine)
        val hip = addJoint("Hip", landmarks.hip)
        val lHip = addJoint("Left Hip", landmarks.leftHip)
        val rHip = addJoint("Right Hip", landmarks.rightHip)
        val lKnee = addJoint("Left Knee", landmarks.leftKnee)
        val rKnee = addJoint("Right Knee", landmarks.rightKnee)
        val lAnkle = addJoint("Left Ankle", landmarks.leftAnkle, true)
        val rAnkle = addJoint("Right Ankle", landmarks.rightAnkle, true)

        // Create bones connecting joints
        fun addBone(name: String, from: Joint?, to: Joint?, parentId: String? = null,
                    stiffness: Float = 1f, constraints: BoneConstraints = BoneConstraints()
        ): Bone? {
            if (from == null || to == null) return null
            val dx = to.position.x - from.position.x
            val dy = to.position.y - from.position.y
            val length = sqrt(dx * dx + dy * dy)
            val bone = Bone(
                id = "b${bones.size}",
                name = name,
                parentId = parentId,
                from = from,
                to = to,
                length = length,
                stiffness = stiffness,
                constraints = constraints
            )
            bones.add(bone)
            return bone
        }

        // Spine chain
        val spineBone = addBone("Spine", hip, spine)
        val neckBone = addBone("Neck", spine, neck, spineBone?.id)

        // Head
        addBone("Head", neck, head, neckBone?.id,
            constraints = BoneConstraints(minAngle = -PI.toFloat() / 4, maxAngle = PI.toFloat() / 4))

        // Shoulders
        val lClavicle = addBone("Left Clavicle", neck, lShoulder, neckBone?.id)
        val rClavicle = addBone("Right Clavicle", neck, rShoulder, neckBone?.id)

        // Arms
        val lUpperArm = addBone("Left Upper Arm", lShoulder, lElbow, lClavicle?.id,
            constraints = BoneConstraints(minAngle = -PI.toFloat() * 0.7f, maxAngle = PI.toFloat() * 0.3f))
        val rUpperArm = addBone("Right Upper Arm", rShoulder, rElbow, rClavicle?.id,
            constraints = BoneConstraints(minAngle = -PI.toFloat() * 0.3f, maxAngle = PI.toFloat() * 0.7f))
        addBone("Left Forearm", lElbow, lWrist, lUpperArm?.id,
            constraints = BoneConstraints(minAngle = 0f, maxAngle = PI.toFloat() * 0.8f))
        addBone("Right Forearm", rElbow, rWrist, rUpperArm?.id,
            constraints = BoneConstraints(minAngle = 0f, maxAngle = PI.toFloat() * 0.8f))

        // Legs
        val lThigh = addBone("Left Thigh", lHip, lKnee, null,
            constraints = BoneConstraints(minAngle = -PI.toFloat() * 0.15f, maxAngle = PI.toFloat() * 0.7f))
        val rThigh = addBone("Right Thigh", rHip, rKnee, null,
            constraints = BoneConstraints(minAngle = -PI.toFloat() * 0.15f, maxAngle = PI.toFloat() * 0.7f))
        addBone("Left Shin", lKnee, lAnkle, lThigh?.id,
            constraints = BoneConstraints(minAngle = PI.toFloat() * 0.3f, maxAngle = PI.toFloat()))
        addBone("Right Shin", rKnee, rAnkle, rThigh?.id,
            constraints = BoneConstraints(minAngle = PI.toFloat() * 0.3f, maxAngle = PI.toFloat()))

        return Pair(bones, joints)
    }

    /**
     * Create Inverse Kinematics chains for limbs
     */
    private fun createIKChains(bones: List<Bone>, joints: List<Joint>): List<IKChain> {
        val chains = mutableListOf<IKChain>()

        // Arm IK chains
        val lArmBones = bones.filter { it.name.contains("Left") && 
            (it.name.contains("Arm") || it.name.contains("Forearm")) }
        if (lArmBones.size >= 2) {
            val target = joints.find { it.name == "Left Wrist" }
            if (target != null) {
                chains.add(IKChain("ik_l_arm", "Left Arm IK", 
                    lArmBones.map { it.id }, target.position))
            }
        }

        val rArmBones = bones.filter { it.name.contains("Right") && 
            (it.name.contains("Arm") || it.name.contains("Forearm")) }
        if (rArmBones.size >= 2) {
            val target = joints.find { it.name == "Right Wrist" }
            if (target != null) {
                chains.add(IKChain("ik_r_arm", "Right Arm IK",
                    rArmBones.map { it.id }, target.position))
            }
        }

        // Leg IK chains
        val lLegBones = bones.filter { it.name.contains("Left") && 
            (it.name.contains("Thigh") || it.name.contains("Shin")) }
        if (lLegBones.size >= 2) {
            val target = joints.find { it.name == "Left Ankle" }
            if (target != null) {
                chains.add(IKChain("ik_l_leg", "Left Leg IK",
                    lLegBones.map { it.id }, target.position))
            }
        }

        val rLegBones = bones.filter { it.name.contains("Right") && 
            (it.name.contains("Thigh") || it.name.contains("Shin")) }
        if (rLegBones.size >= 2) {
            val target = joints.find { it.name == "Right Ankle" }
            if (target != null) {
                chains.add(IKChain("ik_r_leg", "Right Leg IK",
                    rLegBones.map { it.id }, target.position))
            }
        }

        return chains
    }

    private fun createControlPoints(joints: List<Joint>, bones: List<Bone>): List<ControlPoint> {
        return joints.filter { it.isEndpoint }.map { joint ->
            val controllingBones = bones.filter { it.to.id == joint.id }.map { it.id }
            ControlPoint(
                id = "cp_${joint.id}",
                name = "${joint.name} Control",
                position = joint.position,
                controlsBoneIds = controllingBones,
                type = ControlType.IK_TARGET
            )
        }
    }

    /**
     * Solve Inverse Kinematics using CCD (Cyclic Coordinate Descent)
     */
    fun solveIK(
        chain: IKChain,
        bones: List<Bone>,
        joints: MutableList<Joint>,
        target: PointF,
        maxIterations: Int = 20
    ): List<Joint> {
        val chainBones = chain.boneIds.mapNotNull { id -> bones.find { it.id == id } }
        if (chainBones.isEmpty()) return joints

        val jointMap = joints.associateBy { it.id }.toMutableMap()

        for (iteration in 0 until maxIterations) {
            // Process from end effector to root
            for (bone in chainBones.reversed()) {
                val effector = jointMap[bone.to.id] ?: continue
                val base = jointMap[bone.from.id] ?: continue

                val toEnd = PointF(
                    effector.position.x - base.position.x,
                    effector.position.y - base.position.y
                )
                val toTarget = PointF(
                    target.x - base.position.x,
                    target.y - base.position.y
                )

                val endLen = sqrt(toEnd.x * toEnd.x + toEnd.y * toEnd.y)
                val targetLen = sqrt(toTarget.x * toTarget.x + toTarget.y * toTarget.y)

                if (endLen < 0.001f || targetLen < 0.001f) continue

                // Calculate rotation angle
                val cosAngle = (toEnd.x * toTarget.x + toEnd.y * toTarget.y) / (endLen * targetLen)
                val angle = acos(cosAngle.coerceIn(-1f, 1f))

                // Determine rotation direction
                val cross = toEnd.x * toTarget.y - toEnd.y * toTarget.x
                val rotation = if (cross > 0) angle else -angle

                // Apply constrained rotation
                val constrainedRotation = rotation.coerceIn(
                    bone.constraints.minAngle,
                    bone.constraints.maxAngle
                )

                // Rotate all child joints
                val children = getChildJointIds(bone.to.id, bones)
                for (childId in children) {
                    jointMap[childId]?.let { child ->
                        val relX = child.position.x - base.position.x
                        val relY = child.position.y - base.position.y
                        val cosR = cos(constrainedRotation)
                        val sinR = sin(constrainedRotation)
                        val rotatedJoint = child.copy(
                            position = PointF(
                                base.position.x + relX * cosR - relY * sinR,
                                base.position.y + relX * sinR + relY * cosR
                            )
                        )
                        jointMap[childId] = rotatedJoint
                    }
                }
            }

            // Check convergence
            val endEffector = jointMap[chainBones.last().to.id]
            if (endEffector != null) {
                val dist = PointF(
                    target.x - endEffector.position.x,
                    target.y - endEffector.position.y
                )
                val error = sqrt(dist.x * dist.x + dist.y * dist.y)
                if (error < 0.5f) break
            }
        }

        return jointMap.values.toList()
    }

    private fun getChildJointIds(parentJointId: String, bones: List<Bone>): Set<String> {
        val result = mutableSetOf<String>()
        val queue = mutableListOf(parentJointId)
        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            for (bone in bones) {
                if (bone.from.id == current) {
                    result.add(bone.to.id)
                    queue.add(bone.to.id)
                }
            }
        }
        return result
    }

    /**
     * Apply a rig pose to deform attached drawing strokes
     */
    fun applyPose(
        rig: Rig,
        strokes: List<StrokePath>,
        joints: List<Joint>
    ): List<StrokePath> {
        // Build transformation map from original to posed joint positions
        val transforms = mutableMapOf<String, Pair<PointF, Float>>()

        for (joint in joints) {
            val originalJoint = rig.joints.find { it.id == joint.id }
            if (originalJoint != null) {
                val dx = joint.position.x - originalJoint.position.x
                val dy = joint.position.y - originalJoint.position.y
                transforms[joint.id] = Pair(PointF(dx, dy), 0f)
            }
        }

        // Apply transforms to strokes based on bone weights
        return strokes.map { stroke ->
            val newPath = stroke.path.map { point ->
                // Find influencing bones and blend transforms
                var totalDx = 0f
                var totalDy = 0f
                var totalWeight = 0f

                for (bone in rig.bones) {
                    val weight = computeBoneWeight(point, bone.from.position, bone.to.position)
                    if (weight > 0.01f) {
                        val transform = transforms[bone.to.id]
                        if (transform != null) {
                            totalDx += transform.first.x * weight
                            totalDy += transform.first.y * weight
                            totalWeight += weight
                        }
                    }
                }

                if (totalWeight > 0f) {
                    PathPoint(
                        x = point.x + totalDx / totalWeight,
                        y = point.y + totalDy / totalWeight,
                        pressure = point.pressure,
                        tilt = point.tilt
                    )
                } else point
            }
            stroke.copy(path = newPath)
        }
    }

    private fun computeBoneWeight(point: PathPoint, boneFrom: PointF, boneTo: PointF): Float {
        val px = point.x
        val py = point.y

        val ax = boneFrom.x
        val ay = boneFrom.y
        val bx = boneTo.x
        val by = boneTo.y

        val abx = bx - ax
        val aby = by - ay
        val abLenSq = abx * abx + aby * aby

        if (abLenSq < 0.001f) {
            val dx = px - ax
            val dy = py - ay
            val distSq = dx * dx + dy * dy
            return if (distSq < 2500f) 1f else 0f
        }

        // Project point onto bone segment
        var t = ((px - ax) * abx + (py - ay) * aby) / abLenSq
        t = t.coerceIn(0f, 1f)

        val projX = ax + t * abx
        val projY = ay + t * aby

        val distSq = (px - projX) * (px - projX) + (py - projY) * (py - projY)

        // Gaussian weight based on distance to bone
        val sigma = 50f
        return exp(-distSq / (2 * sigma * sigma))
    }
}