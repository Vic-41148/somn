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

    /** Best-effort OEM-specific autostart/battery settings screen; falls back to the stock Android exemption request. */
    fun buildFixIntent(context: Context): Intent {
        val oemIntent = oemSettingsIntent(context)
        if (oemIntent != null && oemIntent.resolveActivity(context.packageManager) != null) {
            return oemIntent
        }
        return Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
    }

    private fun oemSettingsIntent(context: Context): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
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
    }
}
