package dev.vic41148.somn.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.ExternalVitalsSnapshot
import org.junit.Test

class VitalsFlagsTest {

    private fun snap(id: Long, rhr: Float? = 60f, hrv: Float? = 50f) =
        ExternalVitalsSnapshot(sessionId = id, restingHeartRateBpm = rhr, avgHeartRateVariabilityMs = hrv)

    @Test
    fun `null latest returns empty`() {
        assertThat(flagVitals(null, emptyList())).isEmpty()
    }

    @Test
    fun `thin history reports no data instead of flagging`() {
        val flags = flagVitals(snap(3), listOf(snap(1), snap(2)))
        assertThat(flags).isNotEmpty()
        assertThat(flags.all { !it.hasData }).isTrue()
        // Never a false alarm on thin data.
        assertThat(flags.all { it.inRange }).isTrue()
    }

    @Test
    fun `steady vital is in range`() {
        val history = (1..6).map { snap(it.toLong(), rhr = 60f) }
        val flags = flagVitals(snap(7, rhr = 61f), history)
        val rhr = flags.first { it.label == "Resting HR" }
        assertThat(rhr.hasData).isTrue()
        assertThat(rhr.inRange).isTrue()
        assertThat(rhr.detail).contains("Usual")
    }

    @Test
    fun `spiking RHR flags out of range`() {
        val history = (1..6).map { snap(it.toLong(), rhr = 60f) }
        val flags = flagVitals(snap(7, rhr = 75f), history)
        assertThat(flags.first { it.label == "Resting HR" }.inRange).isFalse()
    }

    @Test
    fun `real temp shift still flags on flat history`() {
        val history = (1..6).map {
            ExternalVitalsSnapshot(sessionId = it.toLong(), avgSkinTemperatureCelsius = 36.0f)
        }
        val flags = flagVitals(
            ExternalVitalsSnapshot(sessionId = 7, avgSkinTemperatureCelsius = 36.5f),
            history
        )
        assertThat(flags.first { it.label == "Skin temp" }.inRange).isFalse()
    }

    @Test
    fun `missing latest value shows dash without data`() {        val history = (1..6).map { snap(it.toLong()) }
        val flags = flagVitals(snap(7, rhr = null), history)
        val rhr = flags.first { it.label == "Resting HR" }
        assertThat(rhr.value).isEqualTo("–")
        assertThat(rhr.hasData).isFalse()
    }
}
