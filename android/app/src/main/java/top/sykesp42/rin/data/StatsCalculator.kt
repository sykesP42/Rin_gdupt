package top.sykesp42.rin.data

import kotlin.math.roundToInt

/**
 * Pure calculations for the GPA simulator, course difficulty labels and
 * export formatting. No Android dependencies — fully testable on the JVM.
 */
object StatsCalculator {

    /** Current weighted total points (GPA × credits) across active graded courses. */
    fun totalPoints(grades: List<JwxtGrade>): Double =
        grades.filter { it.active }
            .sumOf { (it.gradePoint ?: 0.0) * (it.credits ?: 0.0) }

    /** Current total credits across active graded courses. */
    fun totalCredits(grades: List<JwxtGrade>): Double =
        grades.filter { it.active }.sumOf { it.credits ?: 0.0 }

    /** Average score for a course across all its attempts (retakes included). */
    fun courseAverage(grades: List<JwxtGrade>, courseName: String): Double? {
        val scores = grades.filter { it.courseName == courseName && it.active }
            .mapNotNull { it.score.toIntOrNull() }
        if (scores.isEmpty()) return null
        return scores.average()
    }

    /**
     * Difficulty label from the course average score:
     * ≥85 给分好 · 75–84 正常 · 65–74 偏难 · <65 高危.
     */
    fun difficultyLabel(average: Double?): String = when {
        average == null -> ""
        average >= 85 -> "给分好"
        average >= 75 -> "正常"
        average >= 65 -> "偏难"
        else -> "高危"
    }

    /** Difficulty accent color bucket for UI rendering. */
    fun difficultyBucket(average: Double?): Int = when {
        average == null -> 0
        average >= 85 -> 1
        average >= 75 -> 2
        average >= 65 -> 3
        else -> 4
    }

    /** Per-course difficulty profile for the whole transcript. */
    fun difficultyProfile(grades: List<JwxtGrade>): List<CourseDifficulty> =
        grades.filter { it.active }
            .groupBy { it.courseName }
            .map { (name, list) ->
                val avg = list.mapNotNull { g -> g.score.toIntOrNull() }.average()
                CourseDifficulty(
                    courseName = name,
                    average = if (avg.isNaN()) null else avg,
                    label = difficultyLabel(if (avg.isNaN()) null else avg),
                    attemptCount = list.size
                )
            }
            .sortedBy { it.average ?: 0.0 }
}

data class CourseDifficulty(
    val courseName: String,
    val average: Double?,
    val label: String,
    val attemptCount: Int
)
