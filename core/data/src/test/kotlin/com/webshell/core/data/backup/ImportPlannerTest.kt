package com.webshell.core.data.backup

import com.webshell.core.data.WebAppEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPlannerTest {

    // ---------- 工具 ----------

    private fun entity(
        id: String,
        title: String = id,
        page: Int = 0,
        cell: Int = 0,
        folderId: String? = null,
        folderName: String? = null,
        createdAt: Long = 0L,
    ) = WebAppEntity(
        id = id,
        title = title,
        url = "https://existing.example/$id",
        iconUrl = null,
        desktopMode = false,
        darkMode = false,
        keepAlive = false,
        isFavorite = false,
        homePage = page,
        homeCellIndex = cell,
        folderId = folderId,
        createdAt = createdAt,
        folderName = folderName,
    )

    private fun app(
        title: String,
        url: String = "https://$title.example.com",
        homePage: Int? = null,
        homeCellIndex: Int? = null,
        folderKey: String? = null,
        folderCellIndex: Int? = null,
        isLocal: Boolean = false,
        sourceId: String? = null,
    ) = BackupApp(
        title = title,
        url = url,
        homePage = homePage,
        homeCellIndex = homeCellIndex,
        folderKey = folderKey,
        folderCellIndex = folderCellIndex,
        isLocal = isLocal,
        sourceId = sourceId,
    )

    private class Ids {
        private var appSeq = 0
        private var folderSeq = 0
        val nextAppId: () -> String = { "new-${++appSeq}" }
        val nextFolderId: () -> String = { "nf-${++folderSeq}" }
    }

    private fun plan(
        manifest: BackupManifest,
        existing: List<WebAppEntity> = emptyList(),
        pageCapacity: Int = 20,
        autoArrange: Boolean = false,
        runtimeRestoreSupported: Boolean = false,
        ids: Ids = Ids(),
    ): Either<ImportRejection, ImportPlan> = ImportPlanner.plan(
        manifest = manifest,
        existing = existing,
        pageCapacity = pageCapacity,
        autoArrange = autoArrange,
        runtimeRestoreSupported = runtimeRestoreSupported,
        idGenerator = ids.nextAppId,
        folderIdGenerator = ids.nextFolderId,
        now = { 1_000_000L },
    )

    private fun Either<ImportRejection, ImportPlan>.requirePlan(): ImportPlan =
        (this as Either.Right).value

    private fun Either<ImportRejection, ImportPlan>.requireRejection(): ImportRejection =
        (this as Either.Left).value

    private fun manifest(
        type: BackupType,
        apps: List<BackupApp>,
        folders: List<BackupFolder> = emptyList(),
        settings: Map<String, String>? = null,
        includesRuntimeData: Boolean = false,
        includesLocalApps: Boolean = false,
    ) = BackupManifest(
        type = type,
        exportedAt = 1L,
        folders = folders,
        apps = apps,
        settings = settings,
        includesRuntimeData = includesRuntimeData,
        includesLocalApps = includesLocalApps,
    )

    // ---------- URLS ----------

    @Test
    fun `urls append keeps file order and flattens folder members`() {
        val plan = plan(
            manifest(
                BackupType.URLS,
                apps = listOf(
                    app("b", folderKey = "f1", folderCellIndex = 1),
                    app("a"),
                    app("c", folderKey = "f1", folderCellIndex = 0),
                ),
            ),
        ).requirePlan()
        assertEquals(listOf("b", "a", "c"), plan.apps.map { it.entity.title })
        assertTrue(plan.apps.all { it.entity.folderId == null })
        assertEquals(0, plan.folderCount)
    }

    @Test
    fun `urls import keeps duplicate urls as separate apps`() {
        val plan = plan(
            manifest(
                BackupType.URLS,
                apps = listOf(
                    app("一", url = "https://dup.example.com"),
                    app("二", url = "https://dup.example.com"),
                ),
            ),
        ).requirePlan()
        assertEquals(2, plan.apps.size)
        assertEquals(listOf("一", "二"), plan.apps.map { it.entity.title })
    }

    @Test
    fun `rename numbering applies against existing and within batch`() {
        val plan = plan(
            manifest(BackupType.URLS, apps = listOf(app("A"), app("A"), app("A"))),
            existing = listOf(entity("e1", title = "A"), entity("e2", title = "A（1）")),
        ).requirePlan()
        assertEquals(listOf("A（2）", "A（3）", "A（4）"), plan.apps.map { it.entity.title })
    }

    @Test
    fun `auto arrange imports get dense reflow marker slots`() {
        val plan = plan(
            manifest(BackupType.URLS, apps = listOf(app("a"), app("b"))),
            autoArrange = true,
        ).requirePlan()
        assertTrue(plan.apps.all { it.entity.homePage == 0 && it.entity.homeCellIndex == -1 })
    }

    @Test
    fun `free placement appends sequentially across pages without colliding`() {
        // 容量 2，本机已占 (0,0) → 依次落 (0,1) (1,0) (1,1) (2,0)
        val plan = plan(
            manifest(BackupType.URLS, apps = listOf(app("a"), app("b"), app("c"), app("d"))),
            existing = listOf(entity("e1", page = 0, cell = 0)),
            pageCapacity = 2,
        ).requirePlan()
        val slots = plan.apps.map { it.entity.homePage to it.entity.homeCellIndex }
        assertEquals(listOf(0 to 1, 1 to 0, 1 to 1, 2 to 0), slots)
        assertTrue((0 to 0) !in slots)
    }

    @Test
    fun `invalid urls are skipped with warning and empty valid set rejects as corrupted`() {
        val mixed = plan(
            manifest(
                BackupType.URLS,
                apps = listOf(app("good", url = "https://ok.example.com"), app("bad", url = "ftp://nope")),
            ),
        ).requirePlan()
        assertEquals(listOf("good"), mixed.apps.map { it.entity.title })
        assertEquals(1, mixed.warnings.size)

        val allBad = plan(
            manifest(BackupType.URLS, apps = listOf(app("bad", url = "ftp://nope"))),
        )
        assertEquals(ImportRejection.Corrupted, allBad.requireRejection())
    }

    @Test
    fun `blank title falls back to url host`() {
        val plan = plan(
            manifest(BackupType.URLS, apps = listOf(app("", url = "https://news.example.com/path"))),
        ).requirePlan()
        assertEquals("news.example.com", plan.apps.single().entity.title)
    }

    // ---------- FOLDER ----------

    @Test
    fun `folder import recreates new folder with dense member order`() {
        val plan = plan(
            manifest(
                BackupType.FOLDER,
                folders = listOf(BackupFolder(key = "old-f", name = "工具")),
                apps = listOf(
                    app("null-order", folderKey = "old-f", folderCellIndex = null),
                    app("second", folderKey = "old-f", folderCellIndex = 2),
                    app("first", folderKey = "old-f", folderCellIndex = 0),
                ),
            ),
        ).requirePlan()
        assertEquals(1, plan.folderCount)
        assertEquals(listOf("first", "second", "null-order"), plan.apps.map { it.entity.title })
        assertEquals(listOf(0, 1, 2), plan.apps.map { it.entity.folderCellIndex })
        val folderIds = plan.apps.map { it.entity.folderId }.distinct()
        assertEquals(listOf("nf-1"), folderIds)
        assertNotEquals("old-f", folderIds.single())
        // 文件夹整体只占一格
        assertEquals(1, plan.apps.map { it.entity.homePage to it.entity.homeCellIndex }.distinct().size)
        assertTrue(plan.apps.all { it.entity.folderName == "工具" })
    }

    @Test
    fun `folder name clashes with existing folder get numbered in home scope`() {
        val plan = plan(
            manifest(
                BackupType.FOLDER,
                folders = listOf(BackupFolder(key = "old-f", name = "工具")),
                apps = listOf(app("x", folderKey = "old-f")),
            ),
            existing = listOf(entity("e1", folderId = "f-old", folderName = "工具")),
        ).requirePlan()
        assertTrue(plan.apps.all { it.entity.folderName == "工具（1）" })
    }

    @Test
    fun `member naming is folder scoped and independent from home scope`() {
        val plan = plan(
            manifest(
                BackupType.FOLDER,
                folders = listOf(BackupFolder(key = "old-f", name = "工具")),
                apps = listOf(
                    app("同名", folderKey = "old-f", folderCellIndex = 0),
                    app("同名", folderKey = "old-f", folderCellIndex = 1),
                ),
            ),
            // 主页上已有同名独立应用：文件夹成员不受影响
            existing = listOf(entity("e1", title = "同名")),
        ).requirePlan()
        assertEquals(listOf("同名", "同名（1）"), plan.apps.map { it.entity.title })
    }

    @Test
    fun `folder import requires exactly one folder`() {
        val zero = plan(manifest(BackupType.FOLDER, apps = listOf(app("a"))))
        assertEquals(ImportRejection.StructureInvalid, zero.requireRejection())
        val two = plan(
            manifest(
                BackupType.FOLDER,
                folders = listOf(BackupFolder("f1", "一"), BackupFolder("f2", "二")),
                apps = listOf(app("a")),
            ),
        )
        assertEquals(ImportRejection.StructureInvalid, two.requireRejection())
    }

    // ---------- FULL ----------

    @Test
    fun `full import keeps folders whole and appends standalone apps`() {
        val plan = plan(
            manifest(
                BackupType.FULL,
                folders = listOf(BackupFolder(key = "f1", name = "文件夹")),
                apps = listOf(
                    app("standalone"),
                    app("m2", folderKey = "f1", folderCellIndex = 1),
                    app("m1", folderKey = "f1", folderCellIndex = 0),
                ),
            ),
            pageCapacity = 20,
        ).requirePlan()
        assertEquals(1, plan.folderCount)
        val members = plan.apps.filter { it.entity.folderId != null }
        assertEquals(listOf("m1", "m2"), members.map { it.entity.title })
        assertEquals(listOf("nf-1"), members.map { it.entity.folderId }.distinct())
        assertTrue(members.all { it.entity.folderName == "文件夹" })
        // 文件夹在首个成员出现处占位：standalone(0,0)，文件夹(0,1)
        val standalone = plan.apps.single { it.entity.folderId == null }
        assertEquals(0 to 0, standalone.entity.homePage to standalone.entity.homeCellIndex)
        assertEquals(0 to 1, members.first().entity.homePage to members.first().entity.homeCellIndex)
    }

    @Test
    fun `full import sets runtime keys only when supported`() {
        val supported = plan(
            manifest(
                BackupType.FULL,
                apps = listOf(app("a", sourceId = "orig-a")),
                includesRuntimeData = true,
            ),
            runtimeRestoreSupported = true,
        ).requirePlan()
        assertEquals("orig-a", supported.apps.single().runtimeProfileKey)
        assertTrue(supported.warnings.isEmpty())

        val unsupported = plan(
            manifest(
                BackupType.FULL,
                apps = listOf(app("a", sourceId = "orig-a")),
                includesRuntimeData = true,
            ),
            runtimeRestoreSupported = false,
        ).requirePlan()
        assertNull(unsupported.apps.single().runtimeProfileKey)
        assertTrue(unsupported.warnings.any { it.contains("不支持独立网站数据") })
    }

    @Test
    fun `full import passes settings through as offered`() {
        val settings = mapOf("gridColumns" to "5", "themeMode" to "dark")
        val plan = plan(
            manifest(BackupType.FULL, apps = listOf(app("a")), settings = settings),
        ).requirePlan()
        assertEquals(settings, plan.settingsOffered)
    }

    @Test
    fun `local app without bundled files gets warning and no source key`() {
        val plan = plan(
            manifest(
                BackupType.URLS,
                apps = listOf(app("本地", url = "local://app-old/index.html", isLocal = true, sourceId = "app-old")),
            ),
        ).requirePlan()
        assertNull(plan.apps.single().localAppSourceKey)
        assertTrue(plan.warnings.any { it.contains("网页文件") })
    }

    // ---------- LAYOUT ----------

    private fun layoutManifest(
        apps: List<BackupApp>,
        folders: List<BackupFolder> = emptyList(),
    ) = manifest(BackupType.LAYOUT, apps = apps, folders = folders)

    @Test
    fun `layout restores exported positions with fresh ids onto empty home`() {
        val plan = plan(
            layoutManifest(
                folders = listOf(BackupFolder(key = "f1", name = "工具", homePage = 0, homeCellIndex = 4)),
                apps = listOf(
                    app("solo", homePage = 1, homeCellIndex = 2, sourceId = "orig-solo"),
                    app("member", homePage = 0, homeCellIndex = 4, folderKey = "f1", folderCellIndex = 0),
                ),
            ),
        ).requirePlan()
        val solo = plan.apps.single { it.entity.folderId == null }
        assertEquals(1 to 2, solo.entity.homePage to solo.entity.homeCellIndex)
        val member = plan.apps.single { it.entity.folderId == "nf-1" }
        assertEquals(0 to 4, member.entity.homePage to member.entity.homeCellIndex)
        assertEquals("工具", member.entity.folderName)
        assertEquals(1, plan.folderCount)
        // 新 id，不复用导出 id
        assertTrue(plan.apps.all { it.entity.id.startsWith("new-") })
        // LAYOUT 不改名
        assertEquals(listOf("solo", "member"), plan.apps.map { it.entity.title })
    }

    @Test
    fun `layout rejects slot conflicting with existing occupancy`() {
        val result = plan(
            layoutManifest(apps = listOf(app("a", homePage = 1, homeCellIndex = 4))),
            existing = listOf(entity("e1", page = 1, cell = 4)),
        )
        assertEquals(ImportRejection.PositionConflict("第2页第5格"), result.requireRejection())
    }

    @Test
    fun `layout rejects out of capacity slot as incompatible`() {
        val result = plan(
            layoutManifest(apps = listOf(app("a", homePage = 0, homeCellIndex = 2))),
            pageCapacity = 2,
        )
        assertEquals(ImportRejection.LayoutIncompatible, result.requireRejection())
    }

    @Test
    fun `layout rejects missing positions`() {
        val result = plan(layoutManifest(apps = listOf(app("a"))))
        assertEquals(ImportRejection.StructureInvalid, result.requireRejection())
    }

    @Test
    fun `layout rejects broken folder reference`() {
        val result = plan(
            layoutManifest(apps = listOf(app("a", homePage = 0, homeCellIndex = 0, folderKey = "ghost"))),
        )
        assertEquals(ImportRejection.StructureInvalid, result.requireRejection())
    }

    @Test
    fun `layout rejects member slot mismatching its folder`() {
        val result = plan(
            layoutManifest(
                folders = listOf(BackupFolder(key = "f1", name = "工具", homePage = 0, homeCellIndex = 1)),
                apps = listOf(app("a", homePage = 0, homeCellIndex = 2, folderKey = "f1")),
            ),
        )
        assertEquals(ImportRejection.StructureInvalid, result.requireRejection())
    }

    @Test
    fun `layout rejects internal slot collision`() {
        val result = plan(
            layoutManifest(
                apps = listOf(
                    app("a", homePage = 0, homeCellIndex = 0),
                    app("b", homePage = 0, homeCellIndex = 0),
                ),
            ),
        )
        assertEquals(ImportRejection.StructureInvalid, result.requireRejection())
    }

    @Test
    fun `layout restores local app source keys`() {
        val plan = plan(
            layoutManifest(
                apps = listOf(
                    app("本地", url = "local://app-old/index.html", isLocal = true, sourceId = "app-old", homePage = 0, homeCellIndex = 0),
                ),
                // LAYOUT 导出总是打包本地网页
            ).copy(includesLocalApps = true),
        ).requirePlan()
        assertEquals("app-old", plan.apps.single().localAppSourceKey)
        assertNull(plan.apps.single().runtimeProfileKey)
    }
}
