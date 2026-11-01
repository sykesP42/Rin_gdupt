package top.sykesp42.rin.ui.schedule

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

/** The campus clock for a section range's first period; blank when unknown. */
fun sectionStart(text: String): String {
    val section = parseSections(text).firstOrNull() ?: return ""
    val starts = arrayOf(
        "08:00", "08:55", "10:00", "10:55", "14:30", "15:25",
        "16:20", "17:10", "19:40", "20:35", "21:25", "22:15"
    )
    return starts.getOrNull(section - 1).orEmpty()
}
