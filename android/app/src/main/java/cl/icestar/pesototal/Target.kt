package cl.icestar.pesototal

/**
 * Estado de la suma frente al peso que pide el WMS.
 *
 * Sin estado y sin Android: es la regla de negocio y se prueba en la JVM.
 */
object Target {

    enum class State { SIN_OBJETIVO, FALTA, CERCA, EN_PESO, EXCEDIDO }

    /** La tolerancia es solo hacia abajo. */
    const val DEFAULT_TOLERANCE_KG = 2.0
    const val DEFAULT_NEAR_KG = 2.0

    /**
     * El pedido admite quedar corto hasta `toleranceKg`, pero pasarse por un
     * gramo ya es exceso: no hay tolerancia hacia arriba.
     *
     * "Cerca" es la franja de aviso justo antes de entrar en esa banda. Los
     * margenes se saturan a cero: uno negativo escrito por error no debe
     * invertir la logica.
     */
    fun state(total: Double, target: Double, toleranceKg: Double, nearKg: Double): State {
        if (target <= 0.0) return State.SIN_OBJETIVO
        // Sin nada escaneado nunca se esta en peso. Con un objetivo menor o
        // igual a la tolerancia (2 kg contra 2 kg) la banda empieza en cero, y
        // cero cumplia: la burbuja daba "completo" antes del primer escaneo.
        if (total <= 0.0) return State.FALTA
        val bandStart = target - maxOf(0.0, toleranceKg)
        return when {
            total > target -> State.EXCEDIDO
            total >= bandStart -> State.EN_PESO
            bandStart - total <= maxOf(0.0, nearKg) -> State.CERCA
            else -> State.FALTA
        }
    }

    /** Positivo = falta ese peso. Negativo = se paso por esa cantidad. */
    fun remaining(total: Double, target: Double): Double =
        WeightParser.round3(target - total)
}
