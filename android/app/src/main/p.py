import io

def edit(path, pairs):
    s = io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        if new in s:
            continue
        assert old in s, "NO ENCONTRADO en %s -> %r" % (path, old[:70])
        s = s.replace(old, new, 1)
    io.open(path, 'w', encoding='utf-8').write(s)

edit('res/values/strings.xml', [(
'''    <string name="objetivo_borrado">Objetivo borrado</string>''',
'''    <string name="objetivo_borrado">Objetivo borrado</string>
    <string name="objetivo_detectado">Objetivo leído del WMS: %1$s kg</string>
    <string name="pantalla_detectado">Objetivo detectado: %1$s kg</string>
    <string name="pantalla_no_detectado">No se encontró el objetivo. Revisa el texto de anclaje.</string>''')])

edit('res/layout/activity_main.xml', [(
'''        <CheckBox
            android:id="@+id/screenTarget"''',
'''        <EditText
            android:id="@+id/targetAnchor"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:background="@drawable/bg_field"
            android:hint="Texto antes del peso pedido en el WMS"
            android:inputType="text"
            android:padding="10dp"
            android:textColor="@color/txt"
            android:textSize="13sp" />

        <CheckBox
            android:id="@+id/screenTarget"''')])

edit('java/cl/icestar/pesototal/MainActivity.kt', [
('''    private lateinit var near: EditText''',
'''    private lateinit var near: EditText
    private lateinit var targetAnchor: EditText'''),

('''        near = findViewById(R.id.near)''',
'''        near = findViewById(R.id.near)
        targetAnchor = findViewById(R.id.targetAnchor)'''),

('''        near.setText(WeightParser.format(s.nearKg, s.comma))''',
'''        near.setText(WeightParser.format(s.nearKg, s.comma))
        targetAnchor.setText(s.targetAnchor)'''),

('''        s.nearKg = kgOf(near, Target.DEFAULT_NEAR_KG)''',
'''        s.nearKg = kgOf(near, Target.DEFAULT_NEAR_KG)
        s.targetAnchor = targetAnchor.text.toString().trim()
            .ifEmpty { TargetScraper.DEFAULT_ANCHOR }'''),

# el diagnostico dice ademas que saco de ahi
('''        val texts = InsertAccessibilityService.readScreenTexts()
        screenTexts.text = if (texts.isEmpty()) {
            getString(R.string.pantalla_vacia)
        } else {
            texts.joinToString(separator = "\n") { "\u00b7 " + it }
        }''',
'''        val texts = InsertAccessibilityService.readScreenTexts()
        if (texts.isEmpty()) {
            screenTexts.text = getString(R.string.pantalla_vacia)
            return
        }
        // Ademas de la lista, el veredicto: sin esto hay que comparar a ojo si
        // el ancla calza con lo que muestra el WMS.
        val anchor = targetAnchor.text.toString().trim().ifEmpty { TargetScraper.DEFAULT_ANCHOR }
        val found = TargetScraper.findTarget(texts, anchor)
        val verdict = if (found == null) {
            getString(R.string.pantalla_no_detectado)
        } else {
            getString(R.string.pantalla_detectado, WeightParser.format(found, s.comma))
        }
        screenTexts.text = verdict + "\n\n" +
            texts.joinToString(separator = "\n") { "\u00b7 " + it }'''),
])
print("ajustes y diagnostico listos")
