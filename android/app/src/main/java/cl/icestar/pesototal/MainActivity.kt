package cl.icestar.pesototal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
    private lateinit var profileWms: EditText
    private lateinit var profileSum: EditText
    private lateinit var comma: CheckBox
    private lateinit var resetAfter: CheckBox
    private lateinit var edgeBar: CheckBox
    private lateinit var useProfiles: CheckBox
    private lateinit var test: EditText
    private lateinit var testResult: TextView

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_main)
        s = PesoApp.instance.settings

        status = findViewById(R.id.status)
        action = findViewById(R.id.action)
        extra = findViewById(R.id.extra)
        offset = findViewById(R.id.offset)
        len = findViewById(R.id.len)
        alpha = findViewById(R.id.alpha)
        profileWms = findViewById(R.id.profileWms)
        profileSum = findViewById(R.id.profileSum)
        comma = findViewById(R.id.comma)
        resetAfter = findViewById(R.id.resetAfter)
        edgeBar = findViewById(R.id.edgeBar)
        useProfiles = findViewById(R.id.useProfiles)
        test = findViewById(R.id.test)
        testResult = findViewById(R.id.testResult)

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

        askNotifications()
        fill()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun fill() {
        action.setText(s.scanAction)
        extra.setText(s.scanExtra)
        offset.setText(s.offset.toString())
        len.setText(s.len.toString())
        alpha.setText(s.alpha.toString())
        profileWms.setText(s.profileWms)
        profileSum.setText(s.profileSum)
        comma.isChecked = s.comma
        resetAfter.isChecked = s.resetAfterInsert
        edgeBar.isChecked = s.edgeBar
        useProfiles.isChecked = s.useProfiles
    }

    private fun save() {
        s.scanAction = action.text.toString().trim().ifEmpty { Settings.DEFAULT_ACTION }
        s.scanExtra = extra.text.toString().trim().ifEmpty { Settings.ZEBRA_EXTRA }
        s.offset = offset.text.toString().toIntOrNull() ?: WeightParser.DEFAULT_OFFSET
        s.len = len.text.toString().toIntOrNull() ?: WeightParser.DEFAULT_LEN
        s.alpha = alpha.text.toString().toIntOrNull() ?: 75
        s.profileWms = profileWms.text.toString().trim().ifEmpty { "WMS" }
        s.profileSum = profileSum.text.toString().trim().ifEmpty { "SUMA" }
        s.comma = comma.isChecked
        s.resetAfterInsert = resetAfter.isChecked
        s.edgeBar = edgeBar.isChecked
        s.useProfiles = useProfiles.isChecked
        fill()

        // La accion del intent solo se lee al registrar el receiver.
        TallyService.stop(this)
        startBubble()
        Toast.makeText(this, R.string.guardado, Toast.LENGTH_SHORT).show()
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
            getString(R.string.st_datawedge, if (DataWedge.isAvailable(this)) yes else no)
        ).joinToString("\n")
    }
}
