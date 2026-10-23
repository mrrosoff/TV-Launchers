package dev.mrrosoff.tv.dot.data

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Where a spoken query can go: apps that open their own search screen with the words filled in. */
object SearchTargets {

    class Target(val app: App, val intent: Intent)

    /** Search links for apps that don't take the standard search intent; {q} is the query. */
    private val links = mapOf(
        "com.google.android.youtube.tv" to "https://www.youtube.com/results?search_query={q}",
        "com.netflix.ninja" to "nflx://www.netflix.com/search?q={q}",
        "com.disney.disneyplus" to "https://www.disneyplus.com/search?q={q}",
    )

    /** Every installed app on the wheel that can search for [query], in the wheel's order. */
    fun forQuery(context: Context, apps: List<App>, query: String): List<Target> = apps.mapNotNull { app ->
        val intent = links[app.packageName]?.let { link ->
            Intent(Intent.ACTION_VIEW, Uri.parse(link.replace("{q}", Uri.encode(query)))).setPackage(app.packageName)
        } ?: Intent(Intent.ACTION_SEARCH).setPackage(app.packageName).putExtra(SearchManager.QUERY, query)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivity(intent, 0) != null) Target(app, intent) else null
    }

    /** The app named in [spoken] ("open Netflix", "netflix"), if any; the longest matching name wins. */
    fun namedApp(apps: List<App>, spoken: String): App? {
        val words = normalize(spoken)
        return apps.filter { normalize(it.label).isNotEmpty() && words.contains(normalize(it.label)) }
            .maxByOrNull { it.label.length }
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
}
