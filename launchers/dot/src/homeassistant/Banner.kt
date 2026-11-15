package dev.mrrosoff.tv.dot.homeassistant

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import dev.mrrosoff.tv.dot.R
import dev.mrrosoff.tv.dot.dots.DotTextView
import dev.mrrosoff.tv.dot.home.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/** A dot-matrix tab that drops from the top edge near the right corner, over whatever is on screen. */
class Banner(private val service: AccessibilityService) {

    private val windows = service.getSystemService(WindowManager::class.java)

    suspend fun show(message: String) {
        val text = DotTextView(service).apply {
            text = message
            pitch = service.dp(3f)
            color = service.getColor(R.color.accent)
        }
        val flag = FrameLayout(service).apply {
            setBackgroundResource(R.drawable.flag)
            val h = service.dp(20f).toInt()
            setPadding(h, service.dp(12f).toInt(), h, service.dp(14f).toInt())
            addView(text)
            alpha = 0f
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = service.dp(64f).toInt()
            y = 0
        }
        windows.addView(flag, params)
        flag.post {
            flag.translationY = -flag.height.toFloat()
            flag.alpha = 1f
            flag.animate().translationY(0f).setInterpolator(DecelerateInterpolator()).setDuration(ANIM_MS).start()
        }
        delay(ANIM_MS + SHOWN.inWholeMilliseconds)
        flag.animate().translationY(-flag.height.toFloat()).setInterpolator(AccelerateInterpolator()).setDuration(ANIM_MS).start()
        delay(ANIM_MS)
        windows.removeView(flag)
    }

    private companion object {
        const val ANIM_MS = 320L
        val SHOWN = 5.seconds
    }
}
