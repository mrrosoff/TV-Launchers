package dev.mrrosoff.braviatv.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable

/** An app's colour on the wheel: its brand colour if known, else its banner's dominant colour, brightened. */
object BrandColors {

    private val known = mapOf(
        "com.netflix.ninja" to 0xFFE50914,
        "com.google.android.youtube.tv" to 0xFFFF0033,
        "com.amazon.amazonvideo.livingroom" to 0xFF00A8E1,
        "com.disney.disneyplus" to 0xFF2E6BFF,
        "com.hulu.livingroomplus" to 0xFF1CE783,
        "com.wbd.stream" to 0xFF4B5BFF,
        "com.peacocktv.peacockandroid" to 0xFFFFC72C,
        "com.cbs.ott" to 0xFF0064FF,
        "com.apple.atve.sony.appletv" to 0xFFF5F5F7,
        "com.spotify.tv.android" to 0xFF1ED760,
    ).mapValues { it.value.toInt() }

    fun of(packageName: String, banner: () -> Drawable?): Int =
        known[packageName] ?: readable(dominant(banner()))

    /** Hue histogram of saturated, not-too-dark pixels on a 24x24 copy; white when nothing qualifies. */
    private fun dominant(d: Drawable?): Int {
        d ?: return Color.WHITE
        val size = 24
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val copy = d.constantState?.newDrawable()?.mutate() ?: d
        copy.setBounds(0, 0, size, size)
        copy.draw(Canvas(bmp))
        val px = IntArray(size * size)
        bmp.getPixels(px, 0, size, 0, 0, size, size)
        bmp.recycle()
        val buckets = 24
        val count = IntArray(buckets)
        val sumR = IntArray(buckets)
        val sumG = IntArray(buckets)
        val sumB = IntArray(buckets)
        val hsv = FloatArray(3)
        for (p in px) {
            if (Color.alpha(p) < 200) continue
            Color.colorToHSV(p, hsv)
            if (hsv[1] < 0.35f || hsv[2] < 0.25f) continue
            val b = (hsv[0] / 360f * buckets).toInt().coerceIn(0, buckets - 1)
            count[b]++
            sumR[b] += Color.red(p)
            sumG[b] += Color.green(p)
            sumB[b] += Color.blue(p)
        }
        val best = count.indices.maxByOrNull { count[it] } ?: return Color.WHITE
        if (count[best] < 8) return Color.WHITE
        return Color.rgb(sumR[best] / count[best], sumG[best] / count[best], sumB[best] / count[best])
    }

    private fun readable(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        if (hsv[2] < 0.8f) hsv[2] = 0.8f
        return Color.HSVToColor(hsv)
    }
}
