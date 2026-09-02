package cl.icestar.pesototal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * La burbuja. Una sola ventana con dos caras: pastilla colapsada y panel.
 *
 * FLAG_NOT_FOCUSABLE es la decision central: recibe toques pero jamas le quita
 * el foco al WMS, que asi sigue recibiendo escaneos y teclado.
 */
class OverlayController(
    private val ctx: Context,
    private val tally: Tally,
    private val settings: Settings
) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val root: View = LayoutInflater.from(ctx).inflate(R.layout.overlay, null)

    private val pill: LinearLayout = root.findViewById(R.id.pill)
    private val panel: View = root.findViewById(R.id.panel)
    private val totalView: TextView = root.findViewById(R.id.total)
    private val countView: TextView = root.findViewById(R.id.count)
    private val modeChip: TextView = root.findViewById(R.id.modeChip)
    private val linesBox: LinearLayout = root.findViewById(R.id.lines)
    private val statusView: TextView = root.findViewById(R.id.status)
    private val btnInsert: Button = root.findViewById(R.id.btnInsert)

    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    private var expanded = false
    private var shown = false

    // Se da de baja en hide(): el servicio se reinicia al guardar ajustes y sin
    // esto quedarian listeners apuntando a vistas ya retiradas.
    private var changeListener: (() -> Unit)? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = settings.bubbleX
        y = settings.bubbleY
    }

    private val dim = Runnable { root.alpha = dimmedAlpha() }
    private val autoCollapse = Runnable { collapse() }

    // ---------------------------------------------------------------- ciclo

    fun show() {
        if (shown) return
        wm.addView(root, params)
        shown = true
        wireUp()
        changeListener = tally.onChange { handler.post { render() } }
        render()
        wake()
    }

    fun hide() {
        if (!shown) return
        handler.removeCallbacksAndMessages(null)
        changeListener?.let { tally.removeChange(it) }
        changeListener = null
        wm.removeView(root)
        shown = false
    }

    /** Un escaneo aceptado: se ilumina y dice cuanto entro. */
    fun onScanAdded(kg: Double) {
        status("+ " + WeightParser.format(kg, settings.comma) + " kg")
        wake()
    }

    /** Llego el intent pero ningun extra traia el codigo: decir cuales venian. */
    fun onUnknownIntent(keys: List<String>) {
        settings.lastIntentKeys = keys.joinToString(", ")
        val msg =
            if (keys.isEmpty()) ctx.getString(R.string.intent_sin_extras)
            else ctx.getString(R.string.intent_extras, keys.joinToString(", "))
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        wake()
    }

    fun onScanRejected(code: String) {
        Toast.makeText(ctx, ctx.getString(R.string.sin_peso, code), Toast.LENGTH_SHORT).show()
        wake()
    }

    // ------------------------------------------------------------- interfaz

    private fun wireUp() {
        pill.setOnTouchListener(DragTouch())
        modeChip.setOnClickListener { toggleMode() }
        btnInsert.setOnClickListener { insert() }
        root.findViewById<Button>(R.id.btnUndo).setOnClickListener {
            if (!tally.undo()) status(ctx.getString(R.string.nada_que_deshacer))
            wake()
        }
        root.findViewById<Button>(R.id.btnReset).setOnClickListener {
            tally.reset()
            wake()
        }
        root.findViewById<Button>(R.id.btnClose).setOnClickListener { collapse() }
    }

    private fun render() {
        totalView.text = WeightParser.format(tally.total, settings.comma) + " kg"
        countView.text = tally.count.toString()

        modeChip.text = ctx.getString(if (settings.sumMode) R.string.modo_suma else R.string.modo_wms)
        modeChip.setBackgroundResource(
            if (settings.sumMode) R.drawable.bg_chip_on else R.drawable.bg_chip_off
        )
        // El chip apagado es gris oscuro: el texto oscuro del XML no se leeria.
        modeChip.setTextColor(ctx.getColor(if (settings.sumMode) R.color.bg else R.color.txt))

        btnInsert.text = ctx.getString(
            if (InsertAccessibilityService.isRunning) R.string.insertar else R.string.copiar
        )

        // El modo barra de borde solo estrecha la pastilla: el conteo se va.
        countView.visibility = if (settings.edgeBar && !expanded) View.GONE else View.VISIBLE

        renderLines()
    }

    private fun renderLines() {
        if (!expanded) return
        linesBox.removeAllViews()
        val rows = tally.snapshot()
        if (rows.isEmpty()) {
            linesBox.addView(rowView(ctx.getString(R.string.sin_lineas), "", false, null))
            return
        }
        rows.forEachIndexed { index, line ->
            linesBox.addView(
                rowView(
                    WeightParser.format(line.kg, settings.comma) + " kg",
                    line.code,
                    line.duplicate
                ) {
                    tally.removeAt(index)
                    wake()
                }
            )
        }
    }

    private fun rowView(
        left: String,
        right: String,
        duplicate: Boolean,
        onDelete: (() -> Unit)?
    ): View {
        val row = LayoutInflater.from(ctx).inflate(R.layout.overlay_line, linesBox, false)
        val kg = row.findViewById<TextView>(R.id.lineKg)
        kg.text = left
        if (duplicate) kg.setTextColor(ctx.getColor(R.color.amber))
        row.findViewById<TextView>(R.id.lineCode).text = right
        val del = row.findViewById<TextView>(R.id.lineDelete)
        if (onDelete == null) {
            del.visibility = View.INVISIBLE
        } else {
            del.setOnClickListener { onDelete() }
        }
        return row
    }

    // --------------------------------------------------------------- accion

    private fun toggle() {
        if (expanded) collapse() else expand()
    }

    private fun expand() {
        expanded = true
        panel.visibility = View.VISIBLE
        render()
        wake()
    }

    private fun collapse() {
        expanded = false
        panel.visibility = View.GONE
        render()
        wake()
    }

    private fun toggleMode() {
        val next = !settings.sumMode
        settings.sumMode = next
        if (settings.useProfiles) {
            val profile = if (next) settings.profileSum else settings.profileWms
            if (!DataWedge.switchProfile(ctx, profile)) {
                status(ctx.getString(R.string.sin_datawedge))
            }
        }
        render()
        wake()
    }

    private fun insert() {
        if (tally.isEmpty) {
            status(ctx.getString(R.string.nada_que_insertar))
            return
        }
        val text = WeightParser.format(tally.total, settings.comma)

        if (InsertAccessibilityService.setFocusedText(text)) {
            status(ctx.getString(R.string.insertado, text))
            afterInsert()
            return
        }

        copyWithFocus(text) { ok ->
            status(
                if (ok) ctx.getString(R.string.copiado, text)
                else ctx.getString(R.string.no_se_pudo_copiar)
            )
            if (ok) afterInsert()
        }
    }

    private fun afterInsert() {
        if (settings.resetAfterInsert) tally.reset()
        if (settings.sumMode) toggleMode()
        wake()
    }

    /**
     * Android 10+ ignora la escritura al portapapeles si la app no tiene foco.
     * Se lo damos por un instante y lo devolvemos enseguida, para no dejar al
     * WMS sin foco mas tiempo del imprescindible.
     */
    private fun copyWithFocus(text: String, done: (Boolean) -> Unit) {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        wm.updateViewLayout(root, params)
        root.post {
            val ok = try {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("peso", text))
                true
            } catch (e: Exception) {
                false
            }
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            wm.updateViewLayout(root, params)
            done(ok)
        }
    }

    // ------------------------------------------------------------ presencia

    private fun status(text: String) {
        statusView.text = text
        statusView.visibility = View.VISIBLE
    }

    private fun dimmedAlpha() = (settings.alpha / 100f) * 0.55f

    /** Vuelve a plena visibilidad y reprograma atenuacion o cierre. */
    private fun wake() {
        root.alpha = settings.alpha / 100f
        handler.removeCallbacks(dim)
        handler.removeCallbacks(autoCollapse)
        if (expanded) handler.postDelayed(autoCollapse, AUTO_COLLAPSE_MS)
        else handler.postDelayed(dim, DIM_DELAY_MS)
    }

    private fun snapToEdge() {
        val screen = ctx.resources.displayMetrics.widthPixels
        val center = params.x + root.width / 2
        params.x = if (center > screen / 2) screen - root.width else 0
        params.y = params.y.coerceAtLeast(0)
        wm.updateViewLayout(root, params)
        settings.bubbleX = params.x
        settings.bubbleY = params.y
    }

    private inner class DragTouch : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = e.rawX
                    touchY = e.rawY
                    moved = false
                    wake()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        params.x = startX + dx
                        params.y = startY + dy
                        wm.updateViewLayout(root, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (moved) snapToEdge() else toggle()
            }
            return true
        }
    }

    private companion object {
        const val DIM_DELAY_MS = 4_000L
        const val AUTO_COLLAPSE_MS = 12_000L
    }
}
