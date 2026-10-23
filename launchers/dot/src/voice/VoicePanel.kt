package dev.mrrosoff.tv.dot.voice

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import dev.mrrosoff.tv.dot.R
import dev.mrrosoff.tv.dot.data.App
import dev.mrrosoff.tv.dot.data.SearchTargets
import dev.mrrosoff.tv.dot.dots.DotTextView
import dev.mrrosoff.tv.dot.home.MenuRows

/**
 * Dot's own voice search, since Google TV's search screen belongs to its disabled launcher. Android's
 * speech recogniser (Google's, through the remote's microphone) turns speech into words; an app name opens
 * that app, anything else is offered to every app that can search for it.
 */
class VoicePanel(
    private val activity: Activity,
    private val panel: ScrollView,
    private val title: DotTextView,
    private val heard: DotTextView,
    private val list: LinearLayout,
    /** Where focus goes when the panel closes. */
    private val home: View,
    private val apps: () -> List<App>,
    private val open: (App, Intent?) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    /** Bumped on every start, so a late timer from an earlier attempt can't close a newer one. */
    private var session = 0

    val isOpen get() = panel.visibility == View.VISIBLE

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            Toast.makeText(activity, R.string.voice_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        session++
        show(activity.getString(R.string.voice_listening), "")
        list.removeAllViews()
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(activity).apply {
            setRecognitionListener(listener)
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
            )
        }
    }

    fun close() {
        recognizer?.destroy()
        recognizer = null
        if (!isOpen) return
        // Move focus off the panel before hiding it, or Android hands focus to whatever is nearest.
        home.requestFocus()
        panel.visibility = View.GONE
        list.removeAllViews()
    }

    private fun show(titleText: String, words: String) {
        title.text = titleText
        heard.text = words
        if (!isOpen) {
            panel.alpha = 0f
            panel.visibility = View.VISIBLE
            panel.animate().alpha(1f).setDuration(160).start()
        }
    }

    private fun handle(query: String) {
        val apps = apps()
        SearchTargets.namedApp(apps, query)?.let { app ->
            close()
            open(app, null)
            return
        }
        show(activity.getString(R.string.voice_search_in), query)
        val targets = SearchTargets.forQuery(activity, apps, query)
        if (targets.isEmpty()) {
            title.text = activity.getString(R.string.voice_no_apps)
            return
        }
        val rows = targets.map { t ->
            MenuRows.make(activity, t.app.label) {
                close()
                try {
                    open(t.app, t.intent)
                } catch (e: ActivityNotFoundException) {
                    open(t.app, null)
                }
            }.also { list.addView(it) }
        }
        MenuRows.chain(rows)
        rows.first().requestFocus()
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partial: Bundle) {
            partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { heard.text = it }
        }

        override fun onResults(results: Bundle) {
            val query = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (query.isNullOrBlank()) onError(SpeechRecognizer.ERROR_NO_MATCH) else handle(query)
        }

        override fun onError(error: Int) {
            show(activity.getString(R.string.voice_try_again), "")
            val failed = session
            panel.postDelayed({ if (session == failed && list.childCount == 0) close() }, ERROR_CLOSE_MS)
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            title.text = activity.getString(R.string.voice_thinking)
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val ERROR_CLOSE_MS = 2500L
    }
}
