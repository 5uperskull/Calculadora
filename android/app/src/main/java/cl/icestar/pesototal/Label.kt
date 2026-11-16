package cl.icestar.pesototal

/**
 * Articulo, lote y vencimiento de la etiqueta propia, con las mismas reglas que
 * la PLANTILLA de Excel del CD. Sin Android: se prueba en la JVM.
 *
 * Ejemplo: 92 1000979 3103011730 10 264430961R 17 261127
 *   articulo = MID(3,7) como numero        -> 1000979
 *   lote     = 9 antes de la letra + letra -> 264430961R
 *   vence    = 6 tras letra+17 (AAMMDD)    -> 261127
 *
 * La cadena es la clave por la que se consolidan las sumas: dos cadenas
 * distintas nunca se suman juntas.
 */
object Label {

    /** Posicion 0-based del AI 310n: justo despues de 92 + articulo de 7. */
    const val WEIGHT_AT = 9

    const val SEP = "|"

    /** Seleccion que abarca todas las lineas: etiquetas sin cadena o arranque. */
    const val ALL = "*"

    private val ARTICLE = Regex("""^92(\d{7})""")
    private val LOT_LETTER = Regex("[RCHX]", RegexOption.IGNORE_CASE)

    // La plantilla busca r17/c17/h17; la X se acepta tambien a pedido del CD,
    // porque la plantilla si la acepta para el lote.
    private val EXPIRY = Regex("""[RCHX]17(\d{6})""", RegexOption.IGNORE_CASE)

    data class Chain(val article: String, val lot: String, val expiry: String) {
        val key: String get() = listOf(article, lot, expiry).joinToString(SEP)
    }

    /** null si el codigo no es una etiqueta propia (no empieza con 92 + 7 digitos). */
    fun parse(code: String): Chain? {
        val article = ARTICLE.find(code)?.groupValues?.get(1)?.let(::normalizeArticle)
            ?: return null
        val letter = LOT_LETTER.find(code, WEIGHT_AT)?.range?.first
        val lot = letter?.takeIf { it >= 9 }?.let { code.substring(it - 9, it + 1).uppercase() }
        val expiry = EXPIRY.find(code, WEIGHT_AT)?.groupValues?.get(1)
        return Chain(article, lot.orEmpty(), expiry.orEmpty())
    }

    /** Igual que el -- del Excel: 0700334 y 700334 son el mismo articulo. */
    fun normalizeArticle(raw: String): String {
        val t = raw.trim()
        return if (t.isNotEmpty() && t.all { it.isDigit() }) t.trimStart('0').ifEmpty { "0" } else t
    }

    /**
     * Una seleccion puede ser una cadena completa o solo un articulo (cuando
     * el articulo lo pide la pantalla del WMS: vale cualquier lote).
     */
    fun matches(key: String, selection: String): Boolean =
        selection == ALL || key == selection || key.startsWith(selection + SEP)

    /** AAMMDD de la etiqueta a DD/MM/AAAA, que es como lo lee el operario. */
    fun formatExpiry(raw: String): String =
        if (raw.length == 6 && raw.all { it.isDigit() }) {
            raw.substring(4, 6) + "/" + raw.substring(2, 4) + "/20" + raw.substring(0, 2)
        } else {
            raw
        }

    /** "1000979 · 264430961R · 261127" para la burbuja. */
    fun display(key: String, none: String): String =
        if (key.isEmpty()) none else key.split(SEP).filter { it.isNotEmpty() }.joinToString(" · ")
}
