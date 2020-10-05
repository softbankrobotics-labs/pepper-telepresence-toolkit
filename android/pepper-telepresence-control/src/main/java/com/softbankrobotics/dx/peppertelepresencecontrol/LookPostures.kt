package com.softbankrobotics.dx.peppertelepresencecontrol

import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.Animate
import com.aldebaran.qi.sdk.`object`.holder.AutonomousAbilitiesType
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import com.aldebaran.qi.sdk.builder.HolderBuilder

enum class RobotPose {
    DOWN_MAX,
    DOWN_HALF,
    MIDDLE,
    UP_HALF,
    UP_END,
    UP_TOOMUCH
}

internal class LookPostures(
    private val qiContext: QiContext,
    private val allowUnnaturalPoses: Boolean
) {

    companion object {
        private const val TAG = "LookPostures"
    }

    // The animations themselves
    private val backgroundMovementHolder = HolderBuilder.with(qiContext)
        .withAutonomousAbilities(
            AutonomousAbilitiesType.BACKGROUND_MOVEMENT
        )
        .build()

    // The animations themselves
    private val downMaxAnim = buildAnimate(R.raw.downwards_max)
    private val downHalfAnim = buildAnimate(R.raw.downwards_50)
    private val middleAnim = buildAnimate(R.raw.stand)
    private val upHalfAnim = buildAnimate(R.raw.upwards_25)
    private val upEndAnim = buildAnimate(R.raw.upwards_50)
    private val upTooMuchAnim = buildAnimate(R.raw.upwards_too_much)

    private fun setupBackgroundMovementForPose(pose: RobotPose): Future<Void> {
        Log.d(TAG, "Setting setupBackgroundMovementForPose to $pose")
        val future = if (pose == RobotPose.MIDDLE) {
            backgroundMovementHolder.async().release()
        } else {
            backgroundMovementHolder.async().hold()
        }
        future.thenConsume {
            if (it.hasError()) {
                Log.w(
                    TAG,
                    "Error in setupBackgroundMovementForPose to $pose: ${it.error}, message: ${it.errorMessage}"
                )
            } else {
                Log.d(TAG, "-> successfully setupBackgroundMovementForPose(pose)")
            }
        }
        return future
    }

    private fun buildAnimate(animResource: Int): Animate {
        val animation = AnimationBuilder.with(qiContext)
            .withResources(animResource)
            .build()
        return AnimateBuilder
            .with(qiContext)
            .withAnimation(animation)
            .build()
    }

    fun takePose(newPose: RobotPose): Future<Void> {
        return setupBackgroundMovementForPose(newPose)
            .thenCompose { getAnimateForPose(newPose).async().run() }
    }

    private fun getAnimateForPose(pose: RobotPose): Animate {
        return when (pose) {
            RobotPose.DOWN_MAX -> downMaxAnim
            RobotPose.DOWN_HALF -> downHalfAnim
            RobotPose.MIDDLE -> middleAnim
            RobotPose.UP_HALF -> upHalfAnim
            RobotPose.UP_END -> upEndAnim
            RobotPose.UP_TOOMUCH -> upTooMuchAnim
        }
    }

    fun nextPoseUpwards(currentPose: RobotPose): RobotPose {
        return when (currentPose) {
            RobotPose.DOWN_MAX -> RobotPose.DOWN_HALF
            RobotPose.DOWN_HALF -> RobotPose.MIDDLE
            RobotPose.MIDDLE -> RobotPose.UP_HALF
            RobotPose.UP_HALF -> RobotPose.UP_END
            RobotPose.UP_END -> {
                if (allowUnnaturalPoses) {
                    RobotPose.UP_TOOMUCH
                } else {
                    currentPose
                }
            }
            RobotPose.UP_TOOMUCH -> currentPose
        }
    }

    fun nextPoseDownwards(currentPose: RobotPose): RobotPose {
        return when (currentPose) {
            RobotPose.DOWN_MAX -> currentPose
            RobotPose.DOWN_HALF -> RobotPose.DOWN_MAX
            RobotPose.MIDDLE -> RobotPose.DOWN_HALF
            RobotPose.UP_HALF -> RobotPose.MIDDLE
            RobotPose.UP_END -> RobotPose.UP_HALF
            RobotPose.UP_TOOMUCH -> RobotPose.UP_END
        }
    }

    fun isPoseAwkward(pose: RobotPose): Boolean {
        return when (pose) {
            RobotPose.UP_HALF -> true
            RobotPose.UP_END -> true
            RobotPose.UP_TOOMUCH -> true
            else -> false
        }
    }
}
