package cl.icestar.pesototal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
