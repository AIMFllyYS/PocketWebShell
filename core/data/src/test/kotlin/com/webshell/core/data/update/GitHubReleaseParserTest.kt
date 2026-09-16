package com.webshell.core.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseParserTest {
    @Test
    fun parsePicksOfficialApkAndIgnoresChecksum() {
        val release = GitHubReleaseParser.parse(
            """
            {
              "tag_name": "v0.1.52",
              "html_url": "https://github.com/AIMFllyYS/PocketWebShell/releases/tag/v0.1.52",
              "body": "# 玄览 0.1.52\n\n- 开屏定稿\n- 历史分组",
              "assets": [
                {
                  "name": "PocketWebShell-v0.1.52.apk.sha256",
                  "browser_download_url": "https://github.com/AIMFllyYS/PocketWebShell/releases/download/v0.1.52/PocketWebShell-v0.1.52.apk.sha256"
                },
                {
                  "name": "PocketWebShell-v0.1.52.apk",
                  "browser_download_url": "https://github.com/AIMFllyYS/PocketWebShell/releases/download/v0.1.52/PocketWebShell-v0.1.52.apk"
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals("0.1.52", release.versionName)
        assertEquals(
            "https://github.com/AIMFllyYS/PocketWebShell/releases/download/v0.1.52/PocketWebShell-v0.1.52.apk",
            release.apkUrl,
        )
        assertTrue(release.notesExcerpt.contains("开屏定稿"))
    }

    @Test
    fun skipsDebugApkAndFallsBackToReleasePage() {
        val release = GitHubReleaseParser.parse(
            """
            {
              "tag_name": "v0.1.10",
              "html_url": "https://github.com/AIMFllyYS/PocketWebShell/releases/tag/v0.1.10",
              "assets": [
                {
                  "name": "app-debug.apk",
                  "browser_download_url": "https://github.com/AIMFllyYS/PocketWebShell/releases/download/v0.1.10/app-debug.apk"
                }
              ]
            }
            """.trimIndent(),
        )
        assertNull(release.apkUrl)
        assertEquals(
            "https://github.com/AIMFllyYS/PocketWebShell/releases/tag/v0.1.10",
            release.downloadUrl,
        )
    }

    @Test
    fun rejectsNonGitHubDownloadHost() {
        assertNull(
            httpsGitHubUrl("https://evil.example/PocketWebShell.apk"),
        )
        assertNull(httpsGitHubUrl("http://github.com/AIMFllyYS/PocketWebShell"))
    }

    @Test
    fun newerRemoteIsAvailableOlderOrEqualIsUpToDate() {
        val release = GitHubRelease(
            versionName = "0.1.52",
            tagName = "v0.1.52",
            notesExcerpt = "notes",
            htmlUrl = "https://github.com/AIMFllyYS/PocketWebShell/releases/tag/v0.1.52",
            apkUrl = "https://github.com/AIMFllyYS/PocketWebShell/releases/download/v0.1.52/PocketWebShell-v0.1.52.apk",
        )
        val available = decideUpdate("0.1.40", release)
        assertTrue(available is AppUpdateResult.Available)
        assertTrue(decideUpdate("0.1.52", release) is AppUpdateResult.UpToDate)
        assertTrue(decideUpdate("0.1.53", release) is AppUpdateResult.UpToDate)
    }
}
