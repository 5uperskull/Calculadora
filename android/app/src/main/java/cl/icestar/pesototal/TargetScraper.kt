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
