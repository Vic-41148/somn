package dev.vic41148.somn.feature.alarm.captcha

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Covers [CaptchaTaskRegistry.resolveTask] — the shared per-alarm-vs-global precedence used by
 * both [dev.vic41148.somn.feature.alarm.ui.AlarmActivity] and the in-app firing screen.
 *
 * This was the exact gap the alarm deep-dive flagged: the per-alarm `captchaType` was decorative
 * because AlarmActivity ignored it and always used the global preference. The precedence test
 * below locks in the fixed behavior.
 */
class CaptchaTaskRegistryTest {

    private val globalMath = "math"
    private val globalShake = "shake"

    @Test
    fun perAlarmMATH_winsOverGlobalPreference() {
        val task = CaptchaTaskRegistry.resolveTask(
            perAlarmType = "MATH",
            globalTaskId = globalShake,
            qrCodeValue = null
        )
        assertThat(task?.id).isEqualTo("math")
    }

    @Test
    fun perAlarmSHAKE_winsOverGlobalPreference() {
        val task = CaptchaTaskRegistry.resolveTask(
            perAlarmType = "SHAKE",
            globalTaskId = globalMath,
            qrCodeValue = null
        )
        assertThat(task?.id).isEqualTo("shake")
    }

    @Test
    fun perAlarmQR_SCAN_mapsToQrcodeTask() {
        val task = CaptchaTaskRegistry.resolveTask(
            perAlarmType = "QR_SCAN",
            globalTaskId = globalMath,
            qrCodeValue = "https://example.com/wake"
        )
        assertThat(task?.id).isEqualTo("qrcode")
    }

    @Test
    fun perAlarmQR_SCAN_withoutConfiguredValue_fallsBackToMath() {
        // A QR captcha with nothing to scan must never lock the user out of dismissing.
        val task = CaptchaTaskRegistry.resolveTask(
            perAlarmType = "QR_SCAN",
            globalTaskId = globalMath,
            qrCodeValue = null
        )
        assertThat(task?.id).isEqualTo("math")
    }

    @Test
    fun perAlarmNONE_usesGlobalPreference() {
        val task = CaptchaTaskRegistry.resolveTask(
            perAlarmType = "NONE",
            globalTaskId = globalShake,
            qrCodeValue = null
        )
        assertThat(task?.id).isEqualTo("shake")
    }

    @Test
    fun unknownPerAlarmType_fallsBackToGlobalPreference() {
        val task = CaptchaTaskRegistry.resolveTask(
            perAlarmType = "TOTALLY_UNKNOWN",
            globalTaskId = globalShake,
            qrCodeValue = null
        )
        assertThat(task?.id).isEqualTo("shake")
    }

    @Test
    fun resolveTask_returnsFreshTask_resetEachCall() {
        val first = CaptchaTaskRegistry.resolveTask("MATH", globalMath, null)
        val second = CaptchaTaskRegistry.resolveTask("MATH", globalMath, null)
        // Registry hands out singletons, so both calls return the same instance — but each call
        // must reset it so a solved state from a previous firing episode never leaks forward.
        assertThat(first).isSameInstanceAs(second)
        assertThat(first?.isComplete()).isFalse()
    }

    @Test
    fun globalSequence_andNfc_remainReachableThroughPreference() {
        assertThat(CaptchaTaskRegistry.resolveTask("NONE", "sequence", null)?.id).isEqualTo("sequence")
        assertThat(CaptchaTaskRegistry.resolveTask("NONE", "nfc", null)?.id).isEqualTo("nfc")
    }

    @Test
    fun perAlarmNFC_mapsToNfcTask() {
        val task = CaptchaTaskRegistry.resolveTask(
            perAlarmType = "NFC",
            globalTaskId = globalMath,
            qrCodeValue = null
        )
        assertThat(task?.id).isEqualTo("nfc")
    }

    @Test
    fun perAlarmNFC_onDeviceWithoutNfc_fallsBackToGlobalPreference() {
        // A tag-tap captcha on hardware that can't read tags can never be solved — same
        // lock-out rule as QR-without-a-value: fall back to the global preference.
        val task = CaptchaTaskRegistry.resolveTask(
            perAlarmType = "NFC",
            globalTaskId = globalShake,
            qrCodeValue = null,
            nfcAvailable = false
        )
        assertThat(task?.id).isEqualTo("shake")
    }

    @Test
    fun perAlarmNFC_onDeviceWithoutNfc_globalAlsoNfc_fallsBackToMath() {
        val task = CaptchaTaskRegistry.resolveTask(
            perAlarmType = "NFC",
            globalTaskId = "nfc",
            qrCodeValue = null,
            nfcAvailable = false
        )
        assertThat(task?.id).isEqualTo("math")
    }

    @Test
    fun globalNfc_onDeviceWithoutNfc_fallsBackToMath() {
        val task = CaptchaTaskRegistry.resolveTask(
            perAlarmType = "NONE",
            globalTaskId = "nfc",
            qrCodeValue = null,
            nfcAvailable = false
        )
        assertThat(task?.id).isEqualTo("math")
    }

    @Test
    fun perAlarmNFC_onDeviceWithoutNfc_globalQrWithoutValue_fallsBackToMath() {
        // The NFC fallback lands on the global "qrcode" task — but with no QR value configured
        // that is equally unsolvable, so the QR check must apply to the *final* task too.
        val task = CaptchaTaskRegistry.resolveTask(
            perAlarmType = "NFC",
            globalTaskId = "qrcode",
            qrCodeValue = null,
            nfcAvailable = false
        )
        assertThat(task?.id).isEqualTo("math")
    }
}
