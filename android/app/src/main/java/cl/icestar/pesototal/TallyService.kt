package cl.icestar.pesototal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Sostiene la burbuja y la escucha del lector.
 *
 * El receiver va registrado en codigo, no en el manifiesto: desde Android 8 un
 * receiver del manifiesto no recibe acciones personalizadas, y la del lector lo
 * es. Por eso hace falta un servicio vivo, y por eso la notificacion.
 */
class TallyService : Service() {

    private lateinit var settings: Settings
    private lateinit var tally: Tally
    private var overlay: OverlayController? = null
    private var receiver: ScanReceiver? = null
    private var changeListener: (() -> Unit)? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settings = PesoApp.instance.settings
        tally = PesoApp.instance.tally

        createChannel()
        startForeground(NOTIF_ID, buildNotification())

        // Arranque seguro: si el proceso murio en modo SUMA, el perfil quedo
        // sin teclado y el WMS no podria escanear. Se vuelve siempre a WMS.
        settings.sumMode = false
        restoreKeystroke()

        overlay = OverlayController(this, tally, settings).also { it.show() }

        receiver = ScanReceiver(
            onScan = { raw -> onScan(raw) },
            onUnknown = { keys -> overlay?.onUnknownIntent(keys) }
        )
        // La categoria DEFAULT va a proposito: si DataWedge la pone en el
        // intent y el filtro no la tiene, el broadcast no llega nunca. Que al
        // filtro le sobre una categoria no estorba; que le falte, si.
        val filter = IntentFilter(settings.scanAction).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        ContextCompat.registerReceiver(
            this, receiver, filter, ContextCompat.RECEIVER_EXPORTED
        )

        changeListener = tally.onChange { updateNotification() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        restoreKeystroke()
        changeListener?.let { tally.removeChange(it) }
        changeListener = null
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
        overlay?.hide()
        overlay = null
        super.onDestroy()
    }

    /** Nunca dejar el lector sin teclado si nosotros ya no estamos. */
    private fun restoreKeystroke() {
        if (settings.cutKeystroke) {
            DataWedge.setKeystrokeOutput(this, settings.profileWms, true)
        }
    }

    private fun onScan(raw: String) {
        // En modo WMS el codigo es del WMS: no lo tocamos.
        if (!settings.sumMode) return

        val result = WeightParser.parse(raw, settings.offset, settings.len)
        if (result == null) {
            overlay?.onScanRejected(WeightParser.clean(raw))
            return
        }
        tally.add(result.kg, result.code)
        overlay?.onScanAdded(result.kg)
    }

    // ------------------------------------------------------------- notificacion

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL, getString(R.string.canal_burbuja), NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_bubble)
            .setContentTitle(
                getString(
                    R.string.notif_total,
                    WeightParser.format(tally.total, settings.comma),
                    tally.count
                )
            )
            .setContentText(
                getString(if (settings.sumMode) R.string.modo_suma_largo else R.string.modo_wms_largo)
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    companion object {
        private const val CHANNEL = "burbuja"
        private const val NOTIF_ID = 1

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, TallyService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, TallyService::class.java))
        }
    }
}
