package cl.icestar.pesototal

import cl.icestar.pesototal.Target.State
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetTest {

    // Pedido de 40 kg, banda de 0,5 kg, aviso de cercania a 2 kg.
    private fun state(total: Double) = Target.state(total, 40.0, 0.5, 2.0)

    @Test
    fun `sin objetivo no opina`() {
        assertEquals(State.SIN_OBJETIVO, Target.state(12.0, 0.0, 0.5, 2.0))
        assertEquals(State.SIN_OBJETIVO, Target.state(12.0, -1.0, 0.5, 2.0))
    }

    @Test
    fun `lejos del objetivo falta`() {
        assertEquals(State.FALTA, state(0.0))
        assertEquals(State.FALTA, state(37.9))
    }

    @Test
    fun `dentro del aviso de cercania`() {
        assertEquals(State.CERCA, state(38.0))
        assertEquals(State.CERCA, state(39.4))
    }

    @Test
    fun `la banda en peso es simetrica`() {
        assertEquals(State.EN_PESO, state(39.5))
        assertEquals(State.EN_PESO, state(40.0))
        assertEquals(State.EN_PESO, state(40.5))
    }

    @Test
    fun `pasada la banda esta excedido`() {
        assertEquals(State.EXCEDIDO, state(40.6))
        assertEquals(State.EXCEDIDO, state(80.0))
    }

    @Test
    fun `sin margen cualquier exceso cuenta`() {
        assertEquals(State.EN_PESO, Target.state(40.0, 40.0, 0.0, 2.0))
        assertEquals(State.EXCEDIDO, Target.state(40.001, 40.0, 0.0, 2.0))
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
