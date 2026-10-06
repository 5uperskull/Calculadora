package cl.icestar.pesototal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.provider.Settings as SysSettings

/**
 * Menu de la app, en dos niveles.
 *
 * Arriba, lo unico que un operario necesita: encender la burbuja, pegar dos
 * veces y el boton de camara, con interruptores que aplican al instante. Todo
 * lo demas queda plegado detras de una contrasena.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var s: Settings

    private lateinit var swBubble: SwitchCompat
    private lateinit var swTwoInserts: SwitchCompat
    private lateinit var swCamera: SwitchCompat
    private lateinit var permCard: View
    private lateinit var permText: TextView
    private lateinit var advanced: View
    private lateinit var advancedState: TextView

    private lateinit var status: TextView
    private lateinit var action: EditText
    private lateinit var extra: EditText
    private lateinit var offset: EditText
    private lateinit var len: EditText
    private lateinit var alpha: EditText
    private lateinit var tolerance: EditText
    private lateinit var near: EditText
    private lateinit var targetAnchor: EditText
    private lateinit var profileWms: EditText
    private lateinit var comma: CheckBox
    private lateinit var resetAfter: CheckBox
    private lateinit var edgeBar: CheckBox
    private lateinit var sound: CheckBox
    private lateinit var screenTarget: CheckBox
    private lateinit var cutKeystroke: CheckBox
    private lateinit var test: EditText
    private lateinit var testResult: TextView
    private lateinit var screenTexts: TextView

    private var unlocked = false

    /** Mientras se reflejan los ajustes en los interruptores, no disparan nada. */
    private var syncing = false

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_main)
        s = PesoApp.instance.settings

        swBubble = findViewById(R.id.swBubble)
        swTwoInserts = findViewById(R.id.swTwoInserts)
        swCamera = findViewById(R.id.swCamera)
        permCard = findViewById(R.id.permCard)
        permText = findViewById(R.id.permText)
        advanced = findViewById(R.id.advanced)
        advancedState = findViewById(R.id.advancedState)

        status = findViewById(R.id.status)
        action = findViewById(R.id.action)
        extra = findViewById(R.id.extra)
        offset = findViewById(R.id.offset)
        len = findViewById(R.id.len)
        alpha = findViewById(R.id.alpha)
        tolerance = findViewById(R.id.tolerance)
        near = findViewById(R.id.near)
        targetAnchor = findViewById(R.id.targetAnchor)
        profileWms = findViewById(R.id.profileWms)
        comma = findViewById(R.id.comma)
        resetAfter = findViewById(R.id.resetAfter)
        edgeBar = findViewById(R.id.edgeBar)
        sound = findViewById(R.id.sound)
        screenTarget = findViewById(R.id.screenTarget)
        cutKeystroke = findViewById(R.id.cutKeystroke)
        test = findViewById(R.id.test)
        testResult = findViewById(R.id.testResult)
        screenTexts = findViewById(R.id.screenTexts)

        wireOperator()
        wireAdvanced()
        showVersion()
        askNotifications()
        fill()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Al salir se vuelve a bloquear: el proximo que abra la app es un operario. */
    override fun onStop() {
        lock()
        super.onStop()
    }

    override fun onDestroy() {
        // Si la burbuja no esta corriendo, los audios los cargo esta pantalla
        // al probarlos y no los liberaria nadie mas.
        if (!TallyService.isRunning) Voice.stop()
        super.onDestroy()
    }

    // ------------------------------------------------------------ operario

    private fun wireOperator() {
        swBubble.setOnCheckedChangeListener { _, on ->
            if (syncing) return@setOnCheckedChangeListener
            if (on) startBubble() else TallyService.stop(this)
        }
        swTwoInserts.setOnCheckedChangeListener { _, on ->
            if (syncing) return@setOnCheckedChangeListener
            s.insertsPerTask = if (on) 2 else 1
            s.insertsDone = 0
            TallyService.notifySettingsChanged()
        }
        swCamera.setOnCheckedChangeListener { _, on ->
            if (syncing) return@setOnCheckedChangeListener
            s.cameraEnabled = on
            TallyService.notifySettingsChanged()
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(
                    SysSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        findViewById<Button>(R.id.btnAccess).setOnClickListener {
            startActivity(Intent(SysSettings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<View>(R.id.advancedHeader).setOnClickListener {
            if (unlocked) lock() else askPassword()
        }
    }

    private fun startBubble() {
        if (!SysSettings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.falta_overlay, Toast.LENGTH_LONG).show()
            refresh()
            return
        }
        TallyService.start(this)
    }

    // ----------------------------------------------------------- contrasena

    /** La comprobacion vive en AdminLock, que tiene su propio test. */
    private fun askPassword() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = getString(R.string.clave_hint)
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.clave_titulo)
            .setView(box)
            .setPositiveButton(R.string.entrar) { _, _ ->
                if (AdminLock.matches(input.text.toString())) {
                    unlocked = true
                    advanced.visibility = View.VISIBLE
                    refresh()
                } else {
                    Toast.makeText(this, R.string.clave_incorrecta, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun lock() {
        unlocked = false
        advanced.visibility = View.GONE
        advancedState.setText(R.string.avanzado_bloqueado)
    }

    // ------------------------------------------------------------ avanzado

    private fun wireAdvanced() {
        findViewById<Button>(R.id.btnZebra).setOnClickListener {
            extra.setText(Settings.ZEBRA_EXTRA)
        }
        findViewById<Button>(R.id.btnHoneywell).setOnClickListener {
            extra.setText(Settings.HONEYWELL_EXTRA)
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnTest).setOnClickListener { runTest() }
        findViewById<Button>(R.id.btnReadScreen).setOnClickListener { readScreen() }
        findViewById<Button>(R.id.btnTestVoice).setOnClickListener {
            // Carga los audios aunque la burbuja este apagada: probar la voz es
            // justo lo que se hace antes de desplegar.
            Voice.start(this)
            Voice.say(Voice.Clip.DUPLICADO)
            status.postDelayed({ refresh() }, 1500)
        }
    }

    /** Sin BuildConfig: AGP 8 lo genera solo si se pide, y no vale la pena. */
    private fun showVersion() {
        val name = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        findViewById<TextView>(R.id.version).text = getString(R.string.version, name)
    }

    private fun fill() {
        action.setText(s.scanAction)
        extra.setText(s.scanExtra)
        offset.setText(s.offset.toString())
        len.setText(s.len.toString())
        alpha.setText(s.alpha.toString())
        tolerance.setText(WeightParser.format(s.toleranceKg, s.comma))
        near.setText(WeightParser.format(s.nearKg, s.comma))
        targetAnchor.setText(s.targetAnchor)
        profileWms.setText(s.profileWms)
        comma.isChecked = s.comma
        resetAfter.isChecked = s.resetAfterInsert
        edgeBar.isChecked = s.edgeBar
        sound.isChecked = s.sound
        screenTarget.isChecked = s.screenTarget
        cutKeystroke.isChecked = s.cutKeystroke
    }

    private fun save() {
        s.scanAction = action.text.toString().trim().ifEmpty { Settings.DEFAULT_ACTION }
        s.scanExtra = extra.text.toString().trim().ifEmpty { Settings.ZEBRA_EXTRA }
        s.offset = offset.text.toString().toIntOrNull() ?: WeightParser.DEFAULT_OFFSET
        s.len = len.text.toString().toIntOrNull() ?: WeightParser.DEFAULT_LEN
        s.alpha = alpha.text.toString().toIntOrNull() ?: 75
        s.toleranceKg = kgOf(tolerance, Target.DEFAULT_TOLERANCE_KG)
        s.nearKg = kgOf(near, Target.DEFAULT_NEAR_KG)
        s.targetAnchor = targetAnchor.text.toString().trim()
            .ifEmpty { TargetScraper.DEFAULT_ANCHOR }
        s.profileWms = profileWms.text.toString().trim().ifEmpty { DataWedge.PROFILE0 }
        s.comma = comma.isChecked
        s.resetAfterInsert = resetAfter.isChecked
        s.edgeBar = edgeBar.isChecked
        s.sound = sound.isChecked
        s.screenTarget = screenTarget.isChecked
        // El alcance del servicio se amplia o se reduce aqui mismo: apagar la
        // casilla surte efecto sin reinstalar.
        InsertAccessibilityService.applyScope(s.screenTarget)
        s.cutKeystroke = cutKeystroke.isChecked
        fill()

        // La accion del intent solo se lee al registrar el receiver.
        TallyService.stop(this)
        startBubble()
        Toast.makeText(this, R.string.guardado, Toast.LENGTH_SHORT).show()
    }

    /** Acepta coma o punto: en piso se teclea como se habla. */
    private fun kgOf(field: EditText, fallback: Double): Double {
        val value = field.text.toString().trim().replace(',', '.').toDoubleOrNull()
        return if (value == null || value < 0.0) fallback else value
    }

    /**
     * Muestra la ultima lectura hecha desde la burbuja, con el veredicto de si
     * el ancla calzo. No se lee en vivo: desde aqui la ventana activa es esta
     * pantalla y se leerian los textos de la propia app.
     */
    private fun readScreen() {
        screenTexts.visibility = View.VISIBLE
        val texts = s.lastScreenTexts.split("|").filter { it.isNotBlank() }
        if (texts.isEmpty()) {
            screenTexts.text = getString(R.string.pantalla_sin_captura)
            return
        }
        val anchor = targetAnchor.text.toString().trim().ifEmpty { TargetScraper.DEFAULT_ANCHOR }
        val found = TargetScraper.findTarget(texts, anchor)
        val verdict = if (found == null) {
            getString(R.string.pantalla_no_detectado)
        } else {
            getString(R.string.pantalla_detectado, WeightParser.format(found, s.comma))
        }
        screenTexts.text = verdict + "\n\n" +
            texts.joinToString(separator = "\n") { "\u00b7 " + it }
    }

    private fun runTest() {
        val raw = test.text.toString()
        val r = WeightParser.parse(raw, s.offset, s.len)
        testResult.text = if (r == null) {
            getString(R.string.sin_peso, WeightParser.clean(raw))
        } else {
            WeightParser.format(r.kg, s.comma) + " kg  \u00b7  " + r.source +
                (r.warn?.let { "  \u00b7  $it" } ?: "")
        }
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        if (granted != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }
    }

    private fun refresh() {
        syncing = true
        swBubble.isChecked = TallyService.isRunning
        swTwoInserts.isChecked = s.insertsPerTask > 1
        swCamera.isChecked = s.cameraEnabled
        syncing = false

        // La tarjeta de permisos solo existe cuando falta algo, y solo con el
        // boton de lo que falta.
        val overlayOk = SysSettings.canDrawOverlays(this)
        val accessOk = InsertAccessibilityService.isRunning
        val missing = mutableListOf<String>()
        if (!overlayOk) missing += getString(R.string.falta_permiso_overlay)
        if (!accessOk) missing += getString(R.string.falta_permiso_acc)
        permCard.visibility = if (missing.isEmpty()) View.GONE else View.VISIBLE
        permText.text = missing.joinToString("\n")
        findViewById<View>(R.id.btnOverlay).visibility = if (overlayOk) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnAccess).visibility = if (accessOk) View.GONE else View.VISIBLE

        advancedState.setText(if (unlocked) R.string.avanzado_ocultar else R.string.avanzado_bloqueado)
        if (!unlocked) return

        val yes = getString(R.string.si)
        val no = getString(R.string.no)
        status.text = listOf(
            getString(R.string.st_overlay, if (overlayOk) yes else no),
            getString(R.string.st_accesibilidad, if (accessOk) yes else no),
            getString(R.string.st_datawedge, if (DataWedge.isAvailable(this)) yes else no),
            getString(R.string.st_burbuja, if (TallyService.isRunning) yes else no),
            getString(R.string.st_voz, if (Voice.isReady) yes else no),
            getString(R.string.st_wms, s.wmsPackage.ifEmpty { getString(R.string.ninguno) }),
            getString(R.string.st_dw, s.lastDataWedgeResult.ifEmpty { getString(R.string.ninguno) }),
            getString(
                R.string.st_ultimo_intent,
                s.lastIntentKeys.ifEmpty { getString(R.string.ninguno) }
            )
        ).joinToString("\n")
    }
}
