package dev.vic41148.somn.core.data.backup

import com.google.common.truth.Truth.assertThat
import dev.vic41148.somn.core.domain.model.NasConfig
import org.junit.Test

/**
 * Regression cover for the WebDAV credential leak: the scheme used to be inferred from the port,
 * so any NAS not on 443 was contacted over plain HTTP and its Basic-auth header went out in the
 * clear. The scheme must now follow [NasConfig.useHttps] and nothing else.
 */
class NasBaseUrlTest {

    private fun config(
        port: Int,
        useHttps: Boolean,
        host: String = "nas.local",
        path: String = "somn"
    ) = NasConfig(host = host, path = path, port = port, useHttps = useHttps)

    @Test
    fun `a non-standard port stays on https`() {
        // The exact case that used to leak: 8443 is not 443, so the old code fell back to http.
        val url = NasClientImpl.buildBaseUrl(config(port = 8443, useHttps = true))

        assertThat(url).isEqualTo("https://nas.local:8443/somn")
    }

    @Test
    fun `port 5005 stays on https`() {
        assertThat(NasClientImpl.buildBaseUrl(config(port = 5005, useHttps = true)))
            .startsWith("https://")
    }

    @Test
    fun `https on its default port omits the port suffix`() {
        assertThat(NasClientImpl.buildBaseUrl(config(port = 443, useHttps = true)))
            .isEqualTo("https://nas.local/somn")
    }

    @Test
    fun `http on its default port omits the port suffix`() {
        assertThat(NasClientImpl.buildBaseUrl(config(port = 80, useHttps = false)))
            .isEqualTo("http://nas.local/somn")
    }

    @Test
    fun `plain http is only ever used when the user explicitly asked for it`() {
        assertThat(NasClientImpl.buildBaseUrl(config(port = 8080, useHttps = false)))
            .isEqualTo("http://nas.local:8080/somn")
    }

    @Test
    fun `port 443 with https disabled is still http`() {
        // Symmetry check: the port must not override the explicit choice in either direction.
        assertThat(NasClientImpl.buildBaseUrl(config(port = 443, useHttps = false)))
            .startsWith("http://")
    }

    @Test
    fun `a leading slash on the path does not produce a double slash`() {
        assertThat(NasClientImpl.buildBaseUrl(config(port = 443, useHttps = true, path = "/somn")))
            .isEqualTo("https://nas.local/somn")
    }

    @Test
    fun `a fresh config defaults to https`() {
        // The default matters as much as the logic: an unconfigured NAS must not start out
        // unencrypted just because the user never opened the toggle.
        assertThat(NasConfig().useHttps).isTrue()
        assertThat(NasClientImpl.buildBaseUrl(NasConfig(host = "nas.local", path = "somn")))
            .startsWith("https://")
    }
}
