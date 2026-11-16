package cl.icestar.pesototal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Casos reales de la PLANTILLA de Excel del CD. */
class LabelTest {

    @Test
    fun `primer ejemplo del CD`() {
        val c = Label.parse("921000979310301173010264430961R17261127")!!
        assertEquals(Label.Chain("1000979", "264430961R", "261127"), c)
    }

    @Test
    fun `segundo ejemplo del CD`() {
        val c = Label.parse("921000282310302504010264635661R17261117")!!
        assertEquals(Label.Chain("1000282", "264635661R", "261117"), c)
    }

    @Test
    fun `lote en minuscula se normaliza para no partir la cadena`() {
        val c = Label.parse("921016103310301150010262960731r17260817")!!
        assertEquals(Label.Chain("1016103", "262960731R", "260817"), c)
    }

    @Test
    fun `lote con X se acepta, tambien su vencimiento`() {
        val c = Label.parse("921000979310301173010264430961X17261127")!!
        assertEquals("264430961X", c.lot)
        assertEquals("261127", c.expiry)
    }

    @Test
    fun `articulo de 6 digitos pierde el cero como en el Excel`() {
        assertEquals("700334", Label.parse("920700334310301173010264430961C17261127")!!.article)
    }

    @Test
    fun `codigo que no es etiqueta propia no tiene cadena`() {
        assertNull(Label.parse("01000000000000003102001150"))
    }

    @Test
    fun `seleccion por articulo abarca todos sus lotes`() {
        assertTrue(Label.matches("1000979|264430961R|261127", "1000979"))
        assertFalse(Label.matches("10009790|X|1", "1000979"))
        assertFalse(Label.matches("1000282|264635661R|261117", "1000979"))
        assertTrue(Label.matches("", Label.ALL))
    }

    @Test
    fun `vencimiento se muestra como lo lee el operario`() {
        assertEquals("27/11/2026", Label.formatExpiry("261127"))
        assertEquals("", Label.formatExpiry(""))
    }
}
