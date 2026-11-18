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
    fun `codigo repetido se suma igual y queda marcado`() {
        val t = Tally(MemStore())
        assertTrue(t.add(11.5, "A"))
        assertFalse(t.add(11.5, "A"))
        assertEquals(23.0, t.total, 0.0)
        assertEquals(2, t.count)
        assertFalse(t.snapshot()[0].duplicate)
        assertTrue(t.snapshot()[1].duplicate)
    }

    @Test
    fun `tras deshacer el mismo codigo vuelve a entrar`() {
        val t = Tally(MemStore())
        t.add(11.5, "A")
        t.undo()
        assertTrue(t.add(11.5, "A"))
        assertEquals(11.5, t.total, 0.0)
    }

    @Test
    fun `un peso manual no bloquea una etiqueta real`() {
        val t = Tally(MemStore())
        t.addManual(11.5)
        assertTrue(t.add(11.5, Tally.MANUAL_CODE))
        assertEquals(23.0, t.total, 0.0)
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

    @Test
    fun `peso manual se suma y nunca se marca duplicado`() {
        val t = Tally(MemStore())
        t.addManual(11.5)
        t.addManual(11.5)
        assertEquals(23.0, t.total, 0.0)
        assertFalse(t.snapshot()[0].duplicate)
        assertFalse(t.snapshot()[1].duplicate)
        assertTrue(t.snapshot()[1].manual)
    }

    @Test
    fun `el peso manual sobrevive al guardado`() {
        val store = MemStore()
        val t = Tally(store)
        t.add(11.5, "ETIQUETA")
        t.addManual(3.25)

        val revivido = Tally(store)
        assertEquals(14.75, revivido.total, 0.0)
        assertFalse(revivido.snapshot()[0].manual)
        assertTrue(revivido.snapshot()[1].manual)
    }

    @Test
    fun `lee lineas guardadas con el formato viejo de tres campos`() {
        val store = MemStore("11.5\u0001ETIQUETA\u0001false")
        val t = Tally(store)
        assertEquals(11.5, t.total, 0.0)
        assertFalse(t.snapshot()[0].manual)
    }

    @Test
    fun `dos cadenas nunca se suman juntas`() {
        val t = Tally(MemStore())
        t.add(10.0, "A", "1|L1|261127"); t.add(5.0, "B", "2|L2|261117"); t.add(2.5, "C", "1|L1|261127")
        assertEquals(12.5, t.total("1|L1|261127"), 0.0)
        assertEquals(5.0, t.total("2|L2|261117"), 0.0)
        assertEquals(2, t.groups().size)
        assertEquals("1|L1|261127", t.groups().first().key)
    }

    @Test
    fun `articulo suma todos sus lotes`() {
        val t = Tally(MemStore())
        t.add(10.0, "A", "1|L1|261127"); t.add(4.0, "B", "1|L2|261130"); t.add(5.0, "C", "2|L3|261117")
        assertEquals(14.0, t.total("1"), 0.0)
    }

    @Test
    fun `cerrar una cadena deja las otras y deshacer la devuelve`() {
        val t = Tally(MemStore())
        t.add(10.0, "A", "1|L1|X"); t.add(5.0, "B", "2|L2|Y")
        t.clear("1|L1|X")
        assertEquals(5.0, t.total, 0.0)
        assertTrue(t.undo())
        assertEquals(15.0, t.total, 0.0)
    }

    @Test
    fun `la cadena sobrevive al guardado`() {
        val store = MemStore()
        Tally(store).apply { add(10.0, "A", "1|L1|X"); addManual(2.0, "1|L1|X") }
        assertEquals(12.0, Tally(store).total("1|L1|X"), 0.0)
    }
}
