package dev.vic41148.somn.core.data.update

import dev.vic41148.somn.core.domain.model.ReleaseInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure org.json parsing of a GitHub/Forgejo releases API response, deliberately defensive: any
 * unknown or missing field maps to a safe default so an API shape tweak cannot crash the checker.
 * Network handling stays in [UpdateRepository]; this class is unit-testable with static JSON.
 */
object ReleaseParser {

    /** A recognizable "checksums" line: filename + 64 lowercase hex digits. */
    private val CHECKSUM_LINE =
        Regex("""(?i)^\s*([a-f0-9]{64})\s+(\S+\.apk)\s*$""")

    private val SHA_IN_BODY = Regex("""(?i)([a-f0-9]{64})""")

    /** One release object (the `/releases/latest` shape). */
    fun parseLatest(raw: String): ReleaseInfo {
        val json = JSONObject(raw)
        return parseRelease(json)
    }

    /** An array of releases (`/releases` shape), newest-first, drafts filtered by the API already. */
    fun parseHistory(raw: String): List<ReleaseInfo> {
        val array = JSONArray(raw)
        val out = ArrayList<ReleaseInfo>(array.length())
        for (i in 0 until array.length()) {
            out.add(parseRelease(array.getJSONObject(i)))
        }
        // De-duplicate by tag defensively - a force-pushed tag should not show up twice.
        return out.distinctBy { it.tag }
    }

    private fun parseRelease(json: JSONObject): ReleaseInfo {
        val tag = json.optString("tag_name", "").trim()
        val assets = json.optJSONArray("assets") ?: JSONArray()
        var apkName: String? = null
        var apkUrl: String? = null
        var checksumAssetUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            val url = asset.optString("browser_download_url", "")
            when {
                name.endsWith(".apk", ignoreCase = true) && apkName == null -> {
                    apkName = name
                    apkUrl = url.ifBlank { null }
                }
                (name.equals("checksums.txt", ignoreCase = true) ||
                    name.contains("sha256", ignoreCase = true)) && checksumAssetUrl == null -> {
                    checksumAssetUrl = url.ifBlank { null }
                }
            }
        }
        val version = versionNameFromTag(tag)

        // Checksum from the body is only a fallback for releases that skip the checksums.txt asset;
        // a matching line naming the specific APK is preferred over the first bare hash.
        val body = json.optString("body", "")
        val checksumFromBody = extractChecksumFromBody(body, apkName)

        return ReleaseInfo(
            tag = tag,
            versionName = version,
            apkUrl = apkUrl,
            checksumSha256 = checksumFromBody,
            checksumAssetUrl = checksumAssetUrl,
            notes = body,
            publishedAt = json.optString("published_at", "").ifBlank { null },
            isPrerelease = json.optBoolean("prerelease", false)
        )
    }

    /** "v0.1.2" -> "0.1.2"; anything else is returned as-is (never empty for a real release). */
    fun versionNameFromTag(tag: String): String {
        val trimmed = tag.trim()
        if (trimmed.length > 1 && (trimmed[0] == 'v' || trimmed[0] == 'V')) return trimmed.substring(1)
        return trimmed
    }

    /** Scans a checksums.txt asset body for the line naming [apkName]; falls back to the first hash. */
    fun extractChecksumFromBody(body: String, apkName: String?): String? {
        if (body.isBlank()) return null
        for (line in body.lineSequence()) {
            val match = CHECKSUM_LINE.matchEntire(line) ?: continue
            val hash = match.groupValues[1].lowercase()
            if (apkName == null || match.groupValues[2].equals(apkName, ignoreCase = true)) {
                return hash
            }
        }
        // No recognizably named line: take the first bare 64-hex string as the last resort.
        return SHA_IN_BODY.find(body)?.groupValues?.get(1)?.lowercase()
    }
}