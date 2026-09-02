package cl.icestar.pesototal

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Avisos hablados.
 *
 * Algunas ROM de terminal industrial vienen sin motor de voz o sin el idioma
 * instalado. Cuando eso pasa se cae a un pitido: peor aviso que una palabra,
 * pero infinitamente mejor que el silencio, que el operario interpretaria como
 * "no paso nada".
 */
object Voice {

    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false

    val isReady: Boolean get() = ready

    fun start(ctx: Context) {
        if (tts != null) return
        val app = ctx.applicationContext
        tts = TextToSpeech(app) { status ->
            val engine = tts
            ready = status == TextToSpeech.SUCCESS && engine != null && setLanguage(engine)
            if (ready) {
                engine?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
        }
    }

    /** Espanol de Chile si esta; si no, espanol a secas. */
    private fun setLanguage(engine: TextToSpeech): Boolean {
        for (locale in listOf(Locale("es", "CL"), Locale("es"))) {
            if (engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE) return true
        }
        return false
    }

    /** QUEUE_FLUSH a proposito: escaneando rapido interesa el ultimo aviso. */
    fun say(text: String) {
        val engine = tts
        if (ready && engine != null) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "peso")
        } else {
            beep()
        }
    }

    private fun beep() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 500)
        }
    }

    fun stop() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
