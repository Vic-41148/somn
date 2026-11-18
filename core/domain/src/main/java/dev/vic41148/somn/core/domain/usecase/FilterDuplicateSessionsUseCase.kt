package dev.vic41148.somn.core.domain.usecase

import dev.vic41148.somn.core.domain.model.SessionRange
import dev.vic41148.somn.core.domain.model.SleepSession

/**
 * Drops sessions whose [SessionRange] already exists, either in the database or earlier in the
 * same batch (issue #113: re-importing the same Sleep as Android `sleep-export.csv` used to
 * insert every row again).
 *
 * Pure and order-preserving: the caller supplies the existing ranges, so this stays testable
 * without Room. The duplicate count is surfaced to the user instead of being swallowed.
 */
class FilterDuplicateSessionsUseCase {

    data class Result(
        val sessions: List<SleepSession>,
        val duplicateCount: Int
    )

    operator fun invoke(
        sessions: List<SleepSession>,
        existing: Collection<SessionRange>
    ): Result {
        val seen = existing.toMutableSet()
        val kept = sessions.filter { session ->
            seen.add(SessionRange(session.startTimeMillis, session.endTimeMillis))
        }
        return Result(kept, sessions.size - kept.size)
    }
}
