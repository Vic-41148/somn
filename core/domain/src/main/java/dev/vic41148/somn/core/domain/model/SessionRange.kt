package dev.vic41148.somn.core.domain.model

/**
 * A completed session's bounds, the identity used to make Sleep as Android CSV import
 * idempotent (issue #113). Start/end are millisecond timestamps, so two genuinely distinct
 * sessions colliding on both is not a realistic case.
 */
data class SessionRange(
    val startTimeMillis: Long,
    val endTimeMillis: Long
)
