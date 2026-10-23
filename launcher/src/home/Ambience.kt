package dev.mrrosoff.braviatv.home

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.view.View
import dev.mrrosoff.braviatv.data.Prefs
import kotlin.random.Random

/** The backdrop: the LED grid, the glow in the selected app's colour, and burn-in drift. */
class Ambience(
    private val activity: Activity,
    private val prefs: Prefs,
    private val handler: Handler,
    dotGrid: View,
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
        dotGrid.background = grid()
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

    /** One repeating tile, so the whole grid costs a single shader. */
    private fun grid(): BitmapDrawable {
        val pitch = activity.dp(8f).toInt()
        val tile = Bitmap.createBitmap(pitch, pitch, Bitmap.Config.ARGB_8888)
        Canvas(tile).drawCircle(pitch / 2f, pitch / 2f, activity.dp(0.9f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x12FFFFFF })
        return BitmapDrawable(activity.resources, tile).apply { setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    }

    private companion object {
        const val DRIFT_MS = 3 * 60 * 1000L
    }
}
