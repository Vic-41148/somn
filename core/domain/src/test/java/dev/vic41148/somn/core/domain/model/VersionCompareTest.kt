package dev.vic41148.somn.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun `simple semver ordering`() {
        assertTrue(VersionCompare.isNewer("0.2.0", "0.1.2"))
        assertTrue(VersionCompare.isNewer("2.0.0", "1.9.9"))
        assertTrue(VersionCompare.isNewer("1.10.0", "1.9.0"))
        assertTrue(!VersionCompare.isNewer("0.1.2", "0.2.0"))
    }

    @Test
    fun `v prefix is ignored`() {
        assertTrue(VersionCompare.isNewer("v0.2.0", "0.1.9"))
        assertEquals(0, VersionCompare.compare("v0.1.2", "0.1.2"))
    }

    @Test
    fun `prerelease sorts below its base`() {
        assertTrue(VersionCompare.isNewer("0.2.0", "0.2.0-rc.1"))
        assertTrue(VersionCompare.isNewer("0.2.0-rc.1", "0.1.9"))
        assertTrue(VersionCompare.compare("0.2.0-rc.1", "0.2.0") < 0)
    }

    @Test
    fun `equal versions compare to zero`() {
        assertEquals(0, VersionCompare.compare("0.1.2", "0.1.2"))
        assertEquals(0, VersionCompare.compare("", ""))
    }

    @Test
    fun `unparseable never beats a real version`() {
        assertTrue(!VersionCompare.isNewer("garbage", "0.1.2"))
        assertTrue(VersionCompare.isNewer("0.1.2", "garbage"))
        assertTrue(!VersionCompare.isNewer("", "0.1.2"))
    }
}