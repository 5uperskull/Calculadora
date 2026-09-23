package cl.icestar.pesototal

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Escribe el total en el campo que tenga el foco de entrada.
 *
 * Apunta al nodo enfocado, nunca a un id del WMS: asi no se rompe cuando el WMS
 * se actualice. Alcance minimo declarado en res/xml/accessibility_service.xml.
 */
class InsertAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        applyScope(PesoApp.instance.settings.screenTarget)
    }

    override fun onDestroy() {
        handler.removeCallbacks(scan)
        if (instance === this) instance = null
        super.onDestroy()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scan = Runnable { scanForTarget() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!PesoApp.instance.settings.screenTarget) return
        // El WMS redibuja la pantalla varias veces seguidas: se agrupan los
        // eventos para no recorrer el arbol una vez por cada redibujado.
        handler.removeCallbacks(scan)
        handler.postDelayed(scan, SCAN_DEBOUNCE_MS)
    }

    private fun scanForTarget() {
        val settings = PesoApp.instance.settings
        val found = TargetScraper.findTarget(readScreenTexts(120), settings.targetAnchor)
            ?: return
        // Un peso imposible es un numero mal leido, no un pedido raro.
        if (found <= 0.0 || found > WeightParser.MAX_KG) return
        if (found == settings.targetKg) return
        settings.targetKg = found
        onTargetDetected?.invoke(found)
    }

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: InsertAccessibilityService? = null

        val isRunning: Boolean get() = instance != null

        /** Lo llena TallyService para llevar el objetivo detectado a la burbuja. */
        var onTargetDetected: ((Double) -> Unit)? = null

        private const val SCAN_DEBOUNCE_MS = 400L

        /**
         * Amplia o reduce el alcance en caliente.
         *
         * Declarado en XML el servicio solo escucha el foco de entrada. Los
         * eventos de cambio de pantalla se piden aqui, y solo si el cliente
         * encendio la lectura del objetivo: el alcance minimo sigue siendo el
         * default, y ampliarlo es un acto deliberado.
         */
        fun applyScope(readScreen: Boolean) {
            val svc = instance ?: return
            val info = svc.serviceInfo ?: return
            val extra = if (readScreen) {
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            } else {
                0
            }
            info.eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED or extra
            svc.serviceInfo = info
        }

        /**
         * Textos visibles en la ventana activa, para diagnosticar contra el WMS
         * real en vez de adivinar su maquetacion.
         *
         * Es una lectura puntual: no necesita ampliar el alcance porque no se
         * suscribe a nada, solo mira el arbol en el instante en que se pide.
         */
        fun readScreenTexts(limit: Int = 60): List<String> {
            val svc = instance ?: return emptyList()
            val root = svc.rootInActiveWindow ?: return emptyList()
            val out = LinkedHashSet<String>()
            try {
                collect(root, out, limit, depth = 0)
            } finally {
                @Suppress("DEPRECATION")
                root.recycle()
            }
            return out.toList()
        }

        private fun collect(
            node: AccessibilityNodeInfo,
            out: MutableSet<String>,
            limit: Int,
            depth: Int
        ) {
            // Tope de profundidad y de resultados: un arbol de WebView puede
            // tener miles de nodos y esto corre en el hilo principal.
            if (out.size >= limit || depth > 24) return
            val text = (node.text ?: node.contentDescription)?.toString()?.trim()
            if (!text.isNullOrEmpty() && text.length <= 80) out += text
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    collect(child, out, limit, depth + 1)
                } finally {
                    @Suppress("DEPRECATION")
                    child.recycle()
                }
            }
        }

        /** false = no hay servicio, no hay campo enfocado, o el campo lo rechazo. */
        fun setFocusedText(text: String): Boolean {
            val svc = instance ?: return false
            val node: AccessibilityNodeInfo =
                svc.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            return try {
                if (!node.isEditable) return false
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text
                    )
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            } finally {
                @Suppress("DEPRECATION")
                node.recycle()
            }
        }
    }
}
