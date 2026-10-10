// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.map

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RegionInstallerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val region = "PMTiles".toByteArray() + byteArrayOf(3) + ByteArray(4096) { it.toByte() }

    private fun target() = File(tmp.root, "regions/panaji.pmtiles")

    private fun leftovers(target: File) = target.parentFile?.listFiles()?.map { it.name }.orEmpty()

    @Test
    fun firstInstallCopiesByteForByte() {
        val target = target()
        val result = RegionInstaller.install({ ByteArrayInputStream(region) }, target, "1")
        assertEquals(InstallResult.Installed(target, copied = true), result)
        assertArrayEquals(region, target.readBytes())
        assertFalse(File(target.parentFile, "panaji.pmtiles.tmp").exists())
    }

    @Test
    fun sameStampSkipsTheCopy() {
        val target = target()
        RegionInstaller.install({ ByteArrayInputStream(region) }, target, "1")
        var opened = false
        val open = {
            opened = true
            ByteArrayInputStream(region)
        }
        val result = RegionInstaller.install(open, target, "1")
        assertEquals(InstallResult.Installed(target, copied = false), result)
        assertFalse(opened)
    }

    @Test
    fun newStampCopiesAgain() {
        val target = target()
        RegionInstaller.install({ ByteArrayInputStream(region) }, target, "1")
        val updated = region + byteArrayOf(42)
        val result = RegionInstaller.install({ ByteArrayInputStream(updated) }, target, "2")
        assertEquals(InstallResult.Installed(target, copied = true), result)
        assertArrayEquals(updated, target.readBytes())
    }

    @Test
    fun changedFileSizeCopiesAgain() {
        val target = target()
        RegionInstaller.install({ ByteArrayInputStream(region) }, target, "1")
        target.appendBytes(byteArrayOf(1))
        val result = RegionInstaller.install({ ByteArrayInputStream(region) }, target, "1")
        assertEquals(InstallResult.Installed(target, copied = true), result)
        assertArrayEquals(region, target.readBytes())
    }

    @Test
    fun streamFailingMidwayLeavesNoTargetAndNoTmp() {
        val target = target()
        val failing = object : InputStream() {
            private var served = 0

            override fun read(): Int {
                if (served >= 100) throw IOException("disk full")
                return region[served++].toInt() and 0xff
            }
        }
        assertEquals(InstallResult.Failed, RegionInstaller.install({ failing }, target, "1"))
        assertFalse(target.exists())
        assertEquals(emptyList<String>(), leftovers(target))
    }

    @Test
    fun badHeaderIsRejected() {
        val target = target()
        val notPmTiles = "PMTiles".toByteArray() + byteArrayOf(2) + ByteArray(100)
        assertEquals(InstallResult.Invalid, RegionInstaller.install({ ByteArrayInputStream(notPmTiles) }, target, "1"))
        assertFalse(target.exists())
        assertEquals(emptyList<String>(), leftovers(target))
    }

    @Test
    fun tooShortFileIsRejected() {
        val target = target()
        val result = RegionInstaller.install({ ByteArrayInputStream(byteArrayOf(1)) }, target, "1")
        assertEquals(InstallResult.Invalid, result)
    }

    @Test
    fun missingAssetIsNotBundled() {
        val target = target()
        assertEquals(InstallResult.NotBundled, RegionInstaller.install({ null }, target, "1"))
        assertFalse(target.exists())
    }

    @Test
    fun headerCheckAcceptsPmTilesV3() {
        val file = tmp.newFile("x.pmtiles")
        file.writeBytes(region)
        assertTrue(RegionInstaller.hasPmTilesHeader(file))
    }

    /** A minimal POSIX tar header: "ustar" at byte 257, as Valhalla's tile extracts have. */
    private val tar = ByteArray(257) + "ustar".toByteArray() + ByteArray(250)

    @Test
    fun tarHeaderCheckAcceptsUstarAndRejectsOthers() {
        val file = tmp.newFile("x.tar")
        file.writeBytes(tar)
        assertTrue(RegionInstaller.hasTarHeader(file))
        file.writeBytes(region)
        assertFalse(RegionInstaller.hasTarHeader(file))
        file.writeBytes(ByteArray(10))
        assertFalse(RegionInstaller.hasTarHeader(file))
    }

    @Test
    fun customFormatCheckInstallsRoutingTiles() {
        val target = File(tmp.root, "regions/panaji-routing.tar")
        val result = RegionInstaller.install({ ByteArrayInputStream(tar) }, target, "1", RegionInstaller::hasTarHeader)
        assertEquals(InstallResult.Installed(target, copied = true), result)
        assertArrayEquals(tar, target.readBytes())
        val again = RegionInstaller.install({ ByteArrayInputStream(tar) }, target, "1", RegionInstaller::hasTarHeader)
        assertEquals(InstallResult.Installed(target, copied = false), again)
    }

    @Test
    fun customFormatCheckRejectsTheWrongFormat() {
        val target = File(tmp.root, "regions/panaji-routing.tar")
        val result = RegionInstaller.install({
            ByteArrayInputStream(region)
        }, target, "1", RegionInstaller::hasTarHeader)
        assertEquals(InstallResult.Invalid, result)
        assertFalse(target.exists())
    }

    @Test
    fun sqliteHeaderCheckAcceptsSqlite3Only() {
        val file = tmp.newFile("x.sqlite")
        file.writeBytes("SQLite format 3\u0000".toByteArray() + ByteArray(100))
        assertTrue(RegionInstaller.hasSqliteHeader(file))
        file.writeBytes("SQLite format 2\u0000".toByteArray() + ByteArray(100))
        assertFalse(RegionInstaller.hasSqliteHeader(file))
        file.writeBytes(tar)
        assertFalse(RegionInstaller.hasSqliteHeader(file))
    }
}
