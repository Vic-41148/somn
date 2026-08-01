package dev.vic41148.somn.core.domain.model

/**
 * Transports NAS backup can actually speak.
 *
 * SMB and NFS used to be listed here with stub implementations that logged a warning and returned
 * false. Nothing could select them — the Settings picker is WebDAV-only, and both `valueOf` call
 * sites coerce unrecognised stored values back to [WEBDAV] — so they only ever advertised support
 * that did not exist. Add a value here when there is an implementation behind it.
 */
enum class NasProtocol {
    WEBDAV
}
