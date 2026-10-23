package dev.mrrosoff.tv.dot.data

import dev.mrrosoff.tv.dot.dots.ParticleView
import java.util.Calendar

/** A themed day: its greeting, colours, a dot-matrix emblem beside the weather, and an ambient effect. */
class Occasion(
    val id: String,
    val greeting: String,
    /** The emblem and greeting colour. */
    val accent: Int,
    /** A second colour for the corner glow and the effect. */
    val secondary: Int,
    val emblem: Array<String>,
    val effect: ParticleView.Effect,
)

object Occasions {

    private const val ORANGE = 0xFFFF7A18.toInt()
    private const val PURPLE = 0xFF8A4DFF.toInt()
    private const val ICE = 0xFF9FD8FF.toInt()
    private const val BLUE = 0xFF3D6BFF.toInt()
    private const val RED = 0xFFFF3B3B.toInt()
    private const val GOLD = 0xFFFFC83D.toInt()
    private const val PINK = 0xFFFF5FA2.toInt()

    /** Ids accepted by [preview], in the order Home screen settings cycles through them. */
    val previewIds = listOf("", "halloween", "holidays", "newyear", "july4", "birthday")

    /**
     * The occasion for [day], or null on an ordinary day. Birthdays come first; [birthdays] is
     * "Name:MM-DD" pairs separated by commas, from the gitignored personal.properties.
     */
    fun on(day: Calendar, birthdays: String): Occasion? {
        val month = day.get(Calendar.MONTH) + 1
        val date = day.get(Calendar.DAY_OF_MONTH)
        birthday(month, date, birthdays)?.let { return it }
        return when {
            month == 10 && date in 24..31 -> halloween()
            month == 12 && date in 18..30 -> holidays()
            month == 12 && date == 31 || month == 1 && date == 1 -> newYear()
            month == 7 && date == 4 -> july4()
            else -> null
        }
    }

    /** A theme by id, to see it outside its dates. */
    fun preview(id: String, birthdays: String): Occasion? = when (id) {
        "halloween" -> halloween()
        "holidays" -> holidays()
        "newyear" -> newYear()
        "july4" -> july4()
        "birthday" -> birthdayFor(parse(birthdays).firstOrNull()?.first ?: "you")
        else -> null
    }

    private fun birthday(month: Int, date: Int, birthdays: String): Occasion? =
        parse(birthdays).firstOrNull { it.second == month && it.third == date }?.let { birthdayFor(it.first) }

    private fun parse(birthdays: String): List<Triple<String, Int, Int>> = birthdays.split(',').mapNotNull { entry ->
        val name = entry.substringBefore(':').trim()
        val md = entry.substringAfter(':', "").trim().split('-')
        val m = md.getOrNull(0)?.toIntOrNull()
        val d = md.getOrNull(1)?.toIntOrNull()
        if (name.isEmpty() || m == null || d == null) null else Triple(name, m, d)
    }

    private fun halloween() = Occasion("halloween", "Happy Halloween", ORANGE, PURPLE, PUMPKIN, ParticleView.Effect.BATS)
    private fun holidays() = Occasion("holidays", "Happy holidays", ICE, BLUE, SNOWFLAKE, ParticleView.Effect.SNOW)
    private fun newYear() = Occasion("newyear", "Happy new year", GOLD, ICE, SNOWFLAKE, ParticleView.Effect.SNOW)
    private fun july4() = Occasion("july4", "Happy 4th of July", RED, BLUE, STAR, ParticleView.Effect.FIREWORKS)
    private fun birthdayFor(name: String) = Occasion("birthday", "Happy birthday $name", GOLD, PINK, CAKE, ParticleView.Effect.CONFETTI)

    private val PUMPKIN = arrayOf(
        "....##...",
        "...#.....",
        ".##.#.##.",
        "#########",
        "##.###.##",
        "#########",
        "##.#.#.##",
        ".#######.",
        "..#####..",
    )
    private val SNOWFLAKE = arrayOf(
        "....#....",
        ".#..#..#.",
        "..#.#.#..",
        "...###...",
        "#########",
        "...###...",
        "..#.#.#..",
        ".#..#..#.",
        "....#....",
    )
    private val STAR = arrayOf(
        "....#....",
        "....#....",
        "...###...",
        "#########",
        ".#######.",
        "..#####..",
        "..##.##..",
        ".##...##.",
        "#.......#",
    )
    private val CAKE = arrayOf(
        "..#.#.#..",
        "..#.#.#..",
        ".#######.",
        ".#.#.#.#.",
        ".#######.",
        "#########",
        "#.#.#.#.#",
        "#########",
        ".........",
    )
}
