package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.SessionType
import dev.vic41148.somn.core.domain.model.SleepSession
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class ExportJsonUseCaseTest {

    private val useCase = ExportJsonUseCase()

    private fun session(
        id: Long = 1L,
        avgBreathingRateBrpm: Float? = null,
        healthConnectRecordId: String? = null
    ) = SleepSession(
        id = id,
        startTimeMillis = 1_700_000_000_000L,
        endTimeMillis = 1_700_028_800_000L,
        sleepDurationMinutes = 420,
        timeInBedMinutes = 480,
        sleepEfficiency = 87.5f,
        sleepOnsetMinutes = 12,
        wakeEvents = 3,
        deepSleepPercent = 18.25f,
        lightSleepPercent = 55f,
        remSleepPercent = 20f,
        sleepScore = 82,
        moodRating = 4,
        notes = "Slept ok",
        isCompleted = true,
        timezoneId = "Europe/Prague",
        isHomeSleep = true,
        alarmUsed = true,
        avgBreathingRateBrpm = avgBreathingRateBrpm,
        coughEventCount = 2,
        isPartial = false,
        sessionType = SessionType.MAIN_SLEEP,
        isOversleep = false,
        healthConnectRecordId = healthConnectRecordId
    )

    @Test
    fun invoke_emptyList_producesEmptyJsonArray() {
        val json = useCase(emptyList())

        val array = JSONArray(json)
        assertThat(array.length()).isEqualTo(0)
    }

    @Test
    fun invoke_roundTripsAllFieldsThroughRealJsonParsing() {
        val json = useCase(listOf(session()))

        // This is the whole point of not skipping this test: parse with the REAL org.json
        // implementation (testImplementation(libs.org.json), not the Android SDK stub that
        // throws "not mocked" under this module's plain-JUnit test config) and assert on the
        // actual parsed values, not just "it didn't throw."
        val array = JSONArray(json)
        assertThat(array.length()).isEqualTo(1)

        val obj = array.getJSONObject(0)
        assertThat(obj.getLong("id")).isEqualTo(1L)
        assertThat(obj.getLong("startTimeMillis")).isEqualTo(1_700_000_000_000L)
        assertThat(obj.getLong("endTimeMillis")).isEqualTo(1_700_028_800_000L)
        assertThat(obj.getInt("sleepDurationMinutes")).isEqualTo(420)
        assertThat(obj.getInt("timeInBedMinutes")).isEqualTo(480)
        assertThat(obj.getDouble("sleepEfficiency")).isEqualTo(87.5)
        assertThat(obj.getInt("sleepOnsetMinutes")).isEqualTo(12)
        assertThat(obj.getInt("wakeEvents")).isEqualTo(3)
        assertThat(obj.getDouble("deepSleepPercent")).isEqualTo(18.25)
        assertThat(obj.getDouble("lightSleepPercent")).isEqualTo(55.0)
        assertThat(obj.getDouble("remSleepPercent")).isEqualTo(20.0)
        assertThat(obj.getInt("sleepScore")).isEqualTo(82)
        assertThat(obj.getInt("moodRating")).isEqualTo(4)
        assertThat(obj.getString("notes")).isEqualTo("Slept ok")
        assertThat(obj.getBoolean("isCompleted")).isTrue()
        assertThat(obj.getString("timezoneId")).isEqualTo("Europe/Prague")
        assertThat(obj.getBoolean("isHomeSleep")).isTrue()
        assertThat(obj.getBoolean("alarmUsed")).isTrue()
        assertThat(obj.getInt("coughEventCount")).isEqualTo(2)
        assertThat(obj.getBoolean("isPartial")).isFalse()
        assertThat(obj.getString("sessionType")).isEqualTo("MAIN_SLEEP")
        assertThat(obj.getBoolean("isOversleep")).isFalse()
    }

    @Test
    fun invoke_nullOptionalFields_areOmittedNotWrittenAsJsonNull() {
        val json = useCase(listOf(session(avgBreathingRateBrpm = null, healthConnectRecordId = null)))

        val obj = JSONArray(json).getJSONObject(0)
        assertThat(obj.has("avgBreathingRateBrpm")).isFalse()
        assertThat(obj.has("healthConnectRecordId")).isFalse()
    }

    @Test
    fun invoke_presentOptionalFields_areIncluded() {
        val json = useCase(listOf(session(avgBreathingRateBrpm = 14.5f, healthConnectRecordId = "hc-record-123")))

        val obj = JSONArray(json).getJSONObject(0)
        assertThat(obj.getDouble("avgBreathingRateBrpm")).isEqualTo(14.5)
        assertThat(obj.getString("healthConnectRecordId")).isEqualTo("hc-record-123")
    }

    @Test
    fun invoke_multipleSessions_preservesOrderAndCount() {
        val json = useCase(listOf(session(id = 1L), session(id = 2L), session(id = 3L)))

        val array = JSONArray(json)
        assertThat(array.length()).isEqualTo(3)
        assertThat(array.getJSONObject(0).getLong("id")).isEqualTo(1L)
        assertThat(array.getJSONObject(1).getLong("id")).isEqualTo(2L)
        assertThat(array.getJSONObject(2).getLong("id")).isEqualTo(3L)
    }
}
