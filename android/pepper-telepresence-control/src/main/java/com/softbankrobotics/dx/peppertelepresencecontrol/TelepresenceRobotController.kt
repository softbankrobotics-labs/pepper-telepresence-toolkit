package com.softbankrobotics.dx.peppertelepresencecontrol

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.*
import com.aldebaran.qi.sdk.`object`.geometry.Quaternion
import com.aldebaran.qi.sdk.`object`.geometry.Vector3
import com.aldebaran.qi.sdk.`object`.holder.AutonomousAbilitiesType
import com.aldebaran.qi.sdk.`object`.power.FlapSensor
import com.aldebaran.qi.sdk.builder.*
import com.aldebaran.qi.sdk.util.FutureUtils
import com.softbankrobotics.dx.peppertelepresencecontrol.TelepresenceRobotController.LookAtState.*
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2


@SuppressLint("LongLogTag")
class TelepresenceRobotController(
    private val qiContext: QiContext,
    val lookDownWhenMoving: Boolean = false
) {

    private val trajectoryMaker = CachedTrajectoryMaker(qiContext)

    companion object {
        private const val TAG = "TelepresenceRobotController"
        private const val ALLOW_UNNATURAL_POSES = false
        private const val AWKWARDNESS_TIMEOUT_SECONDS: Long = 60
        private const val CAM_HALF_WIDTH_RAD = (PI / 4.0) * 0.66 // 45°
        private const val GOTO_CANCELLATION_TIMEOUT = 600L
        private const val STRAFING_CANCELLATION_TIMEOUT = 600L
    }

    private val basicAwarenessHolder = HolderBuilder.with(qiContext)
        .withAutonomousAbilities(AutonomousAbilitiesType.BASIC_AWARENESS)
        .build()
    private val backgroundMovementHolder = HolderBuilder.with(qiContext)
        .withAutonomousAbilities(AutonomousAbilitiesType.BACKGROUND_MOVEMENT)
        .build()

    private var currentPose = RobotPose.MIDDLE
    private var takePoseFuture: Future<Void> = Future.of(null)
    private val lookPostures = LookPostures(qiContext, ALLOW_UNNATURAL_POSES)
    // Awkward pose management
    private var isInAwkwardPose = false
    private var awkwardTimerFuture: Future<Void>? = null

    // Used to get charging flap state in case of movement errors
    private val chargingFlap: FlapSensor = qiContext.power.chargingFlap

    private var goToFuture: Future<Void>? = null
    private var goTo: GoTo
    private val goToTargetFrame : AttachedFrame
    private val robotFrame = qiContext.actuation.robotFrame()
    // Strafing
    private var targetX = 0.0
    private var targetY = 0.0
    private var strafingFuture: Future<Void>? = null
    // Look at
    private var lookDownLookAtFuture: Future<Void>? = null
    private var lookDownLookAt: LookAt
    private var gazeFrame = qiContext.actuation.gazeFrame()
    private var lookAtTargetFrame = qiContext.mapping.makeFreeFrame()
    private var lookAt = LookAtBuilder.with(qiContext)
        .withFrame(lookAtTargetFrame.frame())
        .build()
    private var lookAtFuture: Future<Void>? = null
    enum class LookAtState {
        UNCONSTRAINED, LOOKING_DOWN, FREE_LOOKAT, TRANSITION
    }
    private var lookAtState : LookAtState = UNCONSTRAINED

    init {
        val transform = TransformBuilder.create().fromXTranslation(1.0)
        goToTargetFrame = robotFrame.makeAttachedFrame(transform)
        goTo = GoToBuilder.with(qiContext)
            .withFrame(goToTargetFrame.frame())
            .withPathPlanningPolicy(PathPlanningPolicy.STRAIGHT_LINES_ONLY)
            .build()
        goTo.addOnStartedListener {
            Log.d(TAG, "GoTo started")
        }
        lookDownLookAt = LookAtBuilder.with(qiContext)
            .withFrame(goToTargetFrame.frame())
            .build()
        lookDownLookAt.policy = LookAtMovementPolicy.HEAD_ONLY
    }

    private val moveCancellationHandler = Handler(Looper.getMainLooper())
    private val moveCancellationRunnable = Runnable {
        cancelAllMovements()
        targetX = 0.0
        targetY = 0.0
        Log.d(TAG, "Movements cancelled after timeout")
    }

    /**********************
     * Lifecycle
     **********************/

    fun start() {
        Log.d(TAG, "Basic awareness: holding...")
        basicAwarenessHolder.async().hold()
        updateLookAtTarget(0.0, 0.0)
        Log.d(TAG, "... held.")
    }

    fun stop() {
        Log.d(TAG, "Basic awareness: releasing...")
        basicAwarenessHolder.async().release()
        Log.d(TAG, "... released. Resetting the rest...")
        goTo.removeAllOnStartedListeners()
        goToFuture?.requestCancellation()
        goToFuture = null
        lookAtFuture?.requestCancellation()
        lookAtFuture = null
        strafingFuture?.requestCancellation()
        strafingFuture = null
        setLookAtState(UNCONSTRAINED) // WIll stop everything
        takePose(RobotPose.MIDDLE)
        Log.d(TAG, "... reset.")
    }


    /**********************
     * Movement Helpers
     **********************/

    private fun cancelLookAt() {
        maybeCancel(lookAtFuture)
    }


    private fun maybeCancel(future : Future<Void>?) : Future<Void> {
        return future?.also { it.cancel(true) } ?: Future.of(null) as Future<Void>
    }

    private fun setLookAtState(newState : LookAtState) : Future<Void> {
        if (newState != lookAtState) {
            val oldState = lookAtState
            // Hold background movements
            Log.d(TAG, "Transition from $lookAtState to $newState; starting")
            return backgroundMovementHolder.async().hold()
                .thenCompose {fut ->
                    // Stop the previous lookat, if necessary
                    when (lookAtState) {
                        LOOKING_DOWN -> maybeCancel(lookDownLookAtFuture)
                        FREE_LOOKAT -> maybeCancel(lookAtFuture)
                        else -> Future.of(null)
                    } as Future<Void>
                }.thenCompose {fut ->
                    when(newState) {
                        LOOKING_DOWN -> {
                            // TODO: maybe check if null or done
                            lookDownLookAtFuture?.requestCancellation()
                            lookDownLookAtFuture = lookDownLookAt.async().run().also {
                                logFutureWhenFinished("Look down", it) // NB this also logs the hatch state
                            }
                        }
                        FREE_LOOKAT -> {
                            if (lookAtFuture?.isDone != false) { // Null, or done
                                lookAtFuture?.requestCancellation()
                                lookAtFuture = lookAt.async().run()
                            }
                        }
                        else -> {}
                    }
                    if (newState != TRANSITION) {
                        backgroundMovementHolder.async().release()
                    } else {
                        Future.of(null)
                    }  as Future<Void>
                }.thenConsume {fut ->
                    lookAtState = newState
                    val message = when {
                        fut.isSuccess ->  "Finished setting state to $newState"
                        fut.hasError() -> "Finished setting state to $newState with error ${fut.errorMessage}"
                        else -> "Finished setting state to $newState; cancelled: {${fut.isCancelled}"
                    }
                    Log.d(TAG, message)
                }
        } else {
            // Same state, ignore this
            return Future.of(null)
        }
    }


    /**********************
     * Advance control
     **********************/

    private fun cancelAllMovements() {
        strafingFuture?.cancel(true)
        goToFuture?.cancel(true)
    }

    private fun getYawFromQuaternion(q: Quaternion): Double {
        // yaw (z-axis rotation)
        val x = q.x
        val y = q.y
        val z = q.z
        val w = q.w
        val sinYaw = 2.0 * (w * z + x * y)
        val cosYaw = 1.0 - 2.0 * (y * y + z * z)
        return atan2(sinYaw, cosYaw)
    }

    private fun moveForwards() : Future<Void> {
        if (currentPose != RobotPose.MIDDLE) {
            Log.d(TAG, "Pose is $currentPose, first resetting to middle")
            // First cancel the current move
            return takePose(RobotPose.MIDDLE).thenCompose {
                // then try again
                moveForwards()
            }
        } else {
            if (goToFuture?.isDone == true) {
                goToFuture = null
            }
            strafingFuture?.requestCancellation()
            Log.d(TAG, "Considering starting a GoTo")
            val newGoToFuture : Future<Void> = goToFuture ?: run {
                Log.d(TAG, "GoTo needs to be run")
                setLookAtState(TRANSITION)
                    .andThenCompose {
                        gazeFrame.async().computeTransform(robotFrame)
                    }
                    .andThenCompose { robotToGaze ->
                        val yaw = getYawFromQuaternion(robotToGaze.transform.rotation)
                        if (abs(yaw) < 0.05) {
                            setLookAtState(LOOKING_DOWN)
                                .andThenCompose {
                                    goTo.async().run().also {
                                        logFutureWhenFinished("GoTo", it)
                                    }
                                }
                        } else {
                            turnInRadians(yaw)
                                // Note, at this point startLookDown is in progress
                                .andThenCompose {
                                    setLookAtState(LOOKING_DOWN)
                                }
                                .andThenCompose {
                                    goTo.async().run().also {
                                        logFutureWhenFinished("GoTo", it)
                                    }
                            }
                        }
                    }
            }

            moveCancellationHandler.removeCallbacksAndMessages(null)
            moveCancellationHandler.postDelayed(moveCancellationRunnable, GOTO_CANCELLATION_TIMEOUT)
            Log.d(TAG, "Cancelling GoTo in ${GOTO_CANCELLATION_TIMEOUT}ms")
            goToFuture = newGoToFuture
            return newGoToFuture
        }
    }

    /**********************
     * Tablet Look At Control
     **********************/

    private fun tabletLookAt(rawX: Double, rawY: Double) : Future<Void> {
        val x = rawX.coerceIn(-1.0, 1.0)
        val y = rawY.coerceIn(-1.0, 1.0)
        setLookAtState(UNCONSTRAINED)
        turnInRadians(CAM_HALF_WIDTH_RAD * -x)
        val isMostlyVertical = abs(y) > abs(x)
        return verticalLookAt(-y, isMostlyVertical)
    }

    private fun turnInRadians(theta: Double) : Future<Void> {
        return trajectoryMaker.makeTurnTrajectory(theta)
            .andThenCompose { animate ->
                animate.async().run()
            }
    }

    private fun verticalLookAt(yR: Double, isMostlyVertical: Boolean) : Future<Void> {
        val threshold = if (isMostlyVertical) 0.3 else 0.6
        return when {
            yR > threshold -> takeNextPoseDownwards()
            yR < -threshold -> takeNextPoseUpwards()
            else -> Future.of(null)
        }
    }


    /**********************
     * Head Look At Control
     **********************/

    private fun updateLookAtTarget(x: Double, y: Double) : Future<Void> {
        // Define target coordinates
        val targetX = 10.0
        val targetY = -x * 5
        val targetZ = y * 3
        val targetVector3 = Vector3(targetX, targetY, targetZ)
        val targetTransform = TransformBuilder.create().fromTranslation(targetVector3)
        return lookAtTargetFrame.async().update(gazeFrame, targetTransform, 0L)

    }

    private fun headLookAt(rawX: Double, rawY: Double) : Future<Void> {
        val x = rawX.coerceIn(-1.0, 1.0)
        val y = rawY.coerceIn(-1.0, 1.0)
        return updateLookAtTarget(x, y)
            .andThenCompose { setLookAtState(FREE_LOOKAT) }
    }


    /*******************
     * Pose management
     *******************/

    private fun takePose(newPose: RobotPose): Future<Void> {
        if (newPose != currentPose) {
            Log.d(TAG, "Pose change $currentPose -> $newPose")
            currentPose = newPose
            takePoseFuture.requestCancellation()
            takePoseFuture = takePoseFuture
                .thenCompose { lookPostures.takePose(newPose) }
            checkPoseForAwkwardness(newPose)
        }
        return takePoseFuture
    }

    private fun takeNextPoseUpwards() : Future<Void> {
        return takePose(lookPostures.nextPoseUpwards(currentPose))
    }

    private fun takeNextPoseDownwards() : Future<Void> {
        return takePose(lookPostures.nextPoseDownwards(currentPose))
    }


    /*******************
     * Awkward pose
     *******************/

    private fun checkPoseForAwkwardness(newPose: RobotPose) {
        val newPoseIsAwkward = lookPostures.isPoseAwkward(newPose)
        if (isInAwkwardPose != newPoseIsAwkward) {
            if (newPoseIsAwkward) {
                Log.d(TAG, "Starting awkward pose timeout")
                awkwardTimerFuture = FutureUtils.wait(AWKWARDNESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .andThenCompose {
                        Log.d(TAG, "Awkward pose timeout, returning to middle")
                        takePose(RobotPose.MIDDLE)
                    }
            } else {
                awkwardTimerFuture?.requestCancellation()
            }
            isInAwkwardPose = newPoseIsAwkward
        }
    }

    /**********************
     * Strafing
     **********************/

    private fun logFutureWhenFinished(actionName : String, future: Future<Void>) {
        future.thenConsume {
            when {
                it.isDone -> {
                    Log.d(TAG, "$actionName done")
                }
                it.isCancelled -> {
                    Log.d(TAG, "$actionName cancelled")
                }
                it.hasError() -> {
                    Log.w(TAG, "$actionName error: ${it.errorMessage}")
                    if (chargingFlap.state.open) {
                        Log.w(TAG, "Charging flap is opened")
                    }
                }
            }
        }
    }


    private fun strafe(rawX: Double, rawY: Double) : Future<Void> {
        val x = rawX.coerceIn(-1.0, 1.0)
        val y = rawY.coerceIn(-1.0, 1.0)
        if (targetX != x || targetY != y) {
            targetX = x
            targetY = y
            setLookAtState(LOOKING_DOWN)

            cancelAllMovements()

            strafingFuture = trajectoryMaker.makeStrafeTrajectory(targetX, targetY)
                .andThenCompose { animate ->
                    animate.addOnStartedListener {
                        Log.d(TAG, "Strafing started")
                    }
                    animate.async().run()
                }.also {
                    logFutureWhenFinished("Strafing", it)
                }
        }
        moveCancellationHandler.removeCallbacksAndMessages(null)
        moveCancellationHandler.postDelayed(moveCancellationRunnable, STRAFING_CANCELLATION_TIMEOUT)
        Log.d(TAG, "Cancelling strafing in ${STRAFING_CANCELLATION_TIMEOUT}ms")
        return strafingFuture ?: run {
            val fut : Future<Void> = Future.of(null)
            fut
        }
    }

    /**********************
     * Turn
     **********************/

    private fun turnInDegrees(angleInDegrees: Double) : Future<Void> {
        // Max rotation is one full turn in either direction
        val clampedAngle = angleInDegrees.coerceIn(-360.0, 360.0)
        // Convert angle from deg to rad
        val angleRad = clampedAngle / 180 * PI
        return setLookAtState(UNCONSTRAINED)
            .thenCompose { turnInRadians(angleRad) }
    }


    /**********************
     * Commands management
     **********************/

    fun handleJsonCommand(command: Command) : Future<Void> {
        Log.d(TAG, "handleJsonCommand: $command")
        return when (command.name) {
            "head_look_at" -> headLookAt(command.args[0], command.args[1])
            "tablet_look_at" -> tabletLookAt(command.args[0], command.args[1])
            "turn" -> turnInDegrees(command.args[0])
            "move_forward" -> moveForwards()
            "strafe" -> strafe(command.args[0], command.args[1])
            else -> throw Exception("Unknown command: ${command.name}")
        }
    }

    fun returnToDefaultPose() {
        takePose(RobotPose.MIDDLE).thenCompose {
            setLookAtState(UNCONSTRAINED)
        }
    }

    data class Command(
        val name: String,
        val args: List<Double>
    )
}
