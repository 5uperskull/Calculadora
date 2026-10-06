package cl.icestar.pesototal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Si este test falla, nadie puede entrar a la configuracion avanzada. */
class AdminLockTest {

    @Test
    fun `la contrasena correcta abre`() {
        assertTrue(AdminLock.matches("L0g1s*"))
    }

    @Test
    fun `los espacios de los bordes no importan`() {
        assertTrue(AdminLock.matches("  L0g1s* "))
    }

    @Test
    fun `distingue mayusculas y caracteres`() {
        assertFalse(AdminLock.matches("l0g1s*"))
        assertFalse(AdminLock.matches("L0g1s"))
        assertFalse(AdminLock.matches("Logis*"))
        assertFalse(AdminLock.matches(""))
    }
}
