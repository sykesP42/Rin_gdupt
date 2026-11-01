package top.sykesp42.rin.data

/**
 * Extracts the `var <name> = [...]` array embedded in zf HTML pages.
 * Kept out of [LocalJwxtApi] for direct unit testing of the balanced-bracket
 * scan and the乱序周次 (unordered week list) compression.
 */
object TermExtractor {

    fun extractArray(body: String, variable: String): String? {
        val marker = Regex("\\bvar\\s+" + Regex.escape(variable) + "\\s*=").find(body)
            ?: return null
        val start = body.indexOf('[', marker.range.last)
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until body.length) {
            val char = body[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) return body.substring(start, index + 1)
                    }
                }
            }
        }
        return null
    }

    /** "4,7,6,5" → set; "1-3,7" → set. Returns distinct sorted weeks. */
    fun parseWeeks(raw: String): List<Int> =
        raw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..30 }
            .distinct()
            .sorted()

    /** [1,2,3,5,6,9] → "1-3,5-6,9" (compressed range text). */
    fun compressWeeks(weeks: List<Int>): String {
        if (weeks.isEmpty()) return ""
        val sorted = weeks.distinct().sorted()
        val parts = mutableListOf<String>()
        var runStart = sorted.first()
        var previous = sorted.first()
        for (week in sorted.drop(1)) {
            if (week == previous + 1) {
                previous = week
                continue
            }
            parts += formatRun(runStart, previous)
            runStart = week
            previous = week
        }
        parts += formatRun(runStart, previous)
        return parts.joinToString(",")
    }

    private fun formatRun(start: Int, end: Int): String =
        if (start == end) "$start" else "$start-$end"
}
