package dev.vic41148.somn.feature.tracking.service

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Verifies [foregroundServiceTypeMask] — the Android 14+ foreground-service type mask built from
 * the runtime permissions actually held. These constants are compile-time values, so a plain JVM
 * unit test can assert them without a device.
 */
class ForegroundServiceTypeMaskTest {

    @Test
    fun `mic only claims microphone type`() {
        val mask = foregroundServiceTypeMask(recordAudioGranted = true, bodySensorsGranted = false)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE, mask)
        assertFalse(
            "mic-only mask must not include health type",
            mask and ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH != 0
        )
    }

    @Test
    fun `sensors only claims health type`() {
        val mask = foregroundServiceTypeMask(recordAudioGranted = false, bodySensorsGranted = true)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH, mask)
        assertFalse(
            "sensors-only mask must not include microphone type",
            mask and ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE != 0
        )
    }

    @Test
    fun `both granted claims combined mask`() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
            foregroundServiceTypeMask(recordAudioGranted = true, bodySensorsGranted = true)
        )
    }

    @Test
    fun `neither granted falls back to specialUse`() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            foregroundServiceTypeMask(recordAudioGranted = false, bodySensorsGranted = false)
        )
    }
}
