package dev.vic41148.somn.core.ui.battery

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether Somn currently holds the OS battery-optimization exemption
 * (`IGNORE_BATTERY_OPTIMIZATION`). OEM power-management layers (Samsung, Xiaomi, Huawei)
 * are known to silently revoke this after an OTA update, so [recheck] is meant to be
 * called on every app resume — not just once at onboarding — per REL-03.
 */
object BatteryExemptionState {
    private val _isExempted = MutableStateFlow(true)
    val isExempted: StateFlow<Boolean> = _isExempted.asStateFlow()

    fun recheck(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        _isExempted.value = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * The standard system dialog directly asking to exempt Somn from battery optimization — the
     * exact thing [isExempted] checks, on every OEM, in one tap, no navigation required.
     *
     * This used to try an OEM-specific settings *screen* first (e.g. Samsung's Device Care >
     * Battery hub) and only fall back to this intent if the OEM one did not resolve. That was
     * backwards: those hub screens almost always resolve, so the OEM path won every time, and it
     * lands on a general battery overview — not a per-app toggle — leaving the user to hunt for
     * Somn themselves. [oemBackgroundRestrictionIntent] is kept as a separate, secondary action for
     * the genuinely OEM-only "autostart"/background-restriction screen, which this standard
     * intent cannot reach and which some OEMs enforce in addition to battery optimization.
     */
    fun buildFixIntent(context: Context): Intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    )

    /**
     * Best-effort deep link to the OEM's separate autostart/background-activity screen (Samsung,
     * Xiaomi, Huawei). Distinct from battery optimization — some OEMs kill backgrounded apps via
     * this mechanism even when [isExempted] is true — so this is offered as an additional, optional
     * step, never as a substitute for [buildFixIntent]. Null if the current OEM has no such screen,
     * or [buildFixIntent] should be treated as the only available action.
     */
    fun oemBackgroundRestrictionIntent(context: Context): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intent = when {
            "samsung" in manufacturer -> Intent().setComponent(
                ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            )
            "xiaomi" in manufacturer -> Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST").apply {
                putExtra("package_name", context.packageName)
                putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager))
            }
            "huawei" in manufacturer -> Intent().setComponent(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            )
            else -> null
        }
        return intent?.takeIf { it.resolveActivity(context.packageManager) != null }
    }
}
