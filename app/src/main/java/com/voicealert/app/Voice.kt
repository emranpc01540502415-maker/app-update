package com.voicealert.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import java.util.Locale

object Voice {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, Int>? = null
    private var player: MediaPlayer? = null

    private fun attrs(usage: Int) = AudioAttributes.Builder()
        .setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

    fun play(ctx: Context, ev: Ev, level: Int = -1, force: Boolean = false, name: String = "", min: Int = -1) {
        val app = ctx.applicationContext
        val pr = Prefs(app)
        if (!force && !pr.enabled(ev)) return
        stop()
        val prayer = ev.id.startsWith("p_")
        val usage = if (prayer) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA
        val audio = pr.audio(ev) ?: if (prayer || ev.id == "adhan") pr.audio(Events.adhan) else null
        if (audio != null) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(attrs(usage))
                    setDataSource(audio)
                    setOnCompletionListener { mp -> mp.release(); if (player === mp) player = null }
                    prepare()
                    start()
                }
                return
            } catch (_: Exception) { /* TTS-এ ফিরে যাবে */ }
        }
        val text = pr.text(ev)
            .replace("{level}", if (level < 0) "" else Bn.d(level))
            .replace("{name}", name)
            .replace("{min}", if (min < 0) "" else Bn.d(min))
        if (text.isBlank()) return
        speak(app, text, usage)
    }

    fun stop() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        tts?.stop()
    }

    private fun speak(app: Context, text: String, usage: Int) {
        val t = tts
        if (t != null && ready) { say(app, t, text, usage); return }
        pending = text to usage
        if (t == null) {
            tts = TextToSpeech(app) { status ->
                val engine = tts
                if (status == TextToSpeech.SUCCESS && engine != null) {
                    setup(engine)
                    ready = true
                    pending?.let { pending = null; say(app, engine, it.first, it.second) }
                }
            }
        }
    }

    private fun setup(t: TextToSpeech) {
        val loc = listOf(Locale("bn", "BD"), Locale("bn", "IN"), Locale("bn"))
            .firstOrNull { t.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
        if (loc != null) {
            t.language = loc
            val vs = t.voices?.filter { it.locale.language == "bn" }.orEmpty()
            (vs.firstOrNull { it.name.contains("female", true) } ?: vs.firstOrNull())?.let { t.voice = it }
        }
    }

    private fun say(app: Context, t: TextToSpeech, text: String, usage: Int) {
        val pr = Prefs(app)
        t.setAudioAttributes(attrs(usage))
        t.setPitch(pr.pitch / 100f)
        t.setSpeechRate(pr.rate / 100f)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "va")
    }
}
