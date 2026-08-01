package dev.vic41148.somn.core.domain.model

data class NasConfig(
    val host: String = "",
    val path: String = "",
    val username: String = "",
    val protocol: NasProtocol = NasProtocol.WEBDAV,
    val port: Int = 80,
    val isEnabled: Boolean = false
)
