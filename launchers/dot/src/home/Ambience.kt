package dev.mrrosoff.tv.dot.home

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.view.View
import dev.mrrosoff.tv.dot.data.Prefs
import kotlin.random.Random

/** The glow in the selected app's colour, and burn-in drift. */
class Ambience(
    private val activity: Activity,
    private val prefs: Prefs,
    private val handler: Handler,
    private val glow: View,
    /** The static parts of Home that drift. */
    private val drifting: List<View>,
) {
    private var glowColor = 0
    private var glowAnim: ValueAnimator? = null
    private val glowDrawable = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        setGradientCenter(0.06f, 0.5f)
    }

    init {
        glow.background = glowDrawable
        glow.post { glowDrawable.gradientRadius = glow.width * 0.7f }
    }

    /** Ease the glow from the last app's colour to [target]. */
    fun glowTo(target: Int) {
        glowAnim?.cancel()
        glowAnim = ValueAnimator.ofObject(ArgbEvaluator(), glowColor, target).apply {
            duration = 300
            addUpdateListener {
                glowColor = it.animatedValue as Int
                glowDrawable.colors = intArrayOf((glowColor and 0x00FFFFFF) or 0x38000000, glowColor and 0x00FFFFFF)
            }
            start()
        }
    }

    /** Burn-in care: every few minutes the static parts of Home move a few pixels. */
    fun startDrift() {
        handler.removeCallbacks(drift)
        if (prefs.pixelShift) {
            handler.postDelayed(drift, DRIFT_MS)
        } else {
            drifting.forEach { it.translationX = 0f; it.translationY = 0f }
        }
    }

    fun stopDrift() = handler.removeCallbacks(drift)

    private val drift = Runnable {
        val max = activity.dp(6f)
        val dx = Random.nextFloat() * 2 * max - max
        val dy = Random.nextFloat() * 2 * max - max
        drifting.forEach { it.animate().translationX(dx).translationY(dy).setDuration(1500).start() }
        startDrift()
    }

    private companion object {
        const val DRIFT_MS = 3 * 60 * 1000L
    }
}
