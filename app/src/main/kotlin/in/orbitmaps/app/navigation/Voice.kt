// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.navigation

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Spoken instructions through the phone's own text-to-speech engine, in the phone's language, on the
 * navigation audio channel. Created when a trip starts, not at app start. Nothing is logged or
 * stored; the engine is the user's (it may be an online one they chose).
 */
class Voice(context: Context) {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            val tts = engine
            if (status == TextToSpeech.SUCCESS && tts != null) {
                val locale = Locale.getDefault()
                val result = tts.isLanguageAvailable(locale)
                if (result >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
                tts.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                ready = true
                pending?.let { speak(it) }
                pending = null
            }
        }
    }

    /** Says [text] now, cutting off an earlier instruction. Spoken once the engine is ready. */
    fun speak(text: String) {
        val tts = engine
        if (!ready || tts == null) {
            pending = text
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        pending = null
        engine?.stop()
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private companion object {
        const val UTTERANCE_ID = "orbit-nav"
    }
}
