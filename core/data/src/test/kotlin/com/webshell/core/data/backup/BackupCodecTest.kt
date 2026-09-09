package com.webshell.core.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {

    private fun manifest(type: BackupType) = BackupManifest(
        type = type,
        exportedAt = 1735689600000L,
        gridColumns = 4,
        gridRows = 5,
        folders = listOf(BackupFolder(key = "f-abc", name = "工具", homePage = 0, homeCellIndex = 3)),
        apps = listOf(
            BackupApp(title = "示例", url = "https://example.com"),
            BackupApp(
                title = "本地",
                url = "local://app-x/index.html",
                iconUrl = "icons/app-x",
                isLocal = true,
                folderKey = "f-abc",
                folderCellIndex = 0,
                sourceId = "app-x",
            ),
        ),
        settings = mapOf("gridColumns" to "4"),
        includesRuntimeData = true,
        includesLocalApps = true,
    )

    @Test
    fun `round trip preserves all four types`() {
        for (type in BackupType.entries) {
            val original = manifest(type)
            assertEquals(original, BackupCodec.decode(BackupCodec.encode(original)))
        }
    }

    @Test
    fun `decode rejects wrong format string`() {
        val json = BackupCodec.encode(manifest(BackupType.FULL))
            .replace(BackupCodec.FORMAT, "something-else")
        val error = assertThrows(BackupFormatException::class.java) { BackupCodec.decode(json) }
        assertEquals(BackupFormatException.Reason.NOT_BACKUP, error.reason)
    }

    @Test
    fun `decode rejects future version`() {
        val json = BackupCodec.encode(manifest(BackupType.URLS).copy(version = BackupCodec.SUPPORTED_VERSION + 1))
        val error = assertThrows(BackupFormatException::class.java) { BackupCodec.decode(json) }
        assertEquals(BackupFormatException.Reason.UNSUPPORTED_VERSION, error.reason)
    }

    @Test
    fun `decode rejects malformed json`() {
        val error = assertThrows(BackupFormatException::class.java) { BackupCodec.decode("{ not json") }
        assertEquals(BackupFormatException.Reason.CORRUPTED, error.reason)
    }

    @Test
    fun `decode rejects json without type`() {
        val error = assertThrows(BackupFormatException::class.java) { BackupCodec.decode("""{"format":"pocketwebshell-backup"}""") }
        assertEquals(BackupFormatException.Reason.CORRUPTED, error.reason)
    }

    @Test
    fun `decode tolerates unknown fields for forward compatibility`() {
        val json = BackupCodec.encode(manifest(BackupType.URLS))
            .replace("\"apps\":", "\"futureField\":42,\"apps\":")
        assertEquals(manifest(BackupType.URLS), BackupCodec.decode(json))
    }
}
