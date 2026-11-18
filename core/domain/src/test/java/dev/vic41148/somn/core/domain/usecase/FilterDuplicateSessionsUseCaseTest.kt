package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.SessionRange
import dev.vic41148.somn.core.domain.model.SleepSession
import org.junit.Test

class FilterDuplicateSessionsUseCaseTest {

    private val useCase = FilterDuplicateSessionsUseCase()

    private fun session(start: Long, end: Long) = SleepSession(
        startTimeMillis = start,
        endTimeMillis = end,
        isCompleted = true
    )

    @Test
    fun invoke_noExistingSessions_keepsEverything() {
        val sessions = listOf(session(1_000L, 2_000L), session(3_000L, 4_000L))

        val result = useCase(sessions, emptyList())

        assertThat(result.sessions).containsExactlyElementsIn(sessions).inOrder()
        assertThat(result.duplicateCount).isEqualTo(0)
    }

    @Test
    fun invoke_sessionAlreadyInDatabase_isSkipped() {
        val existing = listOf(SessionRange(1_000L, 2_000L))
        val sessions = listOf(session(1_000L, 2_000L), session(3_000L, 4_000L))

        val result = useCase(sessions, existing)

        assertThat(result.sessions).containsExactly(sessions[1])
        assertThat(result.duplicateCount).isEqualTo(1)
    }

    @Test
    fun invoke_sameFileImportedTwiceInOneBatch_keepsFirstOccurrence() {
        val sessions = listOf(
            session(1_000L, 2_000L),
            session(1_000L, 2_000L),
            session(3_000L, 4_000L)
        )

        val result = useCase(sessions, emptyList())

        assertThat(result.sessions).containsExactly(session(1_000L, 2_000L), session(3_000L, 4_000L))
            .inOrder()
        assertThat(result.duplicateCount).isEqualTo(1)
    }

    @Test
    fun invoke_allRowsAlreadyImported_reportsEveryDuplicateAndKeepsNothing() {
        val existing = listOf(
            SessionRange(1_000L, 2_000L),
            SessionRange(3_000L, 4_000L)
        )
        val sessions = listOf(session(1_000L, 2_000L), session(3_000L, 4_000L))

        val result = useCase(sessions, existing)

        assertThat(result.sessions).isEmpty()
        assertThat(result.duplicateCount).isEqualTo(2)
    }

    @Test
    fun invoke_sameStartDifferentEnd_isNotDuplicate() {
        val existing = listOf(SessionRange(1_000L, 2_000L))
        val sessions = listOf(session(1_000L, 2_500L))

        val result = useCase(sessions, existing)

        assertThat(result.sessions).hasSize(1)
        assertThat(result.duplicateCount).isEqualTo(0)
    }
}
