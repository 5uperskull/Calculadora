package cl.icestar.pesototal

import java.security.MessageDigest

/**
 * Candado de la configuracion avanzada.
 *
 * Una clave dentro del APK no es seguridad: se saca descompilando. Sirve para
 * que nadie toque la configuracion por accidente. Se guarda solo su hash para
 * que al menos no aparezca en texto plano.
 */
object AdminLock {

    /** SHA-256 de la contrasena. */
    private const val HASH = "69f26a0a20348764fc9e1ff1240354c3435208cee67d6c07e538eb7d7b3beea4"

    /** Ignora espacios al principio y al final: con guantes se cuelan solos. */
    fun matches(input: String): Boolean = sha256(input.trim()) == HASH

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
