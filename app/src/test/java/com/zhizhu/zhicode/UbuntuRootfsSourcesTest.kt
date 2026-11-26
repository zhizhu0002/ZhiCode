package com.zhizhu.zhicode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UbuntuRootfsSourcesTest {

    private val sampleSums = """
        7b2dced6dd56ad5e4a813fa25c8de307b655fdabc6ea9213175a92c48dabb048 *ubuntu-base-24.04.3-base-arm64.tar.gz
        04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2 *ubuntu-base-24.04.4-base-arm64.tar.gz
        A91D5A93010193712D346D761372B7C9DB6DFCF093893161C64CA107F05914F2 *ubuntu-base-24.04.5-base-arm64.tar.gz
        deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef *ubuntu-base-24.04.5-base-amd64.tar.gz
    """.trimIndent()

    @Test
    fun parseUbuntuBaseSha256Sums_selectsLatestArm64ForTunaAndUstc() {
        val tuna = UbuntuRootfsSources.parseUbuntuBaseSha256Sums(sampleSums, UbuntuMirrorSource.TUNA)
        assertNotNull(tuna)
        assertEquals(
            "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/ubuntu-base/releases/noble/release/ubuntu-base-24.04.5-base-arm64.tar.gz",
            tuna!!.archiveUrl,
        )
        assertEquals("a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2", tuna.expectedSha256)
        assertFalse(tuna.isXz)
        assertEquals(0, tuna.stripComponents)

        val ustc = UbuntuRootfsSources.parseUbuntuBaseSha256Sums(sampleSums, UbuntuMirrorSource.USTC)
        assertNotNull(ustc)
        assertEquals(
            "https://mirrors.ustc.edu.cn/ubuntu-cdimage/ubuntu-base/releases/noble/release/ubuntu-base-24.04.5-base-arm64.tar.gz",
            ustc!!.archiveUrl,
        )
        assertEquals(tuna.expectedSha256, ustc.expectedSha256)
    }

    @Test
    fun parseUbuntuBaseSha256Sums_returnsNullForGithubOrInvalidInput() {
        assertNull(UbuntuRootfsSources.parseUbuntuBaseSha256Sums(sampleSums, UbuntuMirrorSource.GITHUB))
        assertNull(UbuntuRootfsSources.parseUbuntuBaseSha256Sums("no valid lines", UbuntuMirrorSource.TUNA))
    }

    @Test
    fun githubSpecAndAptMirrors_areConsistent() {
        val gh = UbuntuRootfsSources.githubSpec()
        assertTrue(gh.archiveUrl.startsWith("https://github.com/termux/proot-distro/"))
        assertTrue(gh.isXz)
        assertEquals(1, gh.stripComponents)
        assertEquals(64, gh.expectedSha256.length)

        assertTrue(UbuntuRootfsSources.aptPortsMirrorUrl(UbuntuMirrorSource.TUNA).contains("tuna.tsinghua.edu.cn"))
        assertTrue(UbuntuRootfsSources.aptPortsMirrorUrl(UbuntuMirrorSource.USTC).contains("ustc.edu.cn"))
        assertTrue(UbuntuRootfsSources.aptPortsMirrorUrl(UbuntuMirrorSource.GITHUB).contains("ports.ubuntu.com"))
    }

    @Test
    fun findById_returnsNullWhenUnset() {
        assertNull(UbuntuMirrorSource.findById(null))
        assertNull(UbuntuMirrorSource.findById(""))
        assertNull(UbuntuMirrorSource.findById("unknown"))
        assertEquals(UbuntuMirrorSource.USTC, UbuntuMirrorSource.findById("ustc"))
        assertEquals(UbuntuMirrorSource.GITHUB, UbuntuMirrorSource.findById("GITHUB"))
    }
}
