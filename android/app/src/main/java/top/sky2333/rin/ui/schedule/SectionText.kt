package top.sky2333.rin.ui.schedule

private const val MIN_SECTION = 1
private const val MAX_SECTION = 10

/**
 * Parse a schedule `sectionText` payload into section numbers.
 *
 * Accepted forms, in any combination separated by ASCII/Chinese commas or
 * whitespace:
 * - comma lists: `"1,2"`
 * - hyphen ranges: `"3-4"`, `"9-10"` (Chinese/em dashes are normalized too)
 * - packed pairs: `"0102"` -> 1, 2; `"0910"` -> 9, 10
 * - a bare `"10"` stays section 10 (it is not read as a packed pair)
 *
 * Only 1..10 survive; the result is distinct and ascending. Malformed tokens
 * (`"0"`, `"11"`, `"bad"`, `"1-"`, `"1-2-3"`) are ignored. A reversed range
 * such as `"4-3"` is treated as `3..4` so the block is not silently lost.
 */
fun parseSections(text: String): List<Int> {
    val sections = linkedSetOf<Int>()
    tokenize(text).forEach { token ->
        parseToken(token).forEach { section ->
            if (section in MIN_SECTION..MAX_SECTION) sections += section
        }
    }
    return sections.sorted()
}

private fun tokenize(text: String): List<String> {
    val normalized = text
        .replace('，', ',')
        .replace('－', '-')
        .replace('—', '-')
        .replace('–', '-')
        .replace('−', '-')
        .replace('\u3000', ' ')
    // Collapse whitespace around dashes so "9 - 10" is one range, while
    // "1 2" still reads as two separate tokens.
    return normalized
        .replace(Regex("\\s*-\\s*"), "-")
        .split(Regex("[,\\s]+"))
        .filter { it.isNotEmpty() }
}

private fun parseToken(token: String): List<Int> {
    val dash = token.indexOf('-')
    if (dash >= 0) {
        if (token.indexOf('-', dash + 1) >= 0) return emptyList()
        val start = token.substring(0, dash).toSectionOrNull() ?: return emptyList()
        val end = token.substring(dash + 1).toSectionOrNull() ?: return emptyList()
        return if (start <= end) (start..end).toList() else (end..start).toList()
    }
    if (token.isEmpty() || !token.all { it.isDigit() }) return emptyList()
    return when {
        token.length <= 2 -> listOfNotNull(token.toSectionOrNull())
        token.length % 2 == 0 -> token.chunked(2).mapNotNull { it.toSectionOrNull() }
        else -> emptyList()
    }
}

private fun String.toSectionOrNull(): Int? =
    toIntOrNull()?.takeIf { it in MIN_SECTION..MAX_SECTION }


/** Start/end minute of each teaching period, shared by the grid and local schedule. */
val sectionBounds: Map<Int, Pair<Int, Int>> = mapOf(
    1 to Pair(8 * 60, 8 * 60 + 45),
    2 to Pair(8 * 60 + 55, 9 * 60 + 40),
    3 to Pair(10 * 60, 10 * 60 + 45),
    4 to Pair(10 * 60 + 55, 11 * 60 + 40),
    5 to Pair(14 * 60 + 30, 15 * 60 + 15),
    6 to Pair(15 * 60 + 25, 16 * 60 + 10),
    7 to Pair(16 * 60 + 20, 17 * 60),
    8 to Pair(17 * 60, 17 * 60 + 50),
    9 to Pair(19 * 60 + 40, 20 * 60 + 25),
    10 to Pair(20 * 60 + 35, 21 * 60 + 20)
)


/** First period's clock time for a schedule cell, e.g. "0102" -> "08:00". */
fun sectionStart(text: String): String {
    val first = parseSections(text).firstOrNull() ?: return ""
    val minutes = sectionBounds[first]?.first ?: return ""
    return "%02d:%02d".format(minutes / 60, minutes % 60)
}
