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
            putString("PROFILE_NAME", profile)
            putString("PROFILE_ENABLED", "true")
            putString("CONFIG_MODE", "UPDATE")
            putBundle("PLUGIN_CONFIG", plugin)
        }

        ctx.sendBroadcast(
            Intent(API_ACTION).setPackage(PKG).putExtra(SET_CONFIG, config)
        )
        return true
    }
}
