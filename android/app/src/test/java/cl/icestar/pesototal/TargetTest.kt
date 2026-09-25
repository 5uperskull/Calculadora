package cl.icestar.pesototal

import cl.icestar.pesototal.Target.State
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetTest {

    // Pedido de 40 kg, tolerancia de 2 kg solo hacia abajo, aviso 2 kg antes.
    private fun state(total: Double) = Target.state(total, 40.0, 2.0, 2.0)

    @Test
    fun `sin objetivo no opina`() {
        assertEquals(State.SIN_OBJETIVO, Target.state(12.0, 0.0, 2.0, 2.0))
        assertEquals(State.SIN_OBJETIVO, Target.state(12.0, -1.0, 2.0, 2.0))
    }

    @Test
    fun `lejos del objetivo falta`() {
        assertEquals(State.FALTA, state(0.0))
        assertEquals(State.FALTA, state(35.9))
    }

    @Test
    fun `aviso de cercania antes de la banda`() {
        assertEquals(State.CERCA, state(36.0))
        assertEquals(State.CERCA, state(37.9))
    }

    @Test
    fun `en peso hasta 2 kg por debajo`() {
        assertEquals(State.EN_PESO, state(38.0))
        assertEquals(State.EN_PESO, state(39.2))
        assertEquals(State.EN_PESO, state(40.0))
    }

    @Test
    fun `pasarse un gramo ya es exceso`() {
        assertEquals(State.EXCEDIDO, state(40.001))
        assertEquals(State.EXCEDIDO, state(40.5))
        assertEquals(State.EXCEDIDO, state(80.0))
    }

    @Test
    fun `sin tolerancia solo el valor exacto esta en peso`() {
        assertEquals(State.EN_PESO, Target.state(40.0, 40.0, 0.0, 2.0))
        assertEquals(State.CERCA, Target.state(39.999, 40.0, 0.0, 2.0))
    }

    @Test
    fun `un margen negativo no invierte la logica`() {
        assertEquals(State.EN_PESO, Target.state(40.0, 40.0, -5.0, -5.0))
        assertEquals(State.EXCEDIDO, Target.state(41.0, 40.0, -5.0, -5.0))
    }

    @Test
    fun `lo que falta se redondea a tres decimales`() {
        assertEquals(5.3, Target.remaining(34.7, 40.0), 0.0)
        assertEquals(-1.2, Target.remaining(41.2, 40.0), 0.0)
    }
}
