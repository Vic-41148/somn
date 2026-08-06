package dev.vic41148.somn.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.data.database.SleepDatabase
import dev.vic41148.somn.core.data.database.entity.UserProfileEntity
import dev.vic41148.somn.core.domain.model.Chronotype
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression tests for the chronotype self-heal in [UserProfileRepository.toDomain]: the
 * persisted `chronotype` string may have been derived with the old broken 16-86 banding (see
 * Chronotype.fromMeqScore), so reads recompute from the raw `chronotypeMeqScore` — while a
 * null score (quiz skipped) must keep honoring the persisted string.
 */
@RunWith(RobolectricTestRunner::class)
class UserProfileRepositoryTest {

    private val db: SleepDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        SleepDatabase::class.java
    ).allowMainThreadQueries().build()

    private val repository = UserProfileRepository(db.userProfileDao())

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun getProfile_withStaleDerivedChronotype_recomputesFromRawScore() = runTest {
        // Persisted under the old (broken) banding: score 20 was labeled MODERATE_EVENING,
        // but on the real rMEQ scale 20 is MODERATE_MORNING.
        db.userProfileDao().upsert(
            UserProfileEntity(
                id = 1,
                chronotype = Chronotype.MODERATE_EVENING.name,
                chronotypeMeqScore = 20,
                onboardingCompleted = true
            )
        )

        val profile = repository.getProfile()

        assertThat(profile).isNotNull()
        assertThat(profile!!.chronotype).isEqualTo(Chronotype.MODERATE_MORNING)
        assertThat(profile.chronotypeMeqScore).isEqualTo(20)
    }

    @Test
    fun getProfile_withNullMeqScore_honorsPersistedChronotype() = runTest {
        // Quiz skipped — no raw score to recompute from; the persisted value must win.
        db.userProfileDao().upsert(
            UserProfileEntity(
                id = 1,
                chronotype = Chronotype.DEFINITE_MORNING.name,
                chronotypeMeqScore = null,
                onboardingCompleted = true
            )
        )

        val profile = repository.getProfile()

        assertThat(profile).isNotNull()
        assertThat(profile!!.chronotype).isEqualTo(Chronotype.DEFINITE_MORNING)
    }

    @Test
    fun getProfile_withNullMeqScoreAndGarbagePersisted_returnsUnknown() = runTest {
        db.userProfileDao().upsert(
            UserProfileEntity(
                id = 1,
                chronotype = "NOT_A_REAL_CHRONOTYPE", // garbage string → safe fallback
                chronotypeMeqScore = null,
                onboardingCompleted = true
            )
        )

        val profile = repository.getProfile()

        assertThat(profile).isNotNull()
        assertThat(profile!!.chronotype).isEqualTo(Chronotype.UNKNOWN)
    }
}
