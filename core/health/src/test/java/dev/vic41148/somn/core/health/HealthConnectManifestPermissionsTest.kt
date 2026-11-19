package dev.vic41148.somn.core.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Regression guard for HEALTH-05: Health Connect rejects the *entire* authorization request
 * when any requested permission is missing from the manifest, so [HealthConnectManager.READ_PERMISSIONS]
 * and the manifest must stay in lockstep.
 */
class HealthConnectManifestPermissionsTest {

    @Test
    fun `every read permission requested at runtime is declared in the manifest`() {
        val missing = HealthConnectManager.READ_PERMISSIONS - manifestPermissions()
        assertTrue(
            "Permissions requested by HealthConnectManager but absent from AndroidManifest.xml: $missing",
            missing.isEmpty()
        )
    }

    @Test
    fun `manifest read permissions match READ_PERMISSIONS exactly`() {
        val declaredReadPermissions = manifestPermissions()
            .filter { it.startsWith("android.permission.health.READ_") }
            .toSet()

        assertEquals(HealthConnectManager.READ_PERMISSIONS, declaredReadPermissions)
    }

    @Test
    fun `write sleep permission is declared and requested`() {
        val declaredWritePermissions = manifestPermissions()
            .filter { it.startsWith("android.permission.health.WRITE_") }
            .toSet()

        assertEquals(HealthConnectManager.WRITE_PERMISSIONS, declaredWritePermissions)
    }

    @Test
    fun `READ_PERMISSIONS includes steps and exercise`() {
        assertTrue(
            "Expected android.permission.health.READ_STEPS in READ_PERMISSIONS",
            "android.permission.health.READ_STEPS" in HealthConnectManager.READ_PERMISSIONS
        )
        assertTrue(
            "Expected android.permission.health.READ_EXERCISE in READ_PERMISSIONS",
            "android.permission.health.READ_EXERCISE" in HealthConnectManager.READ_PERMISSIONS
        )
    }

    private fun manifestPermissions(): Set<String> {
        val manifest = manifestFile()
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(manifest)
        val nodes = document.getElementsByTagName("uses-permission")

        return buildSet {
            for (index in 0 until nodes.length) {
                val name = nodes.item(index).attributes.getNamedItem("android:name")?.nodeValue
                if (name != null) add(name)
            }
        }
    }

    private fun manifestFile(): File {
        System.getProperty("somn.health.manifest")?.let { configured ->
            val file = File(configured)
            assertTrue("Manifest configured by Gradle does not exist: $configured", file.isFile)
            return file
        }

        // Fallback for IDE runs where the Gradle system property is not set: walk up from the
        // working directory until the module manifest is found.
        var directory: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (directory != null) {
            File(directory, "src/main/AndroidManifest.xml").takeIf { it.isFile }?.let { return it }
            File(directory, "core/health/src/main/AndroidManifest.xml").takeIf { it.isFile }?.let { return it }
            directory = directory.parentFile
        }
        throw AssertionError("Could not locate core/health/src/main/AndroidManifest.xml")
    }
}
