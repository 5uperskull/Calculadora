package cl.icestar.pesototal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Recibe el intent del lector. Prueba primero el extra configurado y despues
 * los conocidos: un terminal mal configurado es el fallo mas comun, y adivinar
 * el extra correcto sale mas barato que un viaje a bodega.
 */
class ScanReceiver(private val onScan: (String) -> Unit) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val configured = PesoApp.instance.settings.scanExtra
        val raw = intent.getStringExtra(configured)
            ?: intent.getStringExtra(Settings.ZEBRA_EXTRA)
            ?: intent.getStringExtra(Settings.HONEYWELL_EXTRA)
            ?: intent.getStringExtra(Settings.HONEYWELL_EXTRA_ALT)
            ?: return
        if (raw.isNotBlank()) onScan(raw)
    }
}
