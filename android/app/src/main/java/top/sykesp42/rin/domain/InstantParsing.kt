package top.sykesp42.rin.domain

import java.time.Instant
import java.time.OffsetDateTime

/** Parses the RFC3339 timestamps the backend emits; returns null instead of throwing. */
fun parseApiInstant(value: String): Instant? {
    if (value.isBlank()) return null
    return runCatching { OffsetDateTime.parse(value).toInstant() }
        .recoverCatching { Instant.parse(value) }
        .getOrNull()
}
