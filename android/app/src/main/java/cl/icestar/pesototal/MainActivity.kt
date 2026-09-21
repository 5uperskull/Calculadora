package cl.icestar.pesototal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.provider.Settings as SysSettings

/** Pantalla de configuracion. En piso no se abre: solo al instalar o calibrar. */
class MainActivity : AppCompatActivity() {

    private lateinit var s: Settings

    private lateinit var status: TextView
    private lateinit var action: EditText
    private lateinit var extra: EditText
    private lateinit var offset: EditText
    private lateinit var len: EditText
    private lateinit var alpha: EditText
    private lateinit var tolerance: EditText
    private lateinit var near: EditText
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

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_main)
        s = PesoApp.instance.settings

        status = findViewById(R.id.status)
        showVersion()
        action = findViewById(R.id.action)
        extra = findViewById(R.id.extra)
        offset = findViewById(R.id.offset)
        len = findViewById(R.id.len)
        alpha = findViewById(R.id.alpha)
        tolerance = findViewById(R.id.tolerance)
        near = findViewById(R.id.near)
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
        findViewById<Button>(R.id.btnStart).setOnClickListener { startBubble() }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            TallyService.stop(this)
            refresh()
        }
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
            // Arranca el motor aunque la burbuja este apagada: probar la voz es
            // justo lo que se hace antes de desplegar.
            Voice.start(this)
            Voice.say(getString(R.string.voz_prueba))
            status.postDelayed({ refresh() }, 1500)
        }

        askNotifications()
        fill()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        // Si la burbuja no esta corriendo, el motor de voz lo abrio esta
        // pantalla al probarlo y no lo cerraria nadie mas.
        if (!TallyService.isRunning) Voice.stop()
        super.onDestroy()
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
        s.profileWms = profileWms.text.toString().trim().ifEmpty { "WMS" }
        s.comma = comma.isChecked
        s.resetAfterInsert = resetAfter.isChecked
        s.edgeBar = edgeBar.isChecked
        s.sound = sound.isChecked
        s.screenTarget = screenTarget.isChecked
        // El alcance del servicio se amplia o se reduce aqui mismo, no al
        // reinstalar: asi apagar la casilla surte efecto de inmediato.
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
     * Diagnostico: lista los textos que el servicio ve en la pantalla de al
     * lado. Es lo que permite fijar de donde sacar el objetivo mirando el WMS
     * real, en vez de adivinar su maquetacion.
     */
    private fun readScreen() {
        screenTexts.visibility = View.VISIBLE
        if (!InsertAccessibilityService.isRunning) {
            screenTexts.text = getString(R.string.pantalla_sin_accesibilidad)
            return
        }
        val texts = InsertAccessibilityService.readScreenTexts()
        screenTexts.text = if (texts.isEmpty()) {
            getString(R.string.pantalla_vacia)
        } else {
            texts.joinToString(separator = "\n") { "\u00b7 " + it }
        }
    }

    private fun runTest() {
        val raw = test.text.toString()
        val r = WeightParser.parse(raw, s.offset, s.len)
        testResult.text = if (r == null) {
            getString(R.string.sin_peso, WeightParser.clean(raw))
        } else {
            WeightParser.format(r.kg, s.comma) + " kg  ·  " + r.source +
                (r.warn?.let { "  ·  $it" } ?: "")
        }
    }

    private fun startBubble() {
        if (!SysSettings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.falta_overlay, Toast.LENGTH_LONG).show()
            return
        }
        TallyService.start(this)
        refresh()
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
        val yes = getString(R.string.si)
        val no = getString(R.string.no)
        status.text = listOf(
            getString(R.string.st_overlay, if (SysSettings.canDrawOverlays(this)) yes else no),
            getString(
                R.string.st_accesibilidad,
                if (InsertAccessibilityService.isRunning) yes else no
            ),
            getString(R.string.st_datawedge, if (DataWedge.isAvailable(this)) yes else no),
            getString(R.string.st_burbuja, if (TallyService.isRunning) yes else no),
            getString(R.string.st_voz, if (Voice.isReady) yes else no),
            getString(
                R.string.st_ultimo_intent,
                s.lastIntentKeys.ifEmpty { getString(R.string.ninguno) }
            )
        ).joinToString("\n")
    }
}
