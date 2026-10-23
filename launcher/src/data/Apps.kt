package dev.mrrosoff.braviatv.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.drawable.Drawable
import java.text.Collator

class App(val packageName: String, val label: String, private val info: ActivityInfo) {

    /** Not cached: only the selected app's banner is ever on screen. */
    fun banner(context: Context): Drawable? {
        val pm = context.packageManager
        return info.loadBanner(pm) ?: info.applicationInfo.loadBanner(pm) ?: info.loadIcon(pm)
    }

    fun launchIntent(context: Context): Intent =
        (context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(ComponentName(packageName, info.name)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

object Apps {

    /** Every app with a TV launcher entry, one per package, alphabetical by name. */
    fun load(context: Context, hidden: Set<String>): List<App> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val seen = HashSet<String>()
        val apps = ArrayList<App>()
        for (ri in pm.queryIntentActivities(query, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == context.packageName || pkg in hidden || !seen.add(pkg)) continue
            val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
            apps += App(pkg, label, ri.activityInfo)
        }
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        apps.sortWith { a, b -> collator.compare(a.label, b.label) }
        return apps
    }
}
