package dev.vic41148.somn.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.vic41148.somn.core.data.repository.HabitLogRepository
import dev.vic41148.somn.core.data.repository.SleepRepository
import dev.vic41148.somn.core.data.repository.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * DEBUG BUILD ONLY - registered in src/debug/AndroidManifest.xml, so it is present only in the
 * debug variant and never in release. Receives an adb broadcast (see seed.sh) and seeds a week of
 * realistic sleep data so the tracking screens have something to render on a fresh debug install.
 */
class DebugSeedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEED) return
        goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val entry = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    DebugSeedEntryPoint::class.java
                )
                DebugSeeder.seed(
                    sleepRepo = entry.sleepRepository(),
                    habitRepo = entry.habitLogRepository(),
                    tagRepo = entry.tagRepository()
                )
                Log.i(TAG, "Seeded a week of debug data")
            } catch (t: Throwable) {
                Log.e(TAG, "Seed failed", t)
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DebugSeedEntryPoint {
        fun sleepRepository(): SleepRepository
        fun habitLogRepository(): HabitLogRepository
        fun tagRepository(): TagRepository
    }

    companion object {
        private const val TAG = "DebugSeedReceiver"
        const val ACTION_SEED = "dev.vic41148.somn.DEBUG_SEED_DATA"
    }
}
