package cl.icestar.pesototal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

/**
 * Control del lector en caliente.
 *
 * No se cambia de perfil: se modifica el perfil del WMS. Un perfil sin app
 * asociada nunca se activa solo, y SWITCH_TO_PROFILE esta pensado para que lo
 * llame la app que esta en primer plano. La nuestra vive detras del WMS, asi
 * que ese camino no sirve.
 *
 * SET_CONFIG si funciona desde atras porque no toca cual perfil esta activo,
 * sino que reescribe una opcion del perfil que ya lo esta.
 */
object DataWedge {

    private const val PKG = "com.symbol.datawedge"
    private const val API_ACTION = "com.symbol.datawedge.api.ACTION"
    private const val SET_CONFIG = "com.symbol.datawedge.api.SET_CONFIG"
    private const val COMMAND_ID = "peso_corte_teclado"

    const val RESULT_ACTION = "com.symbol.datawedge.api.RESULT_ACTION"

    /**
     * En la pantalla de DataWedge se ve "Profile0", pero para la API se llama
     * "Profile0 (default)". Con el nombre corto el comando se ignora sin dar
     * error, que es exactamente como se ve "el modo SUMA no bloquea".
     */
    const val PROFILE0 = "Profile0 (default)"

    fun profileName(raw: String): String {
        val name = raw.trim()
        return if (name.equals("Profile0", ignoreCase = true)) PROFILE0 else name
    }

    /** Respuesta de DataWedge a nuestro corte; null si es de otro comando. */
    fun describeResult(intent: Intent): Pair<Boolean, String>? {
        if (intent.getStringExtra("COMMAND_IDENTIFIER") != COMMAND_ID) return null
        val ok = intent.getStringExtra("RESULT") == "SUCCESS"
        val info = intent.getBundleExtra("RESULT_INFO")
        @Suppress("DEPRECATION")
        val detail = info?.keySet()?.joinToString { key -> key + "=" + info.get(key) }
        return ok to detail.orEmpty()
    }

    fun isAvailable(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(PKG, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Enciende o apaga la salida de teclado del perfil, dejando intacta la
     * salida de intents. Con el teclado apagado el operario puede tener el
     * cursor dentro del textbox del WMS y el codigo no se escribe ahi: solo
     * nos llega a nosotros.
     *
     * DataWedge no contesta si acepto el cambio. El operario lo ve al instante
     * (el codigo se escribe o no), y el servicio restaura el teclado al morir
     * para que nunca quede el WMS sin poder escanear.
     */
    fun setKeystrokeOutput(ctx: Context, profile: String, enabled: Boolean): Boolean {
        if (!isAvailable(ctx)) return false

        val params = Bundle().apply {
            putString("keystroke_output_enabled", enabled.toString())
        }
        val plugin = Bundle().apply {
            putString("PLUGIN_NAME", "KEYSTROKE")
            putString("RESET_CONFIG", "false")
            putBundle("PARAM_LIST", params)
        }
        val config = Bundle().apply {
            putString("PROFILE_NAME", profileName(profile))
            putString("PROFILE_ENABLED", "true")
            putString("CONFIG_MODE", "UPDATE")
            putBundle("PLUGIN_CONFIG", plugin)
        }

        // Se pide respuesta: sin ella un corte rechazado era invisible.
        ctx.sendBroadcast(
            Intent(API_ACTION)
                .setPackage(PKG)
                .putExtra(SET_CONFIG, config)
                .putExtra("SEND_RESULT", "true")
                .putExtra("COMMAND_IDENTIFIER", COMMAND_ID)
        )
        return true
    }
}
