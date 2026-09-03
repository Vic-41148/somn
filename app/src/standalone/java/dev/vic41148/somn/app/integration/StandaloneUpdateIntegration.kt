package dev.vic41148.somn.app.integration

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.vic41148.somn.app.updates.UpdateBanner
import dev.vic41148.somn.app.updates.UpdateRestorePrompt
import dev.vic41148.somn.core.data.repository.SomnPreferencesRepository
import dev.vic41148.somn.core.data.update.UpdateScheduler
import dev.vic41148.somn.feature.settings.updates.UpdatesScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real self-updater, contributed into the [UpdateIntegration] multibinding set only on
 * `standalone` channel builds. Lives in the standalone source set next to app/updates so compiling
 * the store channel never pulls any updater type into the classpath.
 */
@Singleton
class StandaloneUpdateIntegration @Inject constructor(
    private val preferencesRepository: SomnPreferencesRepository,
    private val updateScheduler: UpdateScheduler
) : UpdateIntegration {

    override fun onAppCreated(application: Application) {
        updateScheduler.ensureScheduled()
        // Kick one immediate check so the Home banner isn't stale on the first frame of a new
        // install; the periodic job covers the rest. Gated on the master switch like the worker.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                if (preferencesRepository.updateAutoCheck.first()) {
                    updateScheduler.checkNow()
                }
            }
        }
    }

    override fun registerUpdateRoutes(builder: NavGraphBuilder, onBack: () -> Unit) {
        builder.composable("updates") {
            UpdatesScreen(onBack = onBack)
        }
    }

    @Composable
    override fun HomeBanner(onOpenUpdates: () -> Unit, onGoToBackup: () -> Unit) {
        UpdateBanner(onOpenUpdates = onOpenUpdates, onGoToBackup = onGoToBackup)
    }

    @Composable
    override fun RestorePrompt() {
        UpdateRestorePrompt()
    }
}