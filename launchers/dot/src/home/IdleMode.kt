package dev.mrrosoff.tv.dot.home

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import dev.mrrosoff.tv.dot.dots.DotFieldView
import kotlin.random.Random

/**
 * After [IDLE_AFTER_MS] untouched on Home, the wheel and everything but the clock fade away, the clock glides
 * to the middle and grows, and the dot field gets brighter and quicker. While idle the clock drifts a little, for the OLED.
 * Any button wakes Home again; [MainActivity] swallows that press so it only wakes.
 */
class IdleMode(
    private val activity: Activity,
    private val handler: Handler,
    private val field: DotFieldView,
    private val wheel: View,
    private val glow: View,
    private val clock: View,
    /** Fade out in this order, and back in the same order. */
    private val others: List<View>,
    /** Whether Home is in a state to go idle (the wheel focused, no menu open). */
    private val allowed: () -> Boolean,
) {
    var idle = false
        private set

    private var homeX = 0f
    private var homeY = 0f
    private var centreX = 0f
    private var centreY = 0f
    private var wheelX = 0f
    private var energy: ValueAnimator? = null

    private val enter = Runnable { if (allowed()) enter() else touch() }
    private val drift = Runnable { drift() }

    /** Something happened on Home: start the countdown again. */
    fun touch() {
        handler.removeCallbacks(enter)
        if (!idle) handler.postDelayed(enter, IDLE_AFTER_MS)
    }

    /** Home is going away: no idle, and back to normal at once if idle. */
    fun stop() {
        handler.removeCallbacks(enter)
        handler.removeCallbacks(drift)
        if (!idle) return
        idle = false
        energy?.cancel()
        field.energy = 0f
        clock.animate().cancel()
        clock.translationX = homeX
        clock.translationY = homeY
        clock.scaleX = 1f
        clock.scaleY = 1f
        wheel.animate().cancel()
        wheel.alpha = 1f
        wheel.translationX = wheelX
        glow.animate().cancel()
        glow.alpha = 1f
        others.forEach {
            it.animate().cancel()
            it.alpha = 1f
            it.translationY = 0f
        }
    }

    fun wake() {
        if (!idle) return
        idle = false
        handler.removeCallbacks(drift)
        clock.animate().translationX(homeX).translationY(homeY).scaleX(1f).scaleY(1f)
            .setStartDelay(0).setDuration(560).setInterpolator(SETTLE).start()
        wheel.animate().alpha(1f).translationX(wheelX).setStartDelay(90).setDuration(460).setInterpolator(SETTLE).start()
        glow.animate().alpha(1f).setStartDelay(90).setDuration(500).setInterpolator(SETTLE).start()
        others.forEachIndexed { i, v ->
            v.animate().alpha(1f).translationY(0f).setStartDelay(160L + i * 60).setDuration(380).setInterpolator(SETTLE).start()
        }
        animateEnergy(0f, 900)
        touch()
    }

    private fun enter() {
        idle = true
        homeX = clock.translationX
        homeY = clock.translationY
        wheelX = wheel.translationX
        val at = IntArray(2)
        clock.getLocationOnScreen(at)
        val root = clock.rootView
        centreX = homeX + root.width / 2f - (at[0] + clock.width / 2f)
        centreY = homeY + root.height / 2f - (at[1] + clock.height / 2f)
        clock.pivotX = clock.width / 2f
        clock.pivotY = clock.height / 2f
        // The clock grows and leaves its column, so nothing it sits in may clip it.
        var parent = clock.parent
        while (parent is ViewGroup) {
            parent.clipChildren = false
            parent.clipToPadding = false
            parent = parent.parent
        }

        wheel.animate().alpha(0f).translationX(wheelX - activity.dp(56f))
            .setStartDelay(0).setDuration(800).setInterpolator(LEAVE).start()
        glow.animate().alpha(0f).setStartDelay(0).setDuration(1200).setInterpolator(LEAVE).start()
        others.forEachIndexed { i, v ->
            v.animate().alpha(0f).translationY(activity.dp(10f)).setStartDelay(i * 80L).setDuration(520).setInterpolator(LEAVE).start()
        }
        clock.animate().translationX(centreX).translationY(centreY).scaleX(SCALE).scaleY(SCALE)
            .setStartDelay(350).setDuration(1300).setInterpolator(GLIDE).start()
        animateEnergy(1f, 2600)
        handler.postDelayed(drift, DRIFT_MS)
    }

    private fun drift() {
        if (!idle) return
        val dx = (Random.nextFloat() * 2 - 1) * activity.dp(48f)
        val dy = (Random.nextFloat() * 2 - 1) * activity.dp(28f)
        clock.animate().translationX(centreX + dx).translationY(centreY + dy)
            .setStartDelay(0).setDuration(5000).setInterpolator(GLIDE).start()
        handler.postDelayed(drift, DRIFT_MS)
    }

    private fun animateEnergy(to: Float, ms: Long) {
        energy?.cancel()
        energy = ValueAnimator.ofFloat(field.energy, to).apply {
            duration = ms
            interpolator = GLIDE
            addUpdateListener { field.energy = it.animatedValue as Float }
            start()
        }
    }

    private companion object {
        const val IDLE_AFTER_MS = 90_000L
        const val DRIFT_MS = 45_000L
        const val SCALE = 1.9f
        /** Slow in and out, for the clock's glide. */
        val GLIDE = PathInterpolator(0.45f, 0f, 0.15f, 1f)
        /** Gentle start, for things leaving. */
        val LEAVE = PathInterpolator(0.4f, 0f, 0.6f, 1f)
        /** Quick start, soft landing, for coming back. */
        val SETTLE = PathInterpolator(0.2f, 0f, 0f, 1f)
    }
}
