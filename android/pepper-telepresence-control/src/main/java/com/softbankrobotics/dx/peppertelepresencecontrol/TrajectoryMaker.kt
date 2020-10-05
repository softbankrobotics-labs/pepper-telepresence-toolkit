package com.softbankrobotics.dx.peppertelepresencecontrol

import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.Animate
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

class TrajectoryMaker(val qiContext : QiContext) {
    fun makeStrafeTrajectory(x: Double, y: Double) : Future<Animate> {
        val animationString =  "[\"Holonomic\", [\"Line\", [${10 * y}, ${-10 * x}]], 0.0, 80.0]"
        return AnimationBuilder.with(qiContext).withTexts(animationString).buildAsync()
            .andThenCompose { animation ->
                AnimateBuilder.with(qiContext).withAnimation(animation).buildAsync()
            }
    }

    fun makeTurnTrajectory(thetaInRadians: Double): Future<Animate> {
        val timeMax = 5.0
        val angleMax = Math.PI
        val duration = abs(thetaInRadians) / angleMax * timeMax
        val animationString = "[\"Holonomic\", [\"Line\", [0, 0]], $thetaInRadians, $duration]"
        return AnimationBuilder.with(qiContext).withTexts(animationString).buildAsync()
            .andThenCompose { animation ->
                AnimateBuilder.with(qiContext).withAnimation(animation).buildAsync()
            }
    }
}


class CachedTrajectoryMaker(qiContext : QiContext) {
    private val maker = TrajectoryMaker(qiContext)

    private val cachedStrafes = HashMap<Pair<Double, Double>, Future<Animate>>()
    private val cachedTurns = HashMap<Double, Future<Animate>>()

    private fun roundAngle(angleInRadians : Double, numSegments : Int) : Double {
        // Round to nearest
        val segmentAngle = 2 * PI / numSegments
        val segment = (angleInRadians / segmentAngle).roundToInt()
        return segment * segmentAngle
    }

    fun makeStrafeTrajectory(x: Double, y: Double) : Future<Animate> {
        val key = Pair(x, y)
        return cachedStrafes[key] ?:
            maker.makeStrafeTrajectory(x, y)
                .also {
                    cachedStrafes[key] = it
                }
    }

    fun makeTurnTrajectory(thetaInRadians: Double): Future<Animate> {
        // We round the angle, otherwise, the cache may grow explosively large.
        val roundedTheta = roundAngle(thetaInRadians, 72)
        return cachedTurns[roundedTheta] ?:
            maker.makeTurnTrajectory(roundedTheta)
                .also {
                    cachedTurns[roundedTheta] = it
                }
    }

}