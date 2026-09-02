package cl.icestar.pesototal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Recibe el intent del lector y saca el codigo de barras.
 *
 * Un terminal mal configurado es el fallo mas comun del despliegue, y se ve
 * igual que "la app no hace nada". Por eso hay cuatro intentos en cascada y,
 * si ninguno acierta, se reportan las claves que traia el intent: con eso se
 * arregla la configuracion sin volver a bodega.
 */
class ScanReceiver(
    private val onScan: (String) -> Unit,
    private val onUnknown: (List<String>) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val code = extract(intent)
        if (code != null) {
            onScan(code)
            return
        }
        onUnknown(intent.extras?.keySet()?.toList().orEmpty())
    }

    private fun extract(intent: Intent): String? {
        // 1. el extra configurado en Ajustes
        text(intent, PesoApp.instance.settings.scanExtra)?.let { return it }

        // 2. los extras conocidos de Zebra y Honeywell
        for (key in KNOWN) text(intent, key)?.let { return it }

        // 3. modo ByteArray: DataWedge puede entregar los datos como bytes
        decodeBytes(intent)?.let { return it }

        // 4. ultimo recurso: cualquier extra de texto que parezca un codigo
        return sniff(intent)
    }

    private fun text(intent: Intent, key: String): String? =
        intent.getStringExtra(key)?.trim()?.takeIf { it.isNotEmpty() }

    @Suppress("DEPRECATION")
    private fun decodeBytes(intent: Intent): String? {
        val list = intent.getSerializableExtra(DECODE_DATA) as? ArrayList<*> ?: return null
        val first = list.firstOrNull() as? ByteArray ?: return null
        return String(first, Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Heuristica deliberadamente estrecha: seis caracteres o mas y mayoria de
     * digitos, descartando las claves que describen la etiqueta en vez de
     * contenerla (LABEL_TYPE, source, version...).
     */
    private fun sniff(intent: Intent): String? {
        val extras = intent.extras ?: return null
        for (key in extras.keySet()) {
            if (IGNORED.any { key.contains(it, ignoreCase = true) }) continue
            val value = intent.getStringExtra(key)?.trim() ?: continue
            if (value.length < 6) continue
            val digits = value.count { it.isDigit() }
            if (digits * 2 >= value.length) return value
        }
        return null
    }

    private companion object {
        const val DECODE_DATA = "com.symbol.datawedge.decode_data"

        val KNOWN = listOf(
            Settings.ZEBRA_EXTRA,
            Settings.HONEYWELL_EXTRA,
            Settings.HONEYWELL_EXTRA_ALT,
            "barcode_string",
            "data_string",
            "scanData"
        )

        val IGNORED = listOf("label", "type", "source", "version", "symbology", "charset")
    }
}
