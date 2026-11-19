package cl.icestar.pesototal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetScraperTest {

    /** Pantalla real de easyWMS, "Tareas de picking". */
    private val pantalla = listOf(
        "ESC",
        "Tareas de picking",
        "Stock a picar",
        "Artículo: 1050783C - FALDA RES E.V",
        "Dejar en: 000000000000788021",
        "Cantidad pendiente: 40 [KG]",
        "Cantidad restante: 40 [KG]",
        "Introduzca cantidad en KG:",
        "40",
        "2:Incidencia"
    )

    @Test
    fun `solo se vacia el campo que trae la cantidad pendiente, nunca el articulo`() {
        // Pantalla del peso: el campo trae la misma cantidad pendiente.
        assertTrue(TargetScraper.isPrefilledWeight("40", "1050783C", 40.0))
        assertTrue(TargetScraper.isPrefilledWeight(" 12,500 ", "", 12.5))
        assertTrue(TargetScraper.isPrefilledWeight("12.5", "", 12.5))
        // Pantalla del articulo: articulos cortos, largos o con letras.
        assertFalse(TargetScraper.isPrefilledWeight("123", "123", 40.0))
        assertFalse(TargetScraper.isPrefilledWeight("85", "", 40.0))
        assertFalse(TargetScraper.isPrefilledWeight("1050783C", "1050783C", 40.0))
        assertFalse(TargetScraper.isPrefilledWeight("1000979", "", 40.0))
        assertFalse(TargetScraper.isPrefilledWeight("AYUDA_10", "AYUDA_10", 40.0))
        assertFalse(TargetScraper.isPrefilledWeight("921000979310301173010264430961R17261127", "", 40.0))
        // Articulo corto que coincide con la cantidad: manda el articulo.
        assertFalse(TargetScraper.isPrefilledWeight("40", "40", 40.0))
    }

    @Test
    fun `saca el pedido de la pantalla real`() {
        assertEquals(40.0, TargetScraper.findTarget(pantalla)!!, 0.0)
    }

    @Test
    fun `no confunde pendiente con restante`() {
        val texts = listOf(
            "Cantidad pendiente: 40 [KG]",
            "Cantidad restante: 25 [KG]"
        )
        assertEquals(40.0, TargetScraper.findTarget(texts)!!, 0.0)
    }

    @Test
    fun `ignora los numeros anteriores al ancla`() {
        val texts = listOf("Dejar en: 000000000000788021 Cantidad pendiente: 40 [KG]")
        assertEquals(40.0, TargetScraper.findTarget(texts)!!, 0.0)
    }

    @Test
    fun `etiqueta y valor en nodos separados`() {
        assertEquals(40.0, TargetScraper.findTarget(listOf("Cantidad pendiente:", "40 [KG]"))!!, 0.0)
    }

    @Test
    fun `acepta coma y punto decimal`() {
        assertEquals(12.5, TargetScraper.findTarget(listOf("Cantidad pendiente: 12,5 [KG]"))!!, 0.0)
        assertEquals(12.5, TargetScraper.findTarget(listOf("Cantidad pendiente: 12.5 [KG]"))!!, 0.0)
    }

    @Test
    fun `no le importan las mayusculas`() {
        assertEquals(40.0, TargetScraper.findTarget(listOf("CANTIDAD PENDIENTE: 40 [KG]"))!!, 0.0)
    }

    @Test
    fun `sin ancla no inventa nada`() {
        assertNull(TargetScraper.findTarget(listOf("Cantidad restante: 40 [KG]")))
        assertNull(TargetScraper.findTarget(pantalla, anchor = "Peso solicitado"))
        assertNull(TargetScraper.findTarget(pantalla, anchor = "   "))
    }

    @Test
    fun `el ancla es configurable`() {
        val texts = listOf("Peso a tomar 7,25 kg")
        assertEquals(7.25, TargetScraper.findTarget(texts, anchor = "Peso a tomar")!!, 0.0)
    }

    @Test
    fun `articulo de la pantalla de easyWMS`() {
        assertEquals("AYUDA_10", TargetScraper.findArticle(listOf("Artículo: AYUDA_10 - PRUEBA DESDE EXCEL_11")))
        assertEquals("1000979", TargetScraper.findArticle(listOf("Articulo:", "01000979 - ASADO")))
        assertNull(TargetScraper.findArticle(listOf("Cantidad pendiente: 40 [KG]")))
    }
}
