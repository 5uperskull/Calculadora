package cl.icestar.pesototal

/**
 * Estado de la suma frente al peso que pide el WMS.
 *
 * Sin estado y sin Android: es la regla de negocio y se prueba en la JVM.
 */
object Target {

    enum class State { SIN_OBJETIVO, FALTA, CERCA, EN_PESO, EXCEDIDO }

    const val DEFAULT_TOLERANCE_KG = 0.5
    const val DEFAULT_NEAR_KG = 2.0

    /**
     * La banda "en peso" es simetrica alrededor del objetivo, y "cerca" es la
     * franja de aviso justo por debajo de esa banda. Los margenes se saturan a
     * cero: un margen negativo escrito por error no debe invertir la logica.
     */
    fun state(total: Double, target: Double, toleranceKg: Double, nearKg: Double): State {
        if (target <= 0.0) return State.SIN_OBJETIVO
        val tolerance = maxOf(0.0, toleranceKg)
        return when {
            total > target + tolerance -> State.EXCEDIDO
            total >= target - tolerance -> State.EN_PESO
            target - total <= maxOf(0.0, nearKg) -> State.CERCA
            else -> State.FALTA
        }
    }

    /** Positivo = falta ese peso. Negativo = se paso por esa cantidad. */
    fun remaining(total: Double, target: Double): Double =
        WeightParser.round3(target - total)
}
