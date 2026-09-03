package dev.vic41148.somn.app.integration

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder

/**
 * Channel-scoped entry points for the in-app self-updater.
 *
 * `standalone` builds (GitHub Releases + self-hosted repo) contribute a real implementation into
 * the [UpdateIntegration] multibinding set - banner, restore prompt, "updates" route and the daily
 * check schedule. `store` builds (F-Droid / IzzyOnDroid / Accrescent) contribute only the no-op,
 * so the shared navigation graph, MainActivity and SomnApp render no updater UI at all. Every
 * method has an empty default so the store channel never implements anything and the updater code
 * stays byte-out of store APKs (it lives in standalone-only source sets and core:data's store
 * variant excludes the update package).
 */
interface UpdateIntegration {

    /** Called from [Application.onCreate] - standalone schedules the daily check worker here. */
    fun onAppCreated(application: Application) {
    }

    /** Registers the "updates" navigation destination on the app's nav graph. */
    fun registerUpdateRoutes(builder: NavGraphBuilder, onBack: () -> Unit) {
    }

    /** Home header slot rendered above the tracking CTA when a newer release is staged. */
    @Composable
    fun HomeBanner(onOpenUpdates: () -> Unit, onGoToBackup: () -> Unit) {
    }

    /** First-launch post-update backup-restore offer dialog. */
    @Composable
    fun RestorePrompt() {
    }
}