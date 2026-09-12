package dev.vic41148.somn.core.domain.model

/**
 * Which color scheme the app uses. SYSTEM follows the OS dark mode,
 * LIGHT and DARK pin the scheme regardless of system setting.
 */
enum class ThemeMode(val displayName: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}
