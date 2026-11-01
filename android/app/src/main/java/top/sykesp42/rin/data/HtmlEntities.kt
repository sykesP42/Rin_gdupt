package top.sykesp42.rin.data

/**
 * JWXT text fields sometimes carry HTML entities, and some rows are escaped
 * twice (`&amp;mdash;`). Decode common named and numeric entities until stable
 * so the teaching calendar does not show raw markup.
 */
internal fun decodeHtmlEntities(value: String): String {
    if (value.isEmpty() || '&' !in value) return value
    var current = value
    repeat(3) {
        val next = ENTITY.replace(current) { match ->
            val token = match.groupValues[1]
            when {
                token.startsWith("#x", ignoreCase = true) ->
                    codePointText(token.substring(2).toIntOrNull(16))
                token.startsWith("#") ->
                    codePointText(token.substring(1).toIntOrNull())
                else -> NAMED[token.lowercase()] ?: match.value
            }
        }
        if (next == current) return current
        current = next
    }
    return current
}

private val ENTITY = Regex("&(#x[0-9A-Fa-f]+|#\\d+|[A-Za-z][A-Za-z0-9]+);")

private val NAMED = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to "\u00A0",
    "mdash" to "\u2014",
    "ndash" to "\u2013",
    "ldquo" to "\u201C",
    "rdquo" to "\u201D",
    "lsquo" to "\u2018",
    "rsquo" to "\u2019",
    "hellip" to "\u2026",
    "middot" to "\u00B7",
    "times" to "\u00D7",
    "divide" to "\u00F7",
    "copy" to "\u00A9",
    "reg" to "\u00AE",
    "deg" to "\u00B0",
    "plusmn" to "\u00B1",
    "sect" to "\u00A7",
    "para" to "\u00B6"
)

private fun codePointText(value: Int?): String {
    if (value == null || value !in 0..0x10FFFF || value in 0xD800..0xDFFF) return ""
    return String(Character.toChars(value))
}
