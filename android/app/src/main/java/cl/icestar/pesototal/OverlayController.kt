package cl.icestar.pesototal

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
    private val summaryView: TextView = root.findViewById(R.id.summary)
    private val btnInsert: Button = root.findViewById(R.id.btnInsert)
    private val btnClose: Button = root.findViewById(R.id.btnClose)
    private val btnManual: Button = root.findViewById(R.id.btnManual)
    private val btnCamera: Button = root.findViewById(R.id.btnCamera)
    private val keypad: View = root.findViewById(R.id.keypad)
    private val linesScroll: View = root.findViewById(R.id.linesScroll)
    private val primaryRow: View = root.findViewById(R.id.primaryRow)
    private val actionsRow: View = root.findViewById(R.id.actionsRow)
    private val manualValue: TextView = root.findViewById(R.id.manualValue)
    private val targetLine: TextView = root.findViewById(R.id.targetLine)

    /** Lo llena el servicio: apagar la burbuja es apagar el servicio. */
    var onExit: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    private var expanded = false
    private var shown = false
    private var exitArmed = false
    private var manualBuffer = ""

    /** El teclado sirve para el peso de una caja y para el objetivo. */
    private var typingTarget = false

    /** Null hasta el primer render: al arrancar no se avisa de nada. */
    private var lastState: Target.State? = null

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
    private val endDuplicateFlash = Runnable { render() }

    /** Hasta cuando se mantiene el aviso amarillo de etiqueta repetida. */
    private var duplicateUntil = 0L
    private val disarmExitLater = Runnable { disarmExit() }
    private val autoCollapse = Runnable { collapse() }

    // ---------------------------------------------------------------- ciclo

    fun show() {
        if (shown) return
        wm.addView(root, params)
        shown = true
        wireUp()
        changeListener = tally.onChange {
            // Si la suma cambia entre la primera y la segunda insercion, lo que
            // se inserte despues ya es otro total: la cuenta vuelve a empezar.
            settings.insertsDone = 0
            handler.post { render() }
        }
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

    /** Un escaneo aceptado: dice cuanto entro y vibra corto. */
    fun onScanAdded(kg: Double) {
        status("+ " + WeightParser.format(kg, settings.comma) + " kg")
        buzz(BUZZ_OK)
        wake()
    }

    /**
     * Etiqueta ya leida: no se suma, solo se avisa. La pastilla se pone amarilla
     * unos segundos y vuelve sola, porque no hay linea nueva que la sostenga.
     */
    fun onDuplicate() {
        duplicateUntil = SystemClock.uptimeMillis() + DUPLICATE_FLASH_MS
        status(ctx.getString(R.string.duplicado_no_sumado))
        buzz(BUZZ_DUP)
        if (settings.sound) Voice.say(ctx.getString(R.string.voz_duplicado))
        render()
        handler.removeCallbacks(endDuplicateFlash)
        handler.postDelayed(endDuplicateFlash, DUPLICATE_FLASH_MS)
        wake()
    }

    /**
     * Con guantes y ruido de camara de frio la pantalla no basta. Cada patron
     * significa una cosa: corta confirma, doble avisa de repetida o de error,
     * larga es peso alcanzado, y triple es exceso.
     */
    private fun buzz(pattern: LongArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
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

    /**
     * Lectura puntual pedida desde la burbuja.
     *
     * Tiene que dispararse desde aqui: la burbuja no toma el foco, asi que la
     * ventana activa sigue siendo el WMS y es su arbol el que se lee. Pedirlo
     * desde la pantalla de ajustes leeria los textos de la propia app.
     */
    private fun scanScreenNow() {
        if (!InsertAccessibilityService.isRunning) {
            status(ctx.getString(R.string.pantalla_sin_accesibilidad))
            buzz(BUZZ_DUP)
            return
        }

        val texts = InsertAccessibilityService.readScreenTexts()
        settings.lastScreenTexts = texts.joinToString(separator = "|")
        // Desde la burbuja la ventana activa es el WMS: se aprende su paquete
        // para que la lectura automatica ignore cualquier otra app.
        InsertAccessibilityService.activePackage()
            ?.takeIf { it.isNotEmpty() && it != ctx.packageName }
            ?.let { settings.wmsPackage = it }

        val found = TargetScraper.findTarget(texts, settings.targetAnchor)
        if (found == null || found <= 0.0 || found > WeightParser.MAX_KG) {
            status(ctx.getString(R.string.pantalla_no_detectado))
            buzz(BUZZ_DUP)
            return
        }

        settings.targetKg = found
        lastState = null
        status(
            ctx.getString(R.string.objetivo_detectado, WeightParser.format(found, settings.comma))
        )
        buzz(BUZZ_OK)
        render()
        wake()
    }

    /** El objetivo llego solo desde la pantalla del WMS. */
    fun onTargetDetected(kg: Double) {
        // Objetivo nuevo, historia nueva: sin esto el cambio de banda
        // arrastraria el estado del pedido anterior.
        lastState = null
        status(ctx.getString(R.string.objetivo_detectado, WeightParser.format(kg, settings.comma)))
        render()
        wake()
    }

    fun onScanRejected(code: String) {
        Toast.makeText(ctx, ctx.getString(R.string.sin_peso, code), Toast.LENGTH_SHORT).show()
        buzz(BUZZ_DUP)
        if (settings.sound) Voice.say(ctx.getString(R.string.voz_sin_peso))
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
        btnClose.setOnClickListener { if (exitArmed) onExit?.invoke() else armExit() }

        btnManual.setOnClickListener { openManual() }
        btnCamera.setOnClickListener { openCamera() }
        targetLine.setOnClickListener { openTarget() }
        targetLine.setOnLongClickListener {
            scanScreenNow()
            true
        }
        root.findViewById<Button>(R.id.btnManualCancel).setOnClickListener { closeManual() }
        root.findViewById<Button>(R.id.btnManualAdd).setOnClickListener { addManual() }
        root.findViewById<Button>(R.id.keyDel).setOnClickListener { backspaceManual() }
        DIGITS.forEach { (id, ch) ->
            root.findViewById<Button>(id).setOnClickListener { typeManual(ch) }
        }
    }

    private fun render() {
        val totalText = WeightParser.format(tally.total, settings.comma)
        val target = settings.targetKg
        val state = Target.state(tally.total, target, settings.toleranceKg, settings.nearKg)

        totalView.text = if (target > 0.0) {
            totalText + " / " + WeightParser.format(target, settings.comma) + " kg"
        } else {
            "$totalText kg"
        }
        countView.text = tally.count.toString()
        summaryView.text = ctx.getString(R.string.resumen, tally.count, totalText)
        renderTargetLine(target, state)

        val duplicate = SystemClock.uptimeMillis() < duplicateUntil
        // El exceso manda sobre el duplicado: es el error que cuesta plata.
        pill.setBackgroundResource(
            when {
                state == Target.State.EXCEDIDO -> R.drawable.bg_pill_over
                state == Target.State.EN_PESO -> R.drawable.bg_pill_ok
                duplicate -> R.drawable.bg_pill_dup
                else -> R.drawable.bg_pill
            }
        )
        totalView.setTextColor(
            ctx.getColor(
                when {
                    // Sobre el relleno rojo, blanco: el rojo sobre rojo no se leia.
                    state == Target.State.EXCEDIDO -> R.color.white
                    state == Target.State.EN_PESO -> R.color.green
                    duplicate -> R.color.amber
                    else -> R.color.txt
                }
            )
        )

        modeChip.text = ctx.getString(if (settings.sumMode) R.string.modo_suma else R.string.modo_wms)
        modeChip.setBackgroundResource(
            if (settings.sumMode) R.drawable.bg_chip_on else R.drawable.bg_chip_off
        )
        // El chip apagado es gris oscuro: el texto oscuro del XML no se leeria.
        modeChip.setTextColor(ctx.getColor(if (settings.sumMode) R.color.bg else R.color.txt))

        val action = ctx.getString(
            if (InsertAccessibilityService.isRunning) R.string.insertar else R.string.copiar
        )
        val steps = settings.insertsPerTask
        btnInsert.text = if (steps > 1) {
            ctx.getString(R.string.accion_paso, action, settings.insertsDone + 1, steps)
        } else {
            action
        }

        // El modo barra de borde solo estrecha la pastilla: el conteo se va.
        countView.visibility = if (settings.edgeBar && !expanded) View.GONE else View.VISIBLE

        renderLines()
        announceTarget(state)
    }

    private fun renderTargetLine(target: Double, state: Target.State) {
        if (target <= 0.0) {
            targetLine.text = ctx.getString(R.string.objetivo_vacio)
            targetLine.setTextColor(ctx.getColor(R.color.dim))
            return
        }
        val targetText = WeightParser.format(target, settings.comma)
        val diff = Target.remaining(tally.total, target)
        val text: String
        val color: Int
        when (state) {
            Target.State.EN_PESO -> {
                text = ctx.getString(R.string.objetivo_en_peso, targetText)
                color = R.color.green
            }
            Target.State.EXCEDIDO -> {
                text = ctx.getString(
                    R.string.objetivo_excedido,
                    targetText,
                    WeightParser.format(-diff, settings.comma)
                )
                color = R.color.coral
            }
            else -> {
                text = ctx.getString(
                    R.string.objetivo_falta,
                    targetText,
                    WeightParser.format(diff, settings.comma)
                )
                color = if (state == Target.State.CERCA) R.color.ice else R.color.dim
            }
        }
        targetLine.text = text
        targetLine.setTextColor(ctx.getColor(color))
    }

    /**
     * Avisa solo en el cambio de estado. Si lo hiciera en cada render, cada
     * escaneo repetiria "excedido" y el operario dejaria de oirlo.
     */
    private fun announceTarget(state: Target.State) {
        val previous = lastState
        lastState = state
        if (previous == null || previous == state) return
        when (state) {
            Target.State.EN_PESO -> {
                buzz(BUZZ_TARGET)
                if (settings.sound) Voice.say(ctx.getString(R.string.voz_completo))
            }
            Target.State.EXCEDIDO -> {
                buzz(BUZZ_OVER)
                if (settings.sound) Voice.say(ctx.getString(R.string.voz_excedido))
            }
            else -> Unit
        }
    }

    private fun renderLines() {
        if (!expanded) return
        linesBox.removeAllViews()
        val rows = tally.snapshot()
        if (rows.isEmpty()) {
            linesBox.addView(
                rowView("", ctx.getString(R.string.sin_lineas), "", false, false, null)
            )
            return
        }
        // La ultima leida arriba, que es donde se mira. El numero sigue siendo
        // el orden de escaneo, asi la lista invertida no pierde la secuencia.
        rows.withIndex().reversed().forEach { (index, line) ->
            linesBox.addView(
                rowView(
                    (index + 1).toString(),
                    WeightParser.format(line.kg, settings.comma) + " kg",
                    if (line.manual) ctx.getString(R.string.manual) else line.code,
                    line.duplicate,
                    line.manual
                ) {
                    tally.removeAt(index)
                    wake()
                }
            )
        }
        // Si el operario habia bajado a revisar, lo recien escaneado vuelve a
        // quedar a la vista.
        linesScroll.post { linesScroll.scrollTo(0, 0) }
    }

    private fun rowView(
        number: String,
        left: String,
        right: String,
        duplicate: Boolean,
        manual: Boolean,
        onDelete: (() -> Unit)?
    ): View {
        val row = LayoutInflater.from(ctx).inflate(R.layout.overlay_line, linesBox, false)
        row.findViewById<TextView>(R.id.lineNum).text = number
        val kg = row.findViewById<TextView>(R.id.lineKg)
        kg.text = left
        if (duplicate) kg.setTextColor(ctx.getColor(R.color.amber))
        val codeView = row.findViewById<TextView>(R.id.lineCode)
        codeView.text = right
        // El peso a mano queda marcado: es trazabilidad, no decoracion.
        if (manual) codeView.setTextColor(ctx.getColor(R.color.ice))
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
        hideKeypad()
        render()
        wake()
    }

    // ---------------------------------------------------------- peso a mano

    /**
     * Etiquetas rotas o ilegibles hay todos los dias, asi que el peso se puede
     * teclear. El teclado es propio en vez de un EditText: son toques sobre una
     * ventana sin foco, asi que el cursor del WMS no se mueve ni sube el teclado
     * del sistema tapando media pantalla.
     */
    /**
     * La burbuja tiene permiso de superposicion, y eso la exime del bloqueo de
     * Android a abrir actividades desde segundo plano.
     */
    private fun openCamera() {
        val intent = Intent(ctx, CameraScanActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            status(ctx.getString(R.string.camara_no_disponible))
        }
    }

    private fun openManual() {
        typingTarget = false
        openKeypad()
    }

    /** Mismo teclado, otro destino: el peso que pide el WMS. */
    private fun openTarget() {
        typingTarget = true
        openKeypad()
    }

    private fun openKeypad() {
        manualBuffer = ""
        renderManual()
        keypad.visibility = View.VISIBLE
        linesScroll.visibility = View.GONE
        primaryRow.visibility = View.GONE
        actionsRow.visibility = View.GONE
        wake()
    }

    private fun hideKeypad() {
        keypad.visibility = View.GONE
        linesScroll.visibility = View.VISIBLE
        primaryRow.visibility = View.VISIBLE
        actionsRow.visibility = View.VISIBLE
    }

    private fun closeManual() {
        hideKeypad()
        render()
        wake()
    }

    private fun typeManual(ch: Char) {
        val buffer = manualBuffer
        if (ch == ',') {
            if (buffer.contains(',')) return
            manualBuffer = if (buffer.isEmpty()) "0," else "$buffer,"
        } else {
            // Tope deliberado: cuatro enteros y tres decimales cubren cualquier
            // pallet, y cortan el cero de mas por dedo con guante.
            if (buffer.contains(',')) {
                if (buffer.substringAfter(',').length >= 3) return
            } else if (buffer.length >= 4) {
                return
            }
            manualBuffer = buffer + ch
        }
        renderManual()
        wake()
    }

    private fun backspaceManual() {
        if (manualBuffer.isNotEmpty()) manualBuffer = manualBuffer.dropLast(1)
        renderManual()
        wake()
    }

    private fun renderManual() {
        val empty = manualBuffer.isEmpty()
        manualValue.text = if (empty) {
            ctx.getString(if (typingTarget) R.string.objetivo_hint else R.string.manual_hint)
        } else {
            "$manualBuffer kg"
        }
        manualValue.setTextColor(ctx.getColor(if (empty) R.color.dim else R.color.txt))
    }

    /** Entrada humana: se valida y se rechaza, nunca se corrige por dentro. */
    private fun addManual() {
        val kg = manualBuffer.replace(',', '.').toDoubleOrNull()
        // En el objetivo el cero es valido y significa borrarlo; en un peso no.
        val minimum = if (typingTarget) 0.0 else 0.001
        if (kg == null || kg < minimum || kg > WeightParser.MAX_KG) {
            status(ctx.getString(R.string.manual_invalido))
            buzz(BUZZ_DUP)
            return
        }

        if (typingTarget) {
            settings.targetKg = kg
            // Objetivo nuevo, historia nueva: sin esto el cambio de banda
            // arrastraria el estado del pedido anterior.
            lastState = null
            status(
                if (kg <= 0.0) {
                    ctx.getString(R.string.objetivo_borrado)
                } else {
                    ctx.getString(
                        R.string.objetivo_fijado, WeightParser.format(kg, settings.comma)
                    )
                }
            )
        } else {
            tally.addManual(kg)
            status(
                ctx.getString(R.string.manual_agregado, WeightParser.format(kg, settings.comma))
            )
        }

        buzz(BUZZ_OK)
        closeManual()
    }

    private fun collapse() {
        expanded = false
        panel.visibility = View.GONE
        render()
        wake()
    }

    /**
     * Salir de SUMA es el final natural del ciclo, asi que inserta lo acumulado
     * en vez de pedir un toque mas. Si la insercion falla, el modo se queda en
     * SUMA: nadie pierde la cuenta por un campo sin foco.
     */
    private fun toggleMode() {
        if (settings.sumMode && !tally.isEmpty) {
            insert()
            return
        }
        setMode(!settings.sumMode)
    }

    private fun setMode(sum: Boolean) {
        settings.sumMode = sum
        applyScannerMode(sum)
        render()
        wake()
    }

    /**
     * En SUMA se le apaga el teclado al lector: el operario deja el cursor en
     * el textbox del WMS y el codigo no se escribe ahi, solo nos llega.
     */
    private fun applyScannerMode(sumMode: Boolean) {
        if (!settings.cutKeystroke) return
        if (!DataWedge.setKeystrokeOutput(ctx, settings.profileWms, !sumMode)) {
            status(ctx.getString(R.string.sin_datawedge))
        }
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

        // Sin accesibilidad: al portapapeles, y el operario pega. Sin tocar el
        // foco de nadie, asi el cursor del WMS sigue donde estaba.
        if (InsertAccessibilityService.copy(ctx, text)) {
            status(ctx.getString(R.string.copiado, text))
            afterInsert()
        } else {
            status(ctx.getString(R.string.no_se_pudo_copiar))
        }
    }

    /**
     * El WMS pide el peso, lo procesa, y vuelve a pedir el mismo. Por eso la
     * suma no se cierra en la primera insercion sino en la ultima.
     */
    private fun afterInsert() {
        val done = settings.insertsDone + 1
        if (done < settings.insertsPerTask) {
            settings.insertsDone = done
            render()
            wake()
            return
        }
        settings.insertsDone = 0
        if (settings.resetAfterInsert) {
            tally.reset()
            // La tarea siguiente trae otro pedido: arrastrar el objetivo viejo
            // haria sonar "excedido" en cuanto empiece a sumar.
            settings.targetKg = 0.0
            lastState = null
        }
        if (settings.sumMode) setMode(false)
        wake()
    }

    // Apagar la burbuja en medio de un turno seria un accidente caro, asi que
    // pide dos toques y se desarma sola.
    private fun armExit() {
        exitArmed = true
        btnClose.setText(R.string.salir_confirmar)
        btnClose.setTextColor(ctx.getColor(R.color.coral))
        status(ctx.getString(R.string.salir_aviso))
        handler.removeCallbacks(disarmExitLater)
        handler.postDelayed(disarmExitLater, EXIT_CONFIRM_MS)
    }

    private fun disarmExit() {
        exitArmed = false
        btnClose.setText(R.string.salir)
        btnClose.setTextColor(ctx.getColor(R.color.dim))
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
        // Con el teclado abierto no se cierra solo: se perderia lo tecleado.
        if (keypad.visibility == View.VISIBLE) return
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
        val DIGITS = listOf(
            R.id.key0 to '0', R.id.key1 to '1', R.id.key2 to '2', R.id.key3 to '3',
            R.id.key4 to '4', R.id.key5 to '5', R.id.key6 to '6', R.id.key7 to '7',
            R.id.key8 to '8', R.id.key9 to '9', R.id.keySep to ','
        )

        val BUZZ_OK = longArrayOf(0, 30)
        val BUZZ_DUP = longArrayOf(0, 45, 90, 45)
        val BUZZ_TARGET = longArrayOf(0, 220)
        val BUZZ_OVER = longArrayOf(0, 120, 80, 120, 80, 120)

        const val DUPLICATE_FLASH_MS = 3_000L
        const val DIM_DELAY_MS = 4_000L
        const val EXIT_CONFIRM_MS = 3_000L
        const val AUTO_COLLAPSE_MS = 12_000L
    }
}
