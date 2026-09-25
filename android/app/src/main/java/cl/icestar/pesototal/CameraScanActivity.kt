package cl.icestar.pesototal

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Lector por camara, para probar sin terminal RF.
 *
 * Sin interfaz propia: usa el escaner de Google Play Services, que trae su
 * pantalla de camara y no exige permiso de camara a la app. Esta actividad solo
 * lo lanza, entrega el codigo y se cierra; al cerrarse, el WMS vuelve al frente.
 */
class CameraScanActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Al girar la pantalla se recrea: no abrir un segundo escaner encima.
        if (savedInstanceState != null) return

        GmsBarcodeScanning.getClient(this)
            .startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue
                if (raw != null && !TallyService.handleCameraScan(raw)) {
                    Toast.makeText(this, R.string.camara_sin_burbuja, Toast.LENGTH_LONG).show()
                }
                finish()
            }
            .addOnCanceledListener { finish() }
            .addOnFailureListener {
                Toast.makeText(this, R.string.camara_no_disponible, Toast.LENGTH_LONG).show()
                finish()
            }
    }
}
