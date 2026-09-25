package cl.icestar.pesototal

/**
 * Acumulador de pesos. Sin dependencias de Android para poder probarlo en la
 * JVM; la persistencia entra por [Store].
 */
class Tally(private val store: Store) {

    interface Store {
        fun load(): String?
        fun save(data: String)
    }

    data class Line(
        val kg: Double,
        val code: String,
        val duplicate: Boolean,
        /** Tecleado a mano porque la etiqueta no se podia leer. */
        val manual: Boolean = false
    )

    private val lines = mutableListOf<Line>()

    /** Copia previa al ultimo reinicio, para poder deshacerlo. */
    private var undoSnapshot: List<Line> = emptyList()

    private val listeners = mutableListOf<() -> Unit>()

    init {
        load()
    }

    val total: Double get() = WeightParser.round3(lines.sumOf { it.kg })
    val count: Int get() = lines.size
    val isEmpty: Boolean get() = lines.isEmpty()
    val canUndo: Boolean get() = lines.isNotEmpty() || undoSnapshot.isNotEmpty()

    fun snapshot(): List<Line> = lines.toList()

    /** Devuelve el mismo listener para poder darlo de baja despues. */
    fun onChange(listener: () -> Unit): () -> Unit {
        listeners += listener
        return listener
    }

    fun removeChange(listener: () -> Unit) {
        listeners -= listener
    }

    /**
     * Un codigo ya leido no se suma: solo se avisa. En piso un doble disparo es
     * mucho mas comun que dos cajas con la misma etiqueta.
     *
     * @return false si era repetido y no se sumo.
     */
    fun add(kg: Double, code: String): Boolean {
        if (lines.any { !it.manual && it.code == code }) return false
        lines += Line(WeightParser.round3(kg), code, duplicate = false)
        undoSnapshot = emptyList()
        commit()
        return true
    }

    /**
     * Peso tecleado a mano, para etiquetas rotas o ilegibles.
     *
     * Nunca se marca duplicado: dos pesos iguales escritos a mano son dos cajas
     * distintas, no una lectura repetida.
     */
    fun addManual(kg: Double) {
        lines += Line(WeightParser.round3(kg), MANUAL_CODE, duplicate = false, manual = true)
        undoSnapshot = emptyList()
        commit()
    }

    /**
     * Una sola tecla Deshacer: si hay lineas quita la ultima, y si la suma
     * quedo vacia por un reinicio, la restaura.
     */
    fun undo(): Boolean {
        if (lines.isNotEmpty()) {
            lines.removeAt(lines.size - 1)
            commit()
            return true
        }
        if (undoSnapshot.isNotEmpty()) {
            lines.addAll(undoSnapshot)
            undoSnapshot = emptyList()
            commit()
            return true
        }
        return false
    }

    companion object {
        const val MANUAL_CODE = "MANUAL"
    }

    fun removeAt(index: Int) {
        if (index !in lines.indices) return
        lines.removeAt(index)
        commit()
    }

    fun reset() {
        if (lines.isEmpty()) return
        undoSnapshot = lines.toList()
        lines.clear()
        commit()
    }

    private fun commit() {
        store.save(serialize())
        listeners.forEach { it() }
    }

    // Formato propio en vez de JSON: el separador es un control char, y
    // WeightParser.clean() los borra, asi que jamas puede aparecer en un codigo.
    private fun serialize(): String = lines.joinToString("\n") {
        "${it.kg}\u0001${it.code}\u0001${it.duplicate}\u0001${it.manual}"
    }

    private fun load() {
        val raw = store.load().orEmpty()
        if (raw.isBlank()) return
        raw.split("\n").forEach { row ->
            val parts = row.split("\u0001")
            // >= 3 y no == 3: lo guardado antes del peso manual traia 3 campos.
            if (parts.size >= 3) {
                val kg = parts[0].toDoubleOrNull() ?: return@forEach
                val manual = parts.getOrNull(3)?.toBoolean() ?: false
                lines += Line(kg, parts[1], parts[2].toBoolean(), manual)
            }
        }
    }
}
