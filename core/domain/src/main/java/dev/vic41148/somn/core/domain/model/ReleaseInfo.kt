package dev.vic41148.somn.core.domain.model

/**
 * A distribution release published to the releases API (GitHub/Forgejo style), capped to the
 * fields the update flow actually needs. The full release object has dozens more. Anything else is
 * stripped at parse time so a surprise API field change cannot leak into prefs or UI state.
 *
 * [checksumSha256] is the hex sha256 of [apkUrl]'s file - the mandatory integrity gate before any
 * install. Because release APIs carry no versionCode, [versionName] is what [VersionCompare]
 * orders releases by. [versionCode] is only populated when a release supplies one (ours does not).
 */
data class ReleaseInfo(
    val tag: String,
    val versionName: String,
    val versionCode: Int = 0,
    val apkUrl: String? = null,
    val checksumSha256: String? = null,
    val checksumAssetUrl: String? = null,
    val notes: String = "",
    val publishedAt: String? = null,
    val isPrerelease: Boolean = false
) {
    companion object {
        val NONE = ReleaseInfo(tag = "", versionName = "")
    }
}

/**
 * The release the update checker most recently found and saved for the UI. Persisted so the banner
 * can render without a network round-trip. The system clears it when the user skips, dismisses, or installs it.
 */
data class StagedRelease(
    val tag: String,
    val versionName: String,
    val notes: String,
    val apkUrl: String?,
    val sha256: String?,
    val atMs: Long
) {
    val isPresent: Boolean get() = tag.isNotBlank()
}

/**
 * Coarse semver ordering for release tags: `1.2.0 > 1.2.0-rc.1 > 1.1.9`. Numeric dot segments are
 * compared numerically, any non-numeric tail (prerelease/build metadata) is treated as lower-precedence
 * than the same base, and an unparseable string is treated as 0 so it never beats a real version.
 * This is deliberately simpler than full semver 2.0 - Somn's own releases control the tag shape.
 */
object VersionCompare {

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    fun compare(a: String, b: String): Int {
        val partsA = split(a)
        val partsB = split(b)
        val aBase = partsA.first
        val bBase = partsB.first
        val baseComparison = compareSegments(aBase, bBase)
        if (baseComparison != 0) return baseComparison
        // Same base version: a release without a prerelease suffix wins over one with it.
        return when {
            partsA.second.isEmpty() && partsB.second.isEmpty() -> 0
            partsA.second.isEmpty() -> 1
            partsB.second.isEmpty() -> -1
            else -> compareSegments(partsA.second, partsB.second)
        }
    }

    private data class Split(val first: List<String>, val second: List<String>)

    private fun split(raw: String): Split {
        val trimmed = raw.trim().removePrefix("v").removePrefix("V")
        // Everything before the first '-' is the base. The tail is the prerelease/build bit.
        val dash = trimmed.indexOf('-')
        if (dash == -1) return Split(splitDots(trimmed), emptyList())
        return Split(splitDots(trimmed.substring(0, dash)), splitDots(trimmed.substring(dash + 1)))
    }

    private fun splitDots(s: String): List<String> = s.split('.').filter { it.isNotBlank() }

    private fun compareSegments(a: List<String>, b: List<String>): Int {
        val length = maxOf(a.size, b.size)
        for (i in 0 until length) {
            val x = a.getOrNull(i)?.toIntOrNull() ?: 0
            val y = b.getOrNull(i)?.toIntOrNull() ?: 0
            if (x != y) return x.compareTo(y)
        }
        return a.size.compareTo(b.size)
    }
}