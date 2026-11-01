package top.sykesp42.rin.monitor

import java.time.Instant
import top.sykesp42.rin.data.ApiActivity
import top.sykesp42.rin.data.MonitorApi
import top.sykesp42.rin.data.hasPeople
import top.sykesp42.rin.domain.Activity
import top.sykesp42.rin.domain.ActivityStatus
import top.sykesp42.rin.domain.SignAccess
import top.sykesp42.rin.domain.Snapshot
import top.sykesp42.rin.domain.State
import top.sykesp42.rin.domain.evaluate
import top.sykesp42.rin.domain.parseApiInstant

/**
 * One recommendation -> course -> roster -> evaluate cycle. All side effects
 * (notifications, history, handoff) are owned by the caller so this stays
 * testable on the JVM.
 */
class MonitorEngine(
    private val api: MonitorApi,
    private val clock: () -> Instant = Instant::now
) {
    suspend fun pollOnce(manualCode: String, lastHandledSignId: String): MonitorView {
        var recommendationStatus = ""
        val code = manualCode.trim().ifEmpty {
            val recommendation = api.recommendation()
            recommendationStatus = recommendation.status
            if (recommendation.status == "matched") recommendation.room?.roomCode.orEmpty() else ""
        }
        if (code.isEmpty()) {
            // Carry the raw recommendation status so the UI can explain the
            // IDLE state ("no_course_today" vs "unknown_room") honestly.
            return MonitorView(state = State.IDLE, recommendationStatus = recommendationStatus, serverTime = clock())
        }

        val course = api.course(code)
        val now = parseApiInstant(course.serverTime) ?: clock()
        val activity = course.activity
        // The course payload already carries the roster in local mode; cloud
        // servers expose it through the dedicated endpoint. One fetch feeds
        // both the confirmation signal and the counts shown in the UI.
        val fetchedRoster = if (activity == null) {
            null
        } else {
            course.roster.takeIf { it.hasPeople() }
                ?: runCatching { api.roster(code) }.getOrNull()
        }
        val rosterSource = fetchedRoster ?: top.sykesp42.rin.data.ApiRoster()
        val roster = rosterSource.toMonitorRoster()
        val selfSigned = if (activity?.status == STATUS_STARTED && course.signAccess.allowed) {
            when {
                roster.me == MySignState.SIGNED -> true
                roster.me == MySignState.UNSIGNED ||
                    roster.me == MySignState.NOT_IN_ROSTER ||
                    roster.me == MySignState.AMBIGUOUS -> false
                // A roster error must never be read as signed; an empty but
                // successful roster falls back to the direct confirmation call.
                fetchedRoster == null -> false
                else -> runCatching { api.selfSigned(code) }.getOrDefault(false)
            }
        } else {
            false
        }

        val decision = evaluate(
            Snapshot(
                now = now,
                threshold = course.signAccess.rosterThreshold,
                activity = activity?.toDomain(),
                signAccess = SignAccess(
                    allowed = course.signAccess.allowed,
                    reason = course.signAccess.reason,
                    requiresConfirmation = course.signAccess.requiresConfirmation,
                    signedCount = course.signAccess.signedCount,
                    rosterCount = course.signAccess.rosterCount
                ),
                lastHandledSignID = lastHandledSignId,
                selfSigned = selfSigned
            )
        )

        return MonitorView(
            state = decision.state,
            autoAllowed = decision.autoAllowed,
            manualRequiresConfirmation = decision.manualRequiresConfirmation,
            signId = decision.signId,
            roomCode = course.roomCode,
            courseName = course.courseName,
            location = course.location,
            teacherNames = course.teacherNames,
            sections = course.sections,
            signedCount = course.signAccess.signedCount,
            rosterCount = course.signAccess.rosterCount,
            roster = roster,
            rosterPeople = rosterSource.toRosterEntries(),
            signAccessAllowed = course.signAccess.allowed,
            threshold = course.signAccess.rosterThreshold,
            effectiveSignEndAt = activity?.let { parseApiInstant(it.effectiveSignEndAt) },
            qrRefreshIntervalSeconds = activity?.qrRefreshIntervalSeconds ?: 0,
            qrType = activity?.qrType.orEmpty(),
            serverTime = now
        )
    }

    private fun ApiActivity.toDomain(): Activity = Activity(
        signId = signId,
        status = when (status) {
            STATUS_NOT_STARTED -> ActivityStatus.NOT_STARTED
            STATUS_STARTED -> ActivityStatus.STARTED
            else -> ActivityStatus.END
        },
        effectiveSignEndAt = parseApiInstant(effectiveSignEndAt) ?: Instant.EPOCH
    )

    companion object {
        private const val STATUS_NOT_STARTED = "NOT_STARTED"
        private const val STATUS_STARTED = "STARTED"
    }
}
