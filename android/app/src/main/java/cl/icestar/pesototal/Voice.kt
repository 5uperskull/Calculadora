package cl.icestar.pesototal

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Avisos hablados, grabados dentro del APK.
 *
 * Antes se usaba el motor de voz del equipo, y en terminales como la Zebra
 * MC3401 no viene o viene sin espanol: el aviso callaba sin decir nada. Con los
 * audios dentro de la app suena igual en cualquier terminal.
 *
 * Sale por el canal de ALARMA: en bodega el de notificaciones suele estar
 * bajado o silenciado, y un aviso que no se oye es peor que ninguno.
 */
object Voice {

    enum class Clip(val res: Int) {
        DUPLICADO(R.raw.voz_duplicado),
        SIN_PESO(R.raw.voz_sin_peso),
        COMPLETO(R.raw.voz_completo),
        EXCEDIDO(R.raw.voz_excedido)
    }

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<Clip, Int>()
    private val loaded = mutableSetOf<Int>()

    val isReady: Boolean get() = pool != null && loaded.size == Clip.values().size

    fun start(ctx: Context) {
        if (pool != null) return
        val created = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        created.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded += sampleId
        }
        Clip.values().forEach { ids[it] = created.load(ctx.applicationContext, it.res, 1) }
        pool = created
    }

    fun say(clip: Clip) {
        val current = pool
        val id = ids[clip]
        if (current != null && id != null && id in loaded) {
            current.play(id, 1f, 1f, 1, 0, 1f)
        } else {
            // Recien arrancado los audios pueden no haber terminado de cargar:
            // un pitido es peor aviso que la palabra, pero mejor que el silencio.
            beep()
        }
    }

    private fun beep() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 500)
        }
    }

    fun stop() {
        pool?.release()
        pool = null
        ids.clear()
        loaded.clear()
    }
}
