package cl.icestar.pesototal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Cambio de perfil en caliente. Es lo que hace que el interruptor de la burbuja
 * decida si el codigo llega tambien al campo del WMS o solo a nosotros.
 *
 * ponytail: Honeywell no tiene equivalente directo. Cuando no hay DataWedge el
 * interruptor degrada a filtrado local y la burbuja lo dice, en vez de fingir.
 */
object DataWedge {

    private const val PKG = "com.symbol.datawedge"
    private const val API_ACTION = "com.symbol.datawedge.api.ACTION"
    private const val SWITCH_TO_PROFILE = "com.symbol.datawedge.api.SWITCH_TO_PROFILE"

    fun isAvailable(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(PKG, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun switchProfile(ctx: Context, profile: String): Boolean {
        if (!isAvailable(ctx)) return false
        val intent = Intent(API_ACTION)
            .setPackage(PKG)
            .putExtra(SWITCH_TO_PROFILE, profile)
        ctx.sendBroadcast(intent)
        return true
    }
}
