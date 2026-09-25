package cl.icestar.pesototal

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Escribe el total en el campo enfocado y, si se activa, lee el peso pedido de
 * la pantalla del WMS.
 *
 * Pensado para quedarse encendido todo el turno. Tener que apagarlo y
 * encenderlo a mano para que algo funcione era sintoma de que estorbaba: por
 * eso hace el minimo trabajo en el hilo principal y descarta todo evento que no
 * le toca.
 */
class InsertAccessibilityService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())

    // Recorrer el arbol son llamadas entre procesos: fuera del hilo principal,
    // para que nunca compitan con el teclado.
    private val worker = HandlerThread("peso-lectura").apply { start() }
    private val background = Handler(worker.looper)
    private val scan = Runnable { scanForTarget() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        applyScope(PesoApp.instance.settings.screenTarget)
    }

    override fun onDestroy() {
        background.removeCallbacks(scan)
        worker.quitSafely()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val settings = PesoApp.instance.settings
        if (!settings.screenTarget) return

        val pkg = event.packageName?.toString().orEmpty()
        // La propia burbuja genera eventos al redibujarse: cada relectura
        // provocaba otro redibujado y otra relectura.
        if (pkg == packageName) return
        val wms = settings.wmsPackage
        if (wms.isNotEmpty() && pkg != wms) return

        // Cada tecla escrita en el WMS es un cambio de contenido. Releer la
        // pantalla por eso compite con el teclado y no aporta nada: el peso
        // pedido no cambia mientras el operario escribe.
        val typing = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.className?.toString()?.contains("EditText") == true
        if (typing) return

        background.removeCallbacks(scan)
        background.postDelayed(scan, SCAN_DEBOUNCE_MS)
    }

    /** Corre en el hilo de fondo; solo el resultado vuelve al principal. */
    private fun scanForTarget() {
        val settings = PesoApp.instance.settings
        val found = TargetScraper.findTarget(readScreenTexts(120), settings.targetAnchor)
            ?: return
        // Un peso imposible es un numero mal leido, no un pedido raro.
        if (found <= 0.0 || found > WeightParser.MAX_KG) return
        if (found == settings.targetKg) return
        main.post {
            settings.targetKg = found
            onTargetDetected?.invoke(found)
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: InsertAccessibilityService? = null

        val isRunning: Boolean get() = instance != null

        /** Lo llena TallyService para llevar el objetivo detectado a la burbuja. */
        var onTargetDetected: ((Double) -> Unit)? = null

        private const val SCAN_DEBOUNCE_MS = 600L

        /**
         * Amplia o reduce el alcance en caliente. Declarado en XML solo escucha
         * el foco de entrada; los cambios de pantalla se piden aqui, y solo si
         * el cliente encendio la lectura del objetivo.
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

        /** Paquete de la ventana activa: el WMS, si se pide desde la burbuja. */
        fun activePackage(): String? {
            val root = instance?.rootInActiveWindow ?: return null
            return try {
                root.packageName?.toString()
            } finally {
                @Suppress("DEPRECATION")
                root.recycle()
            }
        }

        /**
         * Escribe el total en el campo enfocado.
         *
         * Primero SET_TEXT. Los campos de pagina web lo rechazan a menudo, asi
         * que si falla se selecciona el contenido y se pega: reemplaza el valor
         * que el WMS deja precargado y seleccionado.
         */
        fun setFocusedText(text: String): Boolean {
            val svc = instance ?: return false
            val node = svc.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            return try {
                if (!node.isEditable) return false
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text
                    )
                }
                if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true
                pasteInto(svc, node, text)
            } finally {
                @Suppress("DEPRECATION")
                node.recycle()
            }
        }

        private fun pasteInto(ctx: Context, node: AccessibilityNodeInfo, text: String): Boolean {
            if (!copy(ctx, text)) return false
            val length = node.text?.length ?: 0
            val selection = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, length)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
            return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }

        /**
         * Escribir al portapapeles funciona sin foco: Android 10+ restringe
         * LEERLO, no escribirlo. No hace falta robarle el foco a nadie, y
         * robarlo era justo lo que dejaba el teclado del WMS sin campo.
         */
        fun copy(ctx: Context, text: String): Boolean = try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("peso", text))
            true
        } catch (e: Exception) {
            false
        }

        /**
         * Textos visibles en la ventana activa. Puntual: no se suscribe a nada,
         * solo mira el arbol en el instante en que se pide.
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
            // Tope de profundidad y de resultados: un arbol de pagina web puede
            // tener miles de nodos.
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
    }
}
