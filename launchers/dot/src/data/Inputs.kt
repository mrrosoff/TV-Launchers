package dev.mrrosoff.tv.dot.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import dev.mrrosoff.tv.dot.R

class InputRow(val label: String, val connected: Boolean, val intent: Intent, val order: String = "")

object Inputs {

    /**
     * The TV's HDMI and AV ports from the TV input framework, then the Live TV app. A port with a
     * name of its own (renamed in Settings, or reported over HDMI-CEC) shows "Name · HDMI 1".
     */
    fun load(context: Context, liveTvPackage: String): List<InputRow> {
        val rows = ArrayList<InputRow>()
        val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        val all = try {
            tim?.tvInputList.orEmpty()
        } catch (e: RuntimeException) {
            emptyList()
        }
        val ids = all.map { it.id }.toSet()
        for (info in all) {
            if (!info.isPassthroughInput || info.isHidden(context)) continue
            // CEC devices are children of their HDMI port: the port's row stands for them.
            if (info.parentId != null && info.parentId in ids) continue
            val base = info.loadLabel(context)?.toString().orEmpty()
            val custom = info.loadCustomLabel(context)?.toString()?.takeIf { it.isNotBlank() && it != base }
                ?: all.firstOrNull { it.parentId == info.id && !it.isHidden(context) }
                    ?.loadLabel(context)?.toString()?.takeIf { it.isNotBlank() && it != base }
            val label = if (custom != null) "$custom · $base" else base
            val connected = info.type == TvInputInfo.TYPE_HDMI &&
                tim?.getInputState(info.id) == TvInputManager.INPUT_STATE_CONNECTED
            val order = (if (info.type == TvInputInfo.TYPE_HDMI) "0" else "1") + base
            rows += InputRow(label, connected, passthrough(info.id), order)
        }
        rows.sortBy { it.order }
        if (liveTvPackage.isNotEmpty()) {
            context.packageManager.getLeanbackLaunchIntentForPackage(liveTvPackage)?.let {
                rows += InputRow(context.getString(R.string.live_tv), false, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        return rows
    }

    /** The Live TV app switches to an input when asked to view its passthrough channel. */
    private fun passthrough(inputId: String): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(TvContract.buildChannelUriForPassthroughInput(inputId), "vnd.android.cursor.item/channel")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
