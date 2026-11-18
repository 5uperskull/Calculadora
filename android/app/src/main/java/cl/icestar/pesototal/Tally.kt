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
        val manual: Boolean = false,
        /** Articulo|lote|vencimiento (ver [Label]). "" = etiqueta sin cadena. */
        val chain: String = ""
    )

    /** Subtotal de una cadena, para la lista de la burbuja. */
    data class Group(val key: String, val kg: Double, val count: Int)

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

    /** Suma de lo que abarca la seleccion: una cadena, un articulo o todo. */
    fun total(selection: String): Double =
        WeightParser.round3(lines.filter { Label.matches(it.chain, selection) }.sumOf { it.kg })

    fun count(selection: String): Int = lines.count { Label.matches(it.chain, selection) }

    /** Una fila por cadena, la de la ultima lectura primero. */
    fun groups(): List<Group> = lines.reversed().map { it.chain }.distinct().map { key ->
        val own = lines.filter { it.chain == key }
        Group(key, WeightParser.round3(own.sumOf { it.kg }), own.size)
    }

    /** Cadena de la ultima linea, o null si no hay lineas. */
    val lastChain: String? get() = lines.lastOrNull()?.chain

    /** Devuelve el mismo listener para poder darlo de baja despues. */
    fun onChange(listener: () -> Unit): () -> Unit {
        listeners += listener
        return listener
    }

    fun removeChange(listener: () -> Unit) {
        listeners -= listener
    }

    /**
     * Un codigo ya leido se suma igual, marcado como repetido: el operario oye
     * el aviso y decide. Si fue un doble disparo, Deshacer o la X lo quitan.
     *
     * @return false si era repetido (se sumo de todos modos).
     */
    fun add(kg: Double, code: String, chain: String = ""): Boolean {
        val repeated = lines.any { !it.manual && it.code == code }
        lines += Line(WeightParser.round3(kg), code, duplicate = repeated, chain = chain)
        undoSnapshot = emptyList()
        commit()
        return !repeated
    }

    /**
     * Peso tecleado a mano, para etiquetas rotas o ilegibles.
     *
     * Nunca se marca duplicado: dos pesos iguales escritos a mano son dos cajas
     * distintas, no una lectura repetida.
     */
    fun addManual(kg: Double, chain: String = "") {
        lines += Line(
            WeightParser.round3(kg), MANUAL_CODE, duplicate = false, manual = true, chain = chain
        )
        undoSnapshot = emptyList()
        commit()
    }

    /**
     * Una sola tecla Deshacer: si lo ultimo fue vaciar (todo o una cadena),
     * lo devuelve; si no, quita la ultima linea.
     */
    fun undo(): Boolean {
        if (undoSnapshot.isNotEmpty()) {
            lines.addAll(undoSnapshot)
            undoSnapshot = emptyList()
            commit()
            return true
        }
        if (lines.isNotEmpty()) {
            lines.removeAt(lines.size - 1)
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
        undoSnapshot = emptyList()
        commit()
    }

    /**
     * Cierra solo lo insertado: en un pallet mixto las otras cadenas siguen
     * pendientes y no se pueden perder.
     */
    fun clear(selection: String) {
        if (selection == Label.ALL) return reset()
        val gone = lines.filter { Label.matches(it.chain, selection) }
        if (gone.isEmpty()) return
        lines.removeAll(gone)
        undoSnapshot = gone
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
        "${it.kg}\u0001${it.code}\u0001${it.duplicate}\u0001${it.manual}\u0001${it.chain}"
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
                val chain = parts.getOrNull(4).orEmpty()
                lines += Line(kg, parts[1], parts[2].toBoolean(), manual, chain)
            }
        }
    }
}
