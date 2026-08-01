package dev.vic41148.somn.core.domain.model

/**
 * DATA-02: outcome of a [dev.vic41148.somn.core.domain.usecase.ImportSleepAsAndroidUseCase] run.
 * Import from a third-party format is inherently lossy — this is surfaced to the user rather
 * than silently swallowed, per the project's own research findings on Sleep as Android's
 * undocumented/drifting export schema (no official spec, community-reverse-engineered only).
 */
data class ImportResult(
    val sessions: List<SleepSession>,
    val skippedRowCount: Int,
    val warnings: List<String>
) {
    val importedCount: Int get() = sessions.size
}
