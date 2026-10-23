package dev.mrrosoff.tv.dot.dots

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The ambient layer of a themed day, drawn as LED dots at about 30 frames a second, and only while
 * [running] (Home on screen). The motion also keeps the OLED panel from holding a still image.
 */
class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Effect { NONE, SNOW, BATS, FIREWORKS, CONFETTI }

    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var size: Float, var color: Int, var life: Float = 1f, var phase: Float = 0f,
    )

    private var effect = Effect.NONE
    private var colors = intArrayOf(Color.WHITE)
    private val particles = ArrayList<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastFrame = 0L
    private var nextBurst = 0L

    var running = false
        set(value) {
            field = value
            lastFrame = 0L
            if (value) postInvalidateOnAnimation()
        }

    fun show(effect: Effect, palette: IntArray) {
        this.effect = effect
        colors = palette
        particles.clear()
        visibility = if (effect == Effect.NONE) GONE else VISIBLE
        if (width > 0) seed()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        particles.clear()
        seed()
    }

    private fun seed() {
        val count = when (effect) {
            Effect.SNOW -> 70
            Effect.CONFETTI -> 60
            Effect.BATS -> 7
            else -> 0
        }
        repeat(count) { particles += spawn(Random.nextFloat() * height) }
    }

    private fun spawn(y: Float): Particle = when (effect) {
        Effect.SNOW -> Particle(
            Random.nextFloat() * width, y, 0f, dp(8f + Random.nextFloat() * 14f),
            dp(1.2f + Random.nextFloat() * 1.6f), Color.WHITE, phase = Random.nextFloat() * 6.28f,
        ).also { it.life = 0.25f + Random.nextFloat() * 0.45f }
        Effect.CONFETTI -> Particle(
            Random.nextFloat() * width, y, 0f, dp(18f + Random.nextFloat() * 22f),
            dp(1.6f + Random.nextFloat() * 1.4f), colors.random(), phase = Random.nextFloat() * 6.28f,
        ).also { it.life = 0.5f + Random.nextFloat() * 0.4f }
        Effect.BATS -> Particle(
            if (Random.nextBoolean()) -dp(20f) else width + dp(20f), height * (0.1f + Random.nextFloat() * 0.6f),
            0f, 0f, dp(1.8f), colors.random(), phase = Random.nextFloat() * 6.28f,
        ).also {
            it.vx = dp(22f + Random.nextFloat() * 18f) * if (it.x < 0) 1 else -1
            it.life = 0.55f
        }
        else -> Particle(0f, 0f, 0f, 0f, 0f, 0)
    }

    override fun onDraw(canvas: Canvas) {
        if (effect == Effect.NONE) return
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1e9f).coerceAtMost(0.1f)
        lastFrame = now
        when (effect) {
            Effect.FIREWORKS -> fireworks(canvas, dt, now)
            Effect.BATS -> bats(canvas, dt)
            else -> falling(canvas, dt)
        }
        // About 30 fps is plenty for drifting dots and halves the work of a full-rate animation.
        if (running) postInvalidateDelayed(FRAME_MS)
    }

    private fun falling(canvas: Canvas, dt: Float) {
        for (p in particles) {
            p.phase += dt * 1.3f
            p.y += p.vy * dt
            p.x += sin(p.phase) * dp(10f) * dt
            if (p.y > height + p.size) {
                p.y = -p.size
                p.x = Random.nextFloat() * width
            }
            paint.color = p.color
            paint.alpha = (255 * p.life).toInt()
            canvas.drawCircle(p.x, p.y, p.size, paint)
        }
    }

    private fun bats(canvas: Canvas, dt: Float) {
        val pitch = dp(3.2f)
        for (i in particles.indices) {
            val p = particles[i]
            p.phase += dt * 9f
            p.x += p.vx * dt
            p.y += sin(p.phase * 0.25f) * dp(6f) * dt
            if (p.x < -dp(40f) || p.x > width + dp(40f)) particles[i] = spawn(0f)
            val shape = if (sin(p.phase) > 0) BAT_UP else BAT_DOWN
            paint.color = p.color
            paint.alpha = (255 * p.life).toInt()
            shape.forEachIndexed { r, row ->
                row.forEachIndexed { c, ch ->
                    if (ch == '#') canvas.drawCircle(p.x + (c - row.length / 2f) * pitch, p.y + r * pitch, pitch * 0.36f, paint)
                }
            }
        }
    }

    private fun fireworks(canvas: Canvas, dt: Float, now: Long) {
        if (now > nextBurst && width > 0) {
            // The open space between the wheel and the clock, below the clock and above the buttons.
            val cx = width * (0.5f + Random.nextFloat() * 0.25f)
            val cy = height * (0.55f + Random.nextFloat() * 0.2f)
            val color = colors.random()
            val n = 22
            for (k in 0 until n) {
                val angle = 2 * PI * k / n
                val speed = dp(70f + Random.nextFloat() * 30f)
                particles += Particle(cx, cy, (cos(angle) * speed).toFloat(), (sin(angle) * speed).toFloat(), dp(1.8f), color)
            }
            nextBurst = now + (1_400_000_000L + Random.nextLong(1_800_000_000L))
        }
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt * 0.7f
            if (p.life <= 0f) {
                it.remove()
                continue
            }
            p.vy += dp(30f) * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            paint.color = p.color
            paint.alpha = (255 * p.life).toInt()
            canvas.drawCircle(p.x, p.y, p.size, paint)
        }
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private companion object {
        const val FRAME_MS = 33L
        val BAT_UP = arrayOf("#.....#", "##.#.##", ".#####.", "..#.#..")
        val BAT_DOWN = arrayOf(".......", "...#...", ".#####.", "##.#.##")
    }
}
