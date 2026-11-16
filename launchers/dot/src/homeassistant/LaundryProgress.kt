package dev.mrrosoff.tv.dot.homeassistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/** The washer and dryer runs in progress, kept current by [Laundry] for Home's info panel. */
object LaundryProgress {

    /** [startedAt] in epoch milliseconds; [expectedMinutes] is 0 when Home Assistant has no estimate yet. */
    data class Run(val name: String, val startedAt: Long, val expectedMinutes: Int) {
        /** Minutes left, or negative minutes over; null without an estimate. */
        fun minutesLeft(now: Long): Int? =
            if (expectedMinutes <= 0) null else (expectedMinutes - (now - startedAt) / 60_000.0).roundToInt()
    }

    private val state = MutableStateFlow<List<Run>>(emptyList())
    val runs: StateFlow<List<Run>> = state.asStateFlow()

    internal fun publish(runs: List<Run>) {
        state.value = runs
    }
}
