package dev.vic41148.somn.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "somn_prefs")

@Singleton
class SomnPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptionUtils: dev.vic41148.somn.core.data.backup.EncryptionUtils
) {
    companion object {
        /** Sleep-talk clips are pruned after a week unless the user opts out. */
        const val DEFAULT_CLIP_RETENTION_DAYS = 7

        /** Sentinel for [clipRetentionDays] meaning "never prune". */
        const val CLIP_RETENTION_KEEP_FOREVER = 0
    }

    /**
     * Keystore envelope for sensitive string prefs (NAS endpoint parts, QR value, backup
     * URI, menopause answers). New writes are always sealed; reads accept pre-encryption
     * plaintext so v0.1.2 installs keep working until [migrateSensitivePrefsToEncrypted] runs.
     */
    private fun seal(plain: String): String =
        android.util.Base64.encodeToString(
            encryptionUtils.encryptBytes(plain.toByteArray(Charsets.UTF_8)),
            android.util.Base64.NO_WRAP
        )

    private fun unseal(stored: String?): String? {
        if (stored == null) return null
        return try {
            encryptionUtils.decryptBytes(
                android.util.Base64.decode(stored, android.util.Base64.NO_WRAP)
            ).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            stored
        }
    }

    /**
     * One-time upgrade: re-writes any still-plaintext sensitive values sealed. Only values
     * that fail to decrypt get sealed, so re-runs are no-ops.
     */
    suspend fun migrateSensitivePrefsToEncrypted() {
        context.dataStore.edit { prefs ->
            listOf(
                PreferencesKeys.QR_CODE_VALUE,
                PreferencesKeys.BACKUP_URI,
                PreferencesKeys.NAS_HOST,
                PreferencesKeys.NAS_PATH,
                PreferencesKeys.NAS_USERNAME,
                PreferencesKeys.MENO_ANSWERS_CSV
            ).forEach { key ->
                val raw = prefs[key] ?: return@forEach
                val alreadySealed = try {
                    encryptionUtils.decryptBytes(
                        android.util.Base64.decode(raw, android.util.Base64.NO_WRAP)
                    )
                    true
                } catch (_: Exception) {
                    false
                }
                if (!alreadySealed) prefs[key] = seal(raw)
            }
        }
    }

    private object PreferencesKeys {
        val SELECTED_CAPTCHA_TASK_ID = stringPreferencesKey("selected_captcha_task_id")
        val QR_CODE_VALUE = stringPreferencesKey("qr_code_value")
        val MAX_SNOOZE_COUNT = intPreferencesKey("max_snooze_count")
        val BACKUP_URI = stringPreferencesKey("backup_uri")
        val TRACKING_MODE = stringPreferencesKey("tracking_mode")
        // NAS config
        val NAS_ENABLED = booleanPreferencesKey("nas_enabled")
        val NAS_HOST = stringPreferencesKey("nas_host")
        val NAS_PATH = stringPreferencesKey("nas_path")
        val NAS_USERNAME = stringPreferencesKey("nas_username")
        val NAS_PROTOCOL = stringPreferencesKey("nas_protocol")
        val NAS_PORT = intPreferencesKey("nas_port")
        /** Explicit TLS choice for the NAS connection; never inferred from [NAS_PORT]. */
        val NAS_USE_HTTPS = booleanPreferencesKey("nas_use_https")
        /** AES-256-GCM ciphertext (IV + tag included), Base64-encoded — never the raw password. */
        val NAS_PASSWORD_ENCRYPTED = stringPreferencesKey("nas_password_encrypted")
        val OVERSLEEP_THRESHOLD_MINUTES = intPreferencesKey("oversleep_threshold_minutes")
        val WAKE_VERIFICATION_ENABLED = booleanPreferencesKey("wake_verification_enabled")
        val WAKE_VERIFICATION_WINDOW_SECONDS = intPreferencesKey("wake_verification_window_seconds")
        /** Material You dynamic color on Android 12+, on by default so the theme stays as it was. */
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        /** R1: Morning Ready verdict + Today outlook cards on Home, on by default. */
        val SHOW_READINESS_CARD = booleanPreferencesKey("show_readiness_card")
        /** R2 Rest Mode start timestamp. Absent = off; sick nights on/after this leave baselines. */
        val REST_MODE_SINCE = longPreferencesKey("rest_mode_since")
        val MENO_ANSWERS_CSV = stringPreferencesKey("meno_answers_csv")        /**
         * Which hemisphere seasonal analysis uses. Absent (or unmappable) = AUTO — the
         * UTC-offset heuristic in SeasonalAnalysisUseCase stays in charge.
         */
        val HEMISPHERE_OVERRIDE = stringPreferencesKey("hemisphere_override")
        val HEALTH_CONNECT_ENABLED = booleanPreferencesKey("health_connect_enabled")
        val YAMNET_CLASSIFICATION_ENABLED = booleanPreferencesKey("yamnet_classification_enabled")
        /**
         * The user's backup recovery passphrase, Keystore-encrypted at rest so unattended sync can
         * use it. Keystore protects it *on* the device; the passphrase itself is what makes backups
         * readable *off* the device, which is why the user is also shown it once to store elsewhere.
         */
        val BACKUP_PASSPHRASE_ENCRYPTED = stringPreferencesKey("backup_passphrase_encrypted")
        val SNORE_NUDGE_ENABLED = booleanPreferencesKey("snore_nudge_enabled")
        /**
         * Days to keep sleep-talk recordings on disk. [CLIP_RETENTION_KEEP_FOREVER] disables
         * pruning entirely — an explicit opt-in, because the default has to be one that forgets.
         */
        val CLIP_RETENTION_DAYS = intPreferencesKey("clip_retention_days")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val HAPTICS_INTENSITY = stringPreferencesKey("haptics_intensity")
        // ── In-app self-updater ───────────────────────────────────────
        val UPDATE_AUTO_CHECK = booleanPreferencesKey("update_auto_check")
        val UPDATE_CHECK_INTERVAL_DAYS = intPreferencesKey("update_check_interval_days")
        val UPDATE_LAST_CHECKED_MS = longPreferencesKey("update_last_checked_ms")
        val UPDATE_SKIPPED_VERSION = stringPreferencesKey("update_skipped_version")
        val UPDATE_STAGED_RELEASE = stringPreferencesKey("update_staged_release")
        val UPDATE_RESTORE_PROMPT_SHOWN = booleanPreferencesKey("update_restore_prompt_shown")
    }

    val trackingMode: Flow<dev.vic41148.somn.core.domain.model.TrackingMode> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            try {
                dev.vic41148.somn.core.domain.model.TrackingMode.valueOf(
                    preferences[PreferencesKeys.TRACKING_MODE] ?: "ACCELEROMETER"
                )
            } catch (e: Exception) {
                dev.vic41148.somn.core.domain.model.TrackingMode.ACCELEROMETER
            }
        }

    val backupUri: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            unseal(preferences[PreferencesKeys.BACKUP_URI])
        }

    val selectedCaptchaTaskId: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            preferences[PreferencesKeys.SELECTED_CAPTCHA_TASK_ID] ?: "math"
        }

    val qrCodeValue: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            unseal(preferences[PreferencesKeys.QR_CODE_VALUE])
        }

    val maxSnoozeCount: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            preferences[PreferencesKeys.MAX_SNOOZE_COUNT] ?: 3
        }

    suspend fun updateTrackingMode(mode: dev.vic41148.somn.core.domain.model.TrackingMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TRACKING_MODE] = mode.name
        }
    }

    suspend fun updateBackupUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(PreferencesKeys.BACKUP_URI)
            } else {
                preferences[PreferencesKeys.BACKUP_URI] = seal(uri)
            }
        }
    }

    suspend fun updateSelectedCaptchaTask(taskId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_CAPTCHA_TASK_ID] = taskId
        }
    }

    suspend fun updateQrCodeValue(value: String?) {
        context.dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(PreferencesKeys.QR_CODE_VALUE)
            } else {
                preferences[PreferencesKeys.QR_CODE_VALUE] = seal(value)
            }
        }
    }

    suspend fun updateMaxSnoozeCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_SNOOZE_COUNT] = count
        }
    }

    /** SESS-03: minutes beyond the user's target sleep duration before a session is flagged oversleep. */
    val oversleepThresholdMinutes: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.OVERSLEEP_THRESHOLD_MINUTES] ?: 60 }

    suspend fun updateOversleepThresholdMinutes(minutes: Int) {
        context.dataStore.edit { it[PreferencesKeys.OVERSLEEP_THRESHOLD_MINUTES] = minutes }
    }

    /** WAKE-01: whether to require a post-dismiss wake confirmation before fully silencing the alarm. */
    val wakeVerificationEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.WAKE_VERIFICATION_ENABLED] ?: true }

    suspend fun updateWakeVerificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.WAKE_VERIFICATION_ENABLED] = enabled }
    }

    /** WAKE-01: seconds the user has to confirm they're awake before WAKE-02's re-ring fires. */
    val wakeVerificationWindowSeconds: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.WAKE_VERIFICATION_WINDOW_SECONDS] ?: 15 }

    suspend fun updateWakeVerificationWindowSeconds(seconds: Int) {
        context.dataStore.edit { it[PreferencesKeys.WAKE_VERIFICATION_WINDOW_SECONDS] = seconds }
    }

    /** THEME-01: whether Material You should tint the app from the wallpaper on Android 12+. */
    val useDynamicColor: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.USE_DYNAMIC_COLOR] ?: true }

    suspend fun updateUseDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.USE_DYNAMIC_COLOR] = enabled }
    }

    /** R1: whether the Morning Ready verdict + Today outlook cards show on Home. */
    val showReadinessCard: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.SHOW_READINESS_CARD] ?: true }

    suspend fun updateShowReadinessCard(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SHOW_READINESS_CARD] = enabled }
    }

    /** R2: Rest Mode boundary, null when off. Set = now, clear = remove the key. */
    val restModeSince: Flow<Long?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.REST_MODE_SINCE] }

    suspend fun setRestModeSince(sinceMillis: Long?) {
        context.dataStore.edit {
            if (sinceMillis == null) it.remove(PreferencesKeys.REST_MODE_SINCE)
            else it[PreferencesKeys.REST_MODE_SINCE] = sinceMillis
        }
    }

    /**
     * R5 menopause check-in answers as "2,0,3,..." (question order = MENOPAUSE_QUESTIONS).
     * Null until first completed; prefs, not Room — questionnaire data stays a setting.
     */
    val menoAnswers: Flow<List<Int>?> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            unseal(prefs[PreferencesKeys.MENO_ANSWERS_CSV])?.split(",")?.mapNotNull { it.toIntOrNull() }
        }

    suspend fun saveMenoAnswers(answers: List<Int>) {
        context.dataStore.edit { it[PreferencesKeys.MENO_ANSWERS_CSV] = seal(answers.joinToString(",")) }
    }

    /**
     * Hemisphere pin for seasonal analysis — [HemisphereOverride.AUTO] keeps the UTC-offset
     * heuristic, NORTHERN/SOUTHERN force the season mapping.
     */
    val hemisphereOverride: Flow<dev.vic41148.somn.core.domain.model.HemisphereOverride> =
        context.dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { prefs ->
                val raw = prefs[PreferencesKeys.HEMISPHERE_OVERRIDE]
                try {
                    dev.vic41148.somn.core.domain.model.HemisphereOverride.valueOf(raw ?: "AUTO")
                } catch (e: Exception) {
                    dev.vic41148.somn.core.domain.model.HemisphereOverride.AUTO
                }
            }

    suspend fun updateHemisphereOverride(
        override: dev.vic41148.somn.core.domain.model.HemisphereOverride
    ) {
        context.dataStore.edit { it[PreferencesKeys.HEMISPHERE_OVERRIDE] = override.name }
    }

    /** HEALTH-01/02: user opt-in — off by default, syncing external health data is not implied by installing the app. */
    val healthConnectEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.HEALTH_CONNECT_ENABLED] ?: false }

    suspend fun updateHealthConnectEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HEALTH_CONNECT_ENABLED] = enabled }
    }

    /**
     * Task 14 (AUDIO-01) — off by default. Gates YAMNet-based classification as an alternative
     * to the ZCR heuristic in [dev.vic41148.somn.core.audio.AudioEventClassifier] so it can be
     * A/B'd rather than silently replacing the existing (already-shipped) heuristic. Not
     * validated for accuracy (AUDIO-02) or battery impact (AUDIO-03) — those are separate,
     * still-open follow-ups.
     */
    val yamnetClassificationEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.YAMNET_CLASSIFICATION_ENABLED] ?: false }

    suspend fun updateYamnetClassificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.YAMNET_CLASSIFICATION_ENABLED] = enabled }
    }

    /** Whether SleepTrackingService vibrates the phone as a gentle nudge on detected snoring. On by default (existing behavior), but with no way to turn it off before this. */
    val snoreNudgeEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.SNORE_NUDGE_ENABLED] ?: true }

    suspend fun updateSnoreNudgeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SNORE_NUDGE_ENABLED] = enabled }
    }

    /**
     * How long sleep-talk WAV clips survive on disk, in days. Recordings of someone talking in
     * their sleep are about the most sensitive thing this app holds, so the default forgets them
     * after a week rather than keeping them until the user thinks to look.
     */
    val clipRetentionDays: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.CLIP_RETENTION_DAYS] ?: DEFAULT_CLIP_RETENTION_DAYS }

    suspend fun updateClipRetentionDays(days: Int) {
        context.dataStore.edit { it[PreferencesKeys.CLIP_RETENTION_DAYS] = days }
    }

    val hapticsEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.HAPTICS_ENABLED] ?: true }

    suspend fun updateHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HAPTICS_ENABLED] = enabled }
    }

    val hapticsIntensity: Flow<dev.vic41148.somn.core.domain.haptic.HapticsIntensity> =
        context.dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { prefs ->
                try {
                    dev.vic41148.somn.core.domain.haptic.HapticsIntensity.valueOf(
                        prefs[PreferencesKeys.HAPTICS_INTENSITY] ?: "STANDARD"
                    )
                } catch (e: Exception) {
                    dev.vic41148.somn.core.domain.haptic.HapticsIntensity.STANDARD
                }
            }

    suspend fun updateHapticsIntensity(intensity: dev.vic41148.somn.core.domain.haptic.HapticsIntensity) {
        context.dataStore.edit { it[PreferencesKeys.HAPTICS_INTENSITY] = intensity.name }
    }

    // ── NAS Preferences ──────────────────────────────────────────────

    val nasEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.NAS_ENABLED] ?: false }

    val nasHost: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { unseal(it[PreferencesKeys.NAS_HOST]) ?: "" }

    val nasPath: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { unseal(it[PreferencesKeys.NAS_PATH]) ?: "/somn" }

    val nasUsername: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { unseal(it[PreferencesKeys.NAS_USERNAME]) ?: "" }

    val nasProtocol: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.NAS_PROTOCOL] ?: "WEBDAV" }

    val nasPort: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.NAS_PORT] ?: 443 }

    /** Defaults to true: an unconfigured NAS connection must not start out unencrypted. */
    val nasUseHttps: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.NAS_USE_HTTPS] ?: true }

    suspend fun updateNasUseHttps(useHttps: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.NAS_USE_HTTPS] = useHttps }
    }

    suspend fun updateNasEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.NAS_ENABLED] = enabled }
    }

    suspend fun updateNasHost(host: String) {
        context.dataStore.edit { it[PreferencesKeys.NAS_HOST] = seal(host) }
    }

    suspend fun updateNasPath(path: String) {
        context.dataStore.edit { it[PreferencesKeys.NAS_PATH] = seal(path) }
    }

    suspend fun updateNasUsername(username: String) {
        context.dataStore.edit { it[PreferencesKeys.NAS_USERNAME] = seal(username) }
    }

    suspend fun updateNasProtocol(protocol: String) {
        context.dataStore.edit { it[PreferencesKeys.NAS_PROTOCOL] = protocol }
    }

    suspend fun updateNasPort(port: Int) {
        context.dataStore.edit { it[PreferencesKeys.NAS_PORT] = port }
    }

    /** Encrypts [password] via [EncryptionUtils] (Android Keystore-backed AES-256-GCM) before persisting. */
    suspend fun updateNasPassword(password: String) {
        val encrypted = encryptionUtils.encryptBytes(password.toByteArray(Charsets.UTF_8))
        context.dataStore.edit {
            it[PreferencesKeys.NAS_PASSWORD_ENCRYPTED] = android.util.Base64.encodeToString(
                encrypted, android.util.Base64.NO_WRAP
            )
        }
    }

    /** Decrypts the stored NAS password, or null if none has been set. */
    suspend fun getNasPassword(): String? {
        val encoded = context.dataStore.data.map { it[PreferencesKeys.NAS_PASSWORD_ENCRYPTED] }
            .catch { if (it is IOException) emit(null) else throw it }
            .first()
            ?: return null
        val encrypted = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
        return String(encryptionUtils.decryptBytes(encrypted), Charsets.UTF_8)
    }

    // ---- Backup recovery passphrase ----

    /** True once a recovery passphrase exists — without one, backups cannot be encrypted portably. */
    val backupPassphraseSet: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { !it[PreferencesKeys.BACKUP_PASSPHRASE_ENCRYPTED].isNullOrBlank() }

    /** Encrypts [passphrase] via [EncryptionUtils] (Keystore-backed) before persisting. */
    suspend fun updateBackupPassphrase(passphrase: String) {
        val encrypted = encryptionUtils.encryptBytes(passphrase.toByteArray(Charsets.UTF_8))
        context.dataStore.edit {
            it[PreferencesKeys.BACKUP_PASSPHRASE_ENCRYPTED] = android.util.Base64.encodeToString(
                encrypted, android.util.Base64.NO_WRAP
            )
        }
    }

    /** Decrypts the stored recovery passphrase, or null if the user has not set one yet. */
    suspend fun getBackupPassphrase(): String? {
        val encoded = context.dataStore.data.map { it[PreferencesKeys.BACKUP_PASSPHRASE_ENCRYPTED] }
            .catch { if (it is IOException) emit(null) else throw it }
            .first()
            ?: return null
        val encrypted = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
        return String(encryptionUtils.decryptBytes(encrypted), Charsets.UTF_8)
    }

    // ---- In-app self-updater preferences ----

    val updateAutoCheck: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.UPDATE_AUTO_CHECK] ?: true }

    suspend fun updateUpdateAutoCheck(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.UPDATE_AUTO_CHECK] = enabled }
    }

    val updateCheckIntervalDays: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.UPDATE_CHECK_INTERVAL_DAYS] ?: 1 }

    suspend fun updateUpdateCheckIntervalDays(days: Int) {
        context.dataStore.edit { it[PreferencesKeys.UPDATE_CHECK_INTERVAL_DAYS] = days }
    }

    val updateLastCheckedMs: Flow<Long> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.UPDATE_LAST_CHECKED_MS] ?: -1L }

    suspend fun updateUpdateLastCheckedMs(ms: Long) {
        context.dataStore.edit { it[PreferencesKeys.UPDATE_LAST_CHECKED_MS] = ms }
    }

    val updateSkippedVersion: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.UPDATE_SKIPPED_VERSION] ?: "" }

    suspend fun updateUpdateSkippedVersion(tag: String) {
        context.dataStore.edit { it[PreferencesKeys.UPDATE_SKIPPED_VERSION] = tag }
    }

    val updateStagedRelease: Flow<dev.vic41148.somn.core.domain.model.StagedRelease> =
        context.dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { prefs ->
                prefs[PreferencesKeys.UPDATE_STAGED_RELEASE]?.let(::parseStagedRelease)
                    ?: dev.vic41148.somn.core.domain.model.StagedRelease("", "", "", null, null, 0L)
            }

    suspend fun updateUpdateStagedRelease(release: dev.vic41148.somn.core.domain.model.StagedRelease?) {
        context.dataStore.edit { prefs ->
            if (release == null) {
                prefs.remove(PreferencesKeys.UPDATE_STAGED_RELEASE)
            } else {
                prefs[PreferencesKeys.UPDATE_STAGED_RELEASE] = encodeStagedRelease(release)
            }
        }
    }

    val updateRestorePromptShown: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.UPDATE_RESTORE_PROMPT_SHOWN] ?: false }

    suspend fun updateUpdateRestorePromptShown(shown: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.UPDATE_RESTORE_PROMPT_SHOWN] = shown }
    }

    private fun encodeStagedRelease(r: dev.vic41148.somn.core.domain.model.StagedRelease): String {
        val json = org.json.JSONObject()
        json.put("tag", r.tag)
        json.put("versionName", r.versionName)
        json.put("notes", r.notes)
        r.apkUrl?.let { json.put("apkUrl", it) }
        r.sha256?.let { json.put("sha256", it) }
        json.put("atMs", r.atMs)
        return json.toString()
    }

    private fun parseStagedRelease(raw: String): dev.vic41148.somn.core.domain.model.StagedRelease {
        return try {
            val json = org.json.JSONObject(raw)
            dev.vic41148.somn.core.domain.model.StagedRelease(
                tag = json.optString("tag", ""),
                versionName = json.optString("versionName", ""),
                notes = json.optString("notes", ""),
                apkUrl = if (json.isNull("apkUrl")) null else json.optString("apkUrl", "").ifBlank { null },
                sha256 = if (json.isNull("sha256")) null else json.optString("sha256", "").ifBlank { null },
                atMs = json.optLong("atMs", 0L)
            )
        } catch (e: Exception) {
            dev.vic41148.somn.core.domain.model.StagedRelease("", "", "", null, null, 0L)
        }
    }
}
