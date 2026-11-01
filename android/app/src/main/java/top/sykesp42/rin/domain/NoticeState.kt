package top.sykesp42.rin.domain

/**
 * Display-only states for the notice faces and the "now" row. The full client
 * derives these from its sign-in state machine; this build has no sign-in, so
 * the state stays IDLE / NOT_STARTED / EXPIRED in practice. Kept as an enum so
 * the shared UI pieces compile unchanged.
 */
enum class State {
    IDLE, NOT_STARTED, WAITING_THRESHOLD, READY, EXPIRED, CONFIRMED
}

enum class ActivityStatus { NOT_STARTED, STARTED, END }
