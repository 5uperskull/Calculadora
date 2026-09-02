package cl.icestar.pesototal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Mismos casos que ../../../../../../test.js. Si uno cambia, cambian los dos. */
class WeightParserTest {

    private val real = "921016103310301150010262960731r17260817"

    @Test
    fun `codigo real de la etiqueta da 11,5 kg`() {
        val r = WeightParser.parse(real)!!
        assertEquals(11.5, r.kg, 0.0)
        assertEquals("GS1 AI 3103", r.source)
        assertNull(r.warn)
    }

    @Test
    fun `AI 3102 con dos decimales`() {
        val r = WeightParser.parse("0100000000000000" + "3102" + "001150")!!
        assertEquals(11.5, r.kg, 0.0)
    }

    @Test
    fun `recorte de respaldo que empieza en 3 salta el 3`() {
        assertEquals(11.5, WeightParser.parse("3011500", 1, 7)!!.kg, 0.0)
    }

    @Test
    fun `recorte de respaldo que empieza en 2 salta el 2`() {
        assertEquals(11.5, WeightParser.parse("2011500", 1, 7)!!.kg, 0.0)
    }

    @Test
    fun `recorte de respaldo sin prefijo toma los 6 primeros`() {
        assertEquals(11.5, WeightParser.parse("011500", 1, 7)!!.kg, 0.0)
    }

    @Test
    fun `offset por defecto 13 barra 7 sobre el codigo real`() {
        // Se rompe el AI dejando intacto el 3 que el recorte necesita.
        assertEquals(11.5, WeightParser.parse(real.replace("3103", "XYZ3"))!!.kg, 0.0)
    }

    @Test
    fun `codigo sin peso no inventa numero`() {
        assertNull(WeightParser.parse("abc123"))
    }

    @Test
    fun `codigo vacio o nulo`() {
        assertNull(WeightParser.parse(""))
        assertNull(WeightParser.parse(null))
    }

    @Test
    fun `peso fuera de rango marca advertencia`() {
        val r = WeightParser.parse("3100999999")!!
        assertEquals(999999.0, r.kg, 0.0)
        assertNotNull(r.warn)
    }

    @Test
    fun `limpia separadores GS y espacios`() {
        val sucio = "  " + real.substring(0, 20) + "\u001d" + real.substring(20) + "\r\n"
        assertEquals(11.5, WeightParser.parse(sucio)!!.kg, 0.0)
    }

    @Test
    fun `formato decimal coma y punto`() {
        assertEquals("11,5", WeightParser.format(11.5))
        assertEquals("11.5", WeightParser.format(11.5, comma = false))
        assertEquals("921,016", WeightParser.format(921.016))
        assertEquals("34,7", WeightParser.format(11.5 + 11.5 + 11.7))
    }
}
