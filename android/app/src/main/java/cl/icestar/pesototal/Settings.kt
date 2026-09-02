package cl.icestar.pesototal

import android.content.Context

/** Todas las perillas en un solo lugar. Nada aqui obliga a recompilar. */
class Settings(ctx: Context) {

    private val p = ctx.applicationContext.getSharedPreferences("peso", Context.MODE_PRIVATE)

    private fun str(k: String, d: String) = p.getString(k, d) ?: d
    private fun put(k: String, v: Any) = p.edit().apply {
        when (v) {
            is String -> putString(k, v)
            is Int -> putInt(k, v)
            is Boolean -> putBoolean(k, v)
            else -> throw IllegalArgumentException("Tipo no soportado: " + v.javaClass)
        }
    }.apply()

    /** Accion del intent que emite el lector. La define el perfil del terminal. */
    var scanAction: String
        get() = str("scanAction", DEFAULT_ACTION)
        set(v) = put("scanAction", v)

    /** Nombre del extra que trae el codigo. Cambia entre Zebra y Honeywell. */
    var scanExtra: String
        get() = str("scanExtra", ZEBRA_EXTRA)
        set(v) = put("scanExtra", v)

    /** Recorte de respaldo cuando el codigo no trae el AI 310n. */
    var offset: Int
        get() = p.getInt("offset", WeightParser.DEFAULT_OFFSET)
        set(v) = put("offset", v)

    var len: Int
        get() = p.getInt("len", WeightParser.DEFAULT_LEN)
        set(v) = put("len", v)

    var comma: Boolean
        get() = p.getBoolean("comma", true)
        set(v) = put("comma", v)

    /** Opacidad de la burbuja, en porcentaje. */
    var alpha: Int
        get() = p.getInt("alpha", 75)
        set(v) = put("alpha", v.coerceIn(20, 100))

    var edgeBar: Boolean
        get() = p.getBoolean("edgeBar", false)
        set(v) = put("edgeBar", v)

    /**
     * En modo SUMA, apagar la salida de teclado del lector para que el codigo
     * no se escriba en el textbox del WMS. Apagado por defecto: si el terminal
     * no acepta el cambio, el operario tiene que darse cuenta de que sigue
     * escribiendose, no descubrirlo despues.
     */
    var cutKeystroke: Boolean
        get() = p.getBoolean("cutKeystroke", false)
        set(v) = put("cutKeystroke", v)

    /** Perfil de DataWedge asociado al WMS: es el que se modifica en caliente. */
    var profileWms: String
        get() = str("profileWms", "WMS")
        set(v) = put("profileWms", v)

    /** false = el escaneo va al WMS. true = alimenta la suma. */
    var sumMode: Boolean
        get() = p.getBoolean("sumMode", false)
        set(v) = put("sumMode", v)

    var resetAfterInsert: Boolean
        get() = p.getBoolean("resetAfterInsert", true)
        set(v) = put("resetAfterInsert", v)

    /** Claves del ultimo intent recibido. Solo para diagnosticar en Ajustes. */
    var lastIntentKeys: String
        get() = str("lastIntentKeys", "")
        set(v) = put("lastIntentKeys", v)

    var bubbleX: Int
        get() = p.getInt("bubbleX", 0)
        set(v) = put("bubbleX", v)

    var bubbleY: Int
        get() = p.getInt("bubbleY", 240)
        set(v) = put("bubbleY", v)

    fun tallyStore(): Tally.Store = object : Tally.Store {
        override fun load(): String? = p.getString("tally", null)
        override fun save(data: String) = p.edit().putString("tally", data).apply()
    }

    companion object {
        const val DEFAULT_ACTION = "cl.icestar.pesototal.SCAN"
        const val ZEBRA_EXTRA = "com.symbol.datawedge.data_string"
        const val HONEYWELL_EXTRA = "data"
        const val HONEYWELL_EXTRA_ALT = "com.honeywell.aidc.EXTRA_BARCODE_DATA"
    }
}
