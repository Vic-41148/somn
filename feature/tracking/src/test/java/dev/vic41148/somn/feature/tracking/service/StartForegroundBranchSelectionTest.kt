package dev.vic41148.somn.feature.tracking.service

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks in the SDK-conditional [android.app.Service.startForeground] overload selection in
 * [SleepTrackingService.startTrackingForeground] (delegated to [startForegroundTypeForApi]):
 *  - below Q (API 26-28) the two-arg overload must be used — the three-arg overload doesn't exist;
 *  - Q..Tiramisu (API 29-33) uses the three-arg overload with type 0 (manifest-declared types);
 *  - UpsideDownCake+ (API 34+) uses the three-arg overload with the permission-derived type mask.
 *
 * These constants are compile-time values, so a plain JVM unit test can assert them without a
 * device — same approach as [ForegroundServiceTypeMaskTest]. The test validates the decision
 * seam ([startForegroundTypeForApi]), which encodes the full decision table: the below-Q
 * two-arg selection is dispatched at the call site via an explicit [Build.VERSION.SDK_INT] guard
 * (lint's NewApi check requires an SDK guard on the three-arg call), and the seam supplies the
 * type for that three-arg call on Q+. Asserting the framework call itself would require
 * Robolectric, which this module deliberately does not use.
 */
class StartForegroundBranchSelectionTest {

    @Test
    fun `below Q always selects the two-arg overload`() {
        listOf(26, 27, 28).forEach { sdk ->
            // Permission state is irrelevant below Q: the two-arg overload is the only option.
            assertEquals(
                "API $sdk must use the two-arg overload",
                START_FOREGROUND_TWO_ARG,
                startForegroundTypeForApi(sdk, recordAudioGranted = true, bodySensorsGranted = true)
            )
            assertEquals(
                "API $sdk must use the two-arg overload regardless of grants",
                START_FOREGROUND_TWO_ARG,
                startForegroundTypeForApi(sdk, recordAudioGranted = false, bodySensorsGranted = false)
            )
        }
    }

    @Test
    fun `Q through Tiramisu always selects the three-arg overload with type 0`() {
        listOf(29, 30, 31, 32, 33).forEach { sdk ->
            // No FGS runtime-permission enforcement pre-34: type 0 (manifest types) regardless of grants.
            assertEquals(
                "API $sdk must pass type 0 = manifest types",
                0,
                startForegroundTypeForApi(sdk, recordAudioGranted = true, bodySensorsGranted = true)
            )
            assertEquals(
                "API $sdk must pass type 0 even with no permissions granted",
                0,
                startForegroundTypeForApi(sdk, recordAudioGranted = false, bodySensorsGranted = false)
            )
        }
    }

    @Test
    fun `UpsideDownCake and later select the three-arg overload with the permission-derived mask`() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            startForegroundTypeForApi(34, recordAudioGranted = true, bodySensorsGranted = false)
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
            startForegroundTypeForApi(34, recordAudioGranted = false, bodySensorsGranted = true)
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
            startForegroundTypeForApi(35, recordAudioGranted = true, bodySensorsGranted = true)
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            startForegroundTypeForApi(34, recordAudioGranted = false, bodySensorsGranted = false)
        )
    }
}
