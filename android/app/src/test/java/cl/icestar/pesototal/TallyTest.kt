package cl.icestar.pesototal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TallyTest {

    private class MemStore(var data: String? = null) : Tally.Store {
        override fun load() = data
        override fun save(d: String) { data = d }
    }

    @Test
    fun `suma tres pesos`() {
        val t = Tally(MemStore())
        t.add(11.5, "A"); t.add(11.5, "B"); t.add(11.7, "C")
        assertEquals(34.7, t.total, 0.0)
        assertEquals(3, t.count)
    }

    @Test
    fun `deshacer quita la ultima linea`() {
        val t = Tally(MemStore())
        t.add(11.5, "A"); t.add(3.0, "B")
        assertTrue(t.undo())
        assertEquals(11.5, t.total, 0.0)
        assertEquals(1, t.count)
    }

    @Test
    fun `reiniciar vacia y deshacer lo devuelve`() {
        val t = Tally(MemStore())
        t.add(11.5, "A"); t.add(3.0, "B")
        t.reset()
        assertEquals(0.0, t.total, 0.0)
        assertTrue(t.undo())
        assertEquals(14.5, t.total, 0.0)
        assertEquals(2, t.count)
    }

    @Test
    fun `deshacer sin nada que deshacer no truena`() {
        val t = Tally(MemStore())
        assertFalse(t.undo())
    }

    @Test
    fun `borrar una linea concreta`() {
        val t = Tally(MemStore())
        t.add(1.0, "A"); t.add(2.0, "B"); t.add(3.0, "C")
        t.removeAt(1)
        assertEquals(4.0, t.total, 0.0)
        t.removeAt(99) // fuera de rango: no hace nada
        assertEquals(4.0, t.total, 0.0)
    }

    @Test
    fun `codigo repetido se suma igual pero queda marcado`() {
        val t = Tally(MemStore())
        t.add(11.5, "A"); t.add(11.5, "A")
        assertEquals(23.0, t.total, 0.0)
        assertFalse(t.snapshot()[0].duplicate)
        assertTrue(t.snapshot()[1].duplicate)
    }

    @Test
    fun `redondea la suma a tres decimales`() {
        val t = Tally(MemStore())
        t.add(0.1, "A"); t.add(0.2, "B")
        assertEquals(0.3, t.total, 0.0)
    }

    @Test
    fun `persiste ida y vuelta`() {
        val store = MemStore()
        val t = Tally(store)
        t.add(11.5, "921016103310301150010262960731r17260817")
        t.add(3.25, "OTRO")

        val revivido = Tally(store)
        assertEquals(14.75, revivido.total, 0.0)
        assertEquals(2, revivido.count)
        assertEquals("OTRO", revivido.snapshot()[1].code)
    }

    @Test
    fun `un listener dado de baja deja de recibir`() {
        val t = Tally(MemStore())
        var avisos = 0
        val l = t.onChange { avisos++ }
        t.add(1.0, "A")
        assertEquals(1, avisos)

        t.removeChange(l)
        t.add(2.0, "B")
        assertEquals(1, avisos)
    }
}
