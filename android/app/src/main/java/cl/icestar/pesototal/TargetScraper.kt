package cl.icestar.pesototal

/**
 * Saca el peso pedido de los textos que se ven en la pantalla del WMS.
 *
 * Sin Android: se prueba en la JVM contra la pantalla real de easyWMS.
 *
 * Esa pantalla trae "Cantidad pendiente" y "Cantidad restante" una debajo de la
 * otra, y ademas una ubicacion de 18 digitos. Por eso se ancla al texto exacto
 * y se lee solo lo que viene DESPUES del ancla.
 */
object TargetScraper {

    const val DEFAULT_ANCHOR = "Cantidad pendiente"

    private val NUMBER = Regex("""(\d+(?:[.,]\d+)?)""")

    fun findTarget(texts: List<String>, anchor: String = DEFAULT_ANCHOR): Double? {
        val needle = anchor.trim()
        if (needle.isEmpty()) return null

        texts.forEachIndexed { index, text ->
            val at = text.indexOf(needle, ignoreCase = true)
            if (at < 0) return@forEachIndexed

            numberIn(text.substring(at + needle.length))?.let { return it }

            // La etiqueta y el valor pueden venir en nodos separados del arbol.
            for (next in index + 1 until minOf(texts.size, index + 3)) {
                numberIn(texts[next])?.let { return it }
            }
        }
        return null
    }

    // Con y sin tilde: segun la fuente del terminal la "í" puede llegar distinta.
    private val ARTICLE_ANCHOR = Regex("""Art[ií]culo\s*:?""", RegexOption.IGNORE_CASE)
    private val TOKEN = Regex("""[^\s:]+""")

    /**
     * Codigo del articulo que pide el WMS: "Artículo: AYUDA_10 - PRUEBA..." da
     * "AYUDA_10". Solo el codigo, la descripcion que sigue al guion se ignora.
     */
    fun findArticle(texts: List<String>): String? {
        texts.forEachIndexed { index, text ->
            val anchor = ARTICLE_ANCHOR.find(text) ?: return@forEachIndexed
            val after = text.substring(anchor.range.last + 1)
            val candidates = listOf(after) + texts.drop(index + 1).take(1)
            for (candidate in candidates) {
                val token = TOKEN.find(candidate)?.value?.trimEnd('-')
                if (!token.isNullOrEmpty()) return Label.normalizeArticle(token)
            }
        }
        return null
    }

    /**
     * Un unico separador se toma como decimal, sea coma o punto.
     *
     * ponytail: un separador de miles ("1.234" por 1234 kg) se leeria como
     * 1,234 kg. Se ve a simple vista en la burbuja y el objetivo siempre se
     * puede teclear; distinguirlo a ciegas romperia "40.500" como 40,5 kg.
     */
    private fun numberIn(text: String): Double? =
        NUMBER.find(text)?.value?.replace(',', '.')?.toDoubleOrNull()
}
