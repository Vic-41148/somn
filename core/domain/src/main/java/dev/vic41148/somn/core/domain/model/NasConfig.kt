package dev.vic41148.somn.core.domain.model

data class NasConfig(
    val host: String = "",
    val path: String = "",
    val username: String = "",
    val protocol: NasProtocol = NasProtocol.WEBDAV,
    val port: Int = 443,
    val isEnabled: Boolean = false,
    /**
     * Whether to talk to the NAS over TLS.
     *
     * This used to be inferred from [port] — anything other than 443 silently fell back to plain
     * HTTP, which put the WebDAV Basic-auth credentials on the wire in the clear for every user
     * running their NAS on a non-standard port. Transport security is now an explicit choice that
     * defaults to on, independent of whatever port the server happens to listen on.
     */
    val useHttps: Boolean = true
)
