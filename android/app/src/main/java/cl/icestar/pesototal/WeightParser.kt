package cl.icestar.pesototal

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Puerto directo del parser de ../../test.js.
 * test.js es la especificacion: si cambia una regla aqui, cambia alla, y los
 * mismos casos tienen que pasar en los dos lados.
 */
object WeightParser {

    const val MIN_KG = 0.001
    const val MAX_KG = 2000.0
    const val DEFAULT_OFFSET = 13
    const val DEFAULT_LEN = 7

    data class Result(
        val kg: Double,
        val source: String,
        val code: String,
        val warn: String? = null
    )

    /** La pistola puede meter separadores GS (0x1d) y otros control chars. */
    fun clean(raw: String?): String =
        (raw ?: "").filter { it.code in 0x20..0x7E }.trim()

    private val GS1 = Regex("""310([0-5])(\d{6})""")

    /**
     * Via correcta: GS1 AI 310n = peso neto en kg, n decimales, 6 digitos.
     * ponytail: el 310 podria aparecer por coincidencia dentro de otro campo
     * numerico. Si eso pasa en terreno, anclar el AI a posicion fija.
     */
    private fun fromGs1(code: String): Pair<Double, String>? {
        val m = GS1.find(code) ?: return null
        val decimals = m.groupValues[1].toInt()
        val digits = m.groupValues[2].toLong()
        val kg = digits / Math.pow(10.0, decimals.toDouble())
        return kg to "GS1 AI 310$decimals"
    }

    /**
     * Respaldo: la formula original de Excel sobre un recorte de posicion fija.
     * offset es 1-based, igual que EXTRAE/MID.
     */
    private fun fromSlice(code: String, offset: Int, len: Int): Pair<Double, String>? {
        if (offset < 1 || offset > code.length || len < 6) return null
        val end = minOf(code.length, offset - 1 + len)
        val d8 = code.substring(offset - 1, end)
        if (d8.isEmpty()) return null
        val first = d8[0]
        val digits = if (first == '2' || first == '3') d8.drop(1).take(6) else d8.take(6)
        if (digits.length != 6 || !digits.all { it.isDigit() }) return null
        return digits.toLong() / 1000.0 to "recorte $offset/$len"
    }

    /** null = no se encontro peso. No adivina. */
    fun parse(raw: String?, offset: Int = DEFAULT_OFFSET, len: Int = DEFAULT_LEN): Result? {
        val code = clean(raw)
        if (code.isEmpty()) return null
        val hit = fromGs1(code) ?: fromSlice(code, offset, len) ?: return null
        val kg = hit.first
        val warn = if (kg < MIN_KG || kg > MAX_KG) "Fuera de rango" else null
        return Result(kg, hit.second, code, warn)
    }

    fun round3(kg: Double): Double = Math.round(kg * 1000.0) / 1000.0

    /** 11.5 -> "11,5" con coma, "11.5" con punto. Sin ceros de relleno. */
    fun format(kg: Double, comma: Boolean = true): String {
        val s = BigDecimal.valueOf(round3(kg))
            .setScale(3, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        return if (comma) s.replace('.', ',') else s
    }
}
