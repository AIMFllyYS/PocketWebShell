package com.webshell.core.data.backup

import com.webshell.core.data.HomeSlotAllocator
import com.webshell.core.data.WebAppEntity
import java.util.UUID

/** 导入被拒绝的原因（在触碰任何数据之前返回）。 */
sealed interface ImportRejection {
    /** 不是 zip 容器或缺少 backup.json 清单。 */
    data object NotBackupFile : ImportRejection

    /** 清单 version 高于本版本支持上限。 */
    data object UnsupportedVersion : ImportRejection

    /** 清单损坏，或全部条目均无效。 */
    data object Corrupted : ImportRejection

    /** LAYOUT：导出槽位超出目标设备容量（homeCellIndex >= 每页格数等）。 */
    data object LayoutIncompatible : ImportRejection

    /** LAYOUT：导出位置与本机已有占位冲突；detail 定位首个冲突格（如「第2页第5格」）。 */
    data class PositionConflict(val detail: String) : ImportRejection

    /** 结构非法：folderKey 引用缺失、LAYOUT 缺坐标、成员与文件夹槽位不一致、包内槽位互撞。 */
    data object StructureInvalid : ImportRejection
}

/**
 * 单个待导入应用的完整计划：entity 已是终态（新 id、新 folderId、最终落位），
 * 文件类资源以压缩包内路径形式随行，由执行层物化后回填 entity。
 */
data class PlannedApp(
    /** 新实体：id 为新生成；图标若为打包资源此处暂为 null，待物化后回填绝对路径。 */
    val entity: WebAppEntity,
    /** 打包图标的压缩包内路径（"icons/<file>"），物化到 filesDir/icons 后回填 entity.iconUrl；无则 null。 */
    val bundledIconArchivePath: String?,
    /** 运行数据归档键：压缩包 "profiles/<key>/" 应恢复到 entity.id 的 app_webview profile（仅 FULL）。 */
    val runtimeProfileKey: String?,
    /** 本地网页归档键：压缩包 "localapps/<key>/" 应恢复到 entity.id（仅 FULL/LAYOUT 的 isLocal 应用）。 */
    val localAppSourceKey: String?,
)

data class ImportPlan(
    val type: BackupType,
    val apps: List<PlannedApp>,
    val folderCount: Int,
    /** 用户可读的中文警告（跳过的条目、无法恢复的运行数据等）。 */
    val warnings: List<String>,
    /** FULL 且文件携带设置时非 null：设置快照，是否应用由 UI 另行询问用户。 */
    val settingsOffered: Map<String, String>?,
)

/**
 * 极简 Either：Left=拒绝原因，Right=导入计划。
 * 选它而非 kotlin.Result：Result 的 Failure 语义面向异常，而拒绝是合法业务结果，需要携带结构化原因。
 */
sealed interface Either<out L, out R> {
    data class Left<out L>(val value: L) : Either<L, Nothing>
    data class Right<out R>(val value: R) : Either<Nothing, R>
}

/**
 * 导入计划器：纯函数、无 Android 依赖，可在 JVM 单测中运行。
 * 追加类导入（URLS/FOLDER/FULL）改名去重并顺序分配槽位；LAYOUT 全有或全无地恢复原始坐标。
 */
object ImportPlanner {

    private const val DEFAULT_FOLDER_NAME = "未命名文件夹"
    private const val DEFAULT_APP_NAME = "未命名应用"
    private const val ICONS_PREFIX = "icons/"

    fun plan(
        manifest: BackupManifest,
        existing: List<WebAppEntity>,
        pageCapacity: Int,
        autoArrange: Boolean,
        runtimeRestoreSupported: Boolean,
        idGenerator: () -> String = { "app-" + UUID.randomUUID().toString().take(8) },
        folderIdGenerator: () -> String = { "f-" + UUID.randomUUID().toString().take(8) },
        now: () -> Long = { System.currentTimeMillis() },
    ): Either<ImportRejection, ImportPlan> {
        val ctx = PlanContext(
            manifest = manifest,
            existing = existing,
            capacity = pageCapacity.coerceAtLeast(1),
            autoArrange = autoArrange,
            runtimeRestoreSupported = runtimeRestoreSupported,
            idGenerator = idGenerator,
            folderIdGenerator = folderIdGenerator,
            now = now,
        )
        val entries = sanitize(ctx) ?: return Either.Left(ImportRejection.Corrupted)
        val plan = when (manifest.type) {
            BackupType.URLS -> planUrls(ctx, entries)
            BackupType.FOLDER -> planFolder(ctx, entries) ?: return Either.Left(ImportRejection.StructureInvalid)
            BackupType.FULL -> planFull(ctx, entries)
            BackupType.LAYOUT -> return planLayout(ctx, entries)
        }
        return Either.Right(plan)
    }

    // ---------- 公共清洗 ----------

    private class Entry(val app: BackupApp, val title: String)

    /** 过滤无效链接；返回 null 表示有效条目为零（整体按 Corrupted 拒绝）。 */
    private fun sanitize(ctx: PlanContext): List<Entry>? {
        val entries = mutableListOf<Entry>()
        for (app in ctx.manifest.apps) {
            val title = displayTitle(app)
            if (!isSupportedUrl(app.url)) {
                ctx.warnings += "已跳过无效条目「$title」：仅支持 http/https 链接"
                continue
            }
            entries += Entry(app, title)
        }
        return entries.ifEmpty { null }
    }

    private fun isSupportedUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://") || url.startsWith("local://")

    private fun displayTitle(app: BackupApp): String =
        app.title.ifBlank { hostOf(app.url) ?: DEFAULT_APP_NAME }

    private fun hostOf(url: String): String? =
        runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }

    /** 本地网页归档键：优先导出器写入的 sourceId，其次从 local://<appId>/ 形态的 url 解析。 */
    private fun localAppKey(app: BackupApp): String? {
        app.sourceId?.takeIf { it.isNotBlank() }?.let { return it }
        if (!app.url.startsWith("local://")) return null
        return app.url.removePrefix("local://").substringBefore('/').takeIf { it.isNotBlank() }
    }

    /** 成员相对顺序：folderCellIndex 升序、null 排最后，同序保持文件内先后。 */
    private fun memberOrder(entries: List<Entry>): List<Entry> =
        entries.sortedWith(compareBy({ it.app.folderCellIndex == null }, { it.app.folderCellIndex ?: 0 }))

    // ---------- 追加类（URLS / FOLDER / FULL） ----------

    /** URLS：全部摊平为独立应用，忽略 folderKey，按文件顺序追加。 */
    private fun planUrls(ctx: PlanContext, entries: List<Entry>): ImportPlan {
        val planned = entries.map { entry ->
            val title = ctx.homeNames.allocate(entry.title)
            plannedStandalone(ctx, entry, title)
        }
        return ImportPlan(BackupType.URLS, planned, folderCount = 0, warnings = ctx.warnings.toList(), settingsOffered = null)
    }

    /** FOLDER：恰好一个文件夹；整体作为新文件夹追加到主页末尾。 */
    private fun planFolder(ctx: PlanContext, entries: List<Entry>): ImportPlan? {
        val folder = ctx.manifest.folders.singleOrNull() ?: return null
        val planned = placeFolder(ctx, folder, memberOrder(entries))
        return ImportPlan(BackupType.FOLDER, planned, folderCount = 1, warnings = ctx.warnings.toList(), settingsOffered = null)
    }

    /** FULL：独立应用与文件夹（整体）均追加；文件夹在其首个成员出现处占位。 */
    private fun planFull(ctx: PlanContext, entries: List<Entry>): ImportPlan {
        val manifest = ctx.manifest
        if (manifest.includesRuntimeData && !ctx.runtimeRestoreSupported) {
            ctx.warnings += "此设备不支持独立网站数据，已跳过运行数据恢复"
        }
        val folderByKey = manifest.folders.associateBy { it.key }
        val membersByFolder = entries
            .filter { it.app.folderKey != null && it.app.folderKey in folderByKey }
            .groupBy { it.app.folderKey!! }
        val planned = mutableListOf<PlannedApp>()
        val placedFolders = mutableSetOf<String>()
        for (entry in entries) {
            val folderKey = entry.app.folderKey
            if (folderKey != null && folderKey in folderByKey) {
                if (!placedFolders.add(folderKey)) continue
                planned += placeFolder(ctx, folderByKey.getValue(folderKey), memberOrder(membersByFolder.getValue(folderKey)))
            } else {
                if (folderKey != null) {
                    ctx.warnings += "「${entry.title}」的文件夹信息缺失，已作为独立应用导入"
                }
                val title = ctx.homeNames.allocate(entry.title)
                planned += plannedStandalone(ctx, entry, title)
            }
        }
        return ImportPlan(
            type = BackupType.FULL,
            apps = planned,
            folderCount = placedFolders.size,
            warnings = ctx.warnings.toList(),
            settingsOffered = manifest.settings,
        )
    }

    /** 独立应用追加：分配主页作用域名字已完成，此处取下一个槽位并登记占用。 */
    private fun plannedStandalone(ctx: PlanContext, entry: Entry, title: String): PlannedApp {
        val (page, cell) = ctx.nextSlot()
        return buildPlanned(ctx, entry, title, page, cell, folderId = null, folderName = null, folderCellIndex = null)
            .also { ctx.markPlaced(listOf(it.entity)) }
    }

    /** 文件夹整体追加：新 folderId、文件夹名在主页作用域去重、成员名仅在文件夹作用域去重、槽位只占一格。 */
    private fun placeFolder(ctx: PlanContext, folder: BackupFolder, members: List<Entry>): List<PlannedApp> {
        val folderName = ctx.homeNames.allocate(folder.name.ifBlank { DEFAULT_FOLDER_NAME })
        val newFolderId = ctx.folderIdGenerator()
        val (page, cell) = ctx.nextSlot()
        val memberNames = NameAllocator()
        val planned = members.mapIndexed { index, member ->
            val title = memberNames.allocate(member.title)
            buildPlanned(ctx, member, title, page, cell, newFolderId, folderName, folderCellIndex = index)
        }
        ctx.markPlaced(planned.map { it.entity })
        return planned
    }

    // ---------- LAYOUT（坐标恢复，全有或全无） ----------

    private fun planLayout(ctx: PlanContext, entries: List<Entry>): Either<ImportRejection, ImportPlan> {
        val manifest = ctx.manifest

        // 结构：文件夹坐标齐全、key 唯一
        val folderByKey = LinkedHashMap<String, BackupFolder>()
        for (folder in manifest.folders) {
            if (folder.homePage == null || folder.homeCellIndex == null) {
                return Either.Left(ImportRejection.StructureInvalid)
            }
            if (folderByKey.put(folder.key, folder) != null) return Either.Left(ImportRejection.StructureInvalid)
        }
        // 结构：应用坐标齐全、folderKey 可解析、成员槽位与文件夹一致
        for (entry in entries) {
            val app = entry.app
            if (app.homePage == null || app.homeCellIndex == null) return Either.Left(ImportRejection.StructureInvalid)
            val folderKey = app.folderKey ?: continue
            val folder = folderByKey[folderKey] ?: return Either.Left(ImportRejection.StructureInvalid)
            if (folder.homePage != app.homePage || folder.homeCellIndex != app.homeCellIndex) {
                return Either.Left(ImportRejection.StructureInvalid)
            }
        }
        // 容量：任何导出槽位越界 → 布局不兼容
        val capacity = ctx.capacity
        val slotOutOfRange = entries.any { it.app.homePage!! < 0 || it.app.homeCellIndex!! !in 0 until capacity } ||
            manifest.folders.any { it.homePage!! < 0 || it.homeCellIndex!! !in 0 until capacity }
        if (slotOutOfRange) return Either.Left(ImportRejection.LayoutIncompatible)

        // 包内互撞：文件夹与独立应用各占一格
        val slotOwner = HashMap<Pair<Int, Int>, String>()
        for (folder in manifest.folders) {
            val slot = folder.homePage!! to folder.homeCellIndex!!
            if (slotOwner.putIfAbsent(slot, "folder:${folder.key}") != null) {
                return Either.Left(ImportRejection.StructureInvalid)
            }
        }
        for (entry in entries) {
            if (entry.app.folderKey != null) continue
            val slot = entry.app.homePage!! to entry.app.homeCellIndex!!
            if (slotOwner.putIfAbsent(slot, "app:${entry.title}") != null) {
                return Either.Left(ImportRejection.StructureInvalid)
            }
        }

        // 与本机占位冲突：仅统计本机坐标有效的应用
        val occupied = ctx.existing
            .filter { it.homePage >= 0 && it.homeCellIndex in 0 until capacity }
            .mapTo(HashSet()) { it.homePage to it.homeCellIndex }
        for (folder in manifest.folders) {
            val slot = folder.homePage!! to folder.homeCellIndex!!
            if (slot in occupied) return Either.Left(ImportRejection.PositionConflict(cellLabel(slot)))
        }
        for (entry in entries) {
            if (entry.app.folderKey != null) continue
            val slot = entry.app.homePage!! to entry.app.homeCellIndex!!
            if (slot in occupied) return Either.Left(ImportRejection.PositionConflict(cellLabel(slot)))
        }

        // 通过：恢复原始坐标与原始名字（不改名），id/folderId 全部换新
        val newFolderIds = manifest.folders.associate { it.key to ctx.folderIdGenerator() }
        val planned = entries.map { entry ->
            val folderKey = entry.app.folderKey
            val folder = folderKey?.let(folderByKey::get)
            buildPlanned(
                ctx = ctx,
                entry = entry,
                title = entry.title,
                page = entry.app.homePage!!,
                cell = entry.app.homeCellIndex!!,
                folderId = folderKey?.let(newFolderIds::get),
                folderName = folder?.name?.ifBlank { DEFAULT_FOLDER_NAME },
                folderCellIndex = entry.app.folderCellIndex,
            )
        }
        val folderCount = folderByKey.keys.count { key -> entries.any { it.app.folderKey == key } }
        return Either.Right(
            ImportPlan(BackupType.LAYOUT, planned, folderCount, warnings = ctx.warnings.toList(), settingsOffered = null),
        )
    }

    /** 用户可读的槽位定位：页号与格号均从 1 起。 */
    private fun cellLabel(slot: Pair<Int, Int>): String = "第${slot.first + 1}页第${slot.second + 1}格"

    // ---------- 实体构建 ----------

    private fun buildPlanned(
        ctx: PlanContext,
        entry: Entry,
        title: String,
        page: Int,
        cell: Int,
        folderId: String?,
        folderName: String?,
        folderCellIndex: Int?,
    ): PlannedApp {
        val app = entry.app
        val bundledIcon = app.iconUrl?.takeIf { it.startsWith(ICONS_PREFIX) }
        val remoteIcon = app.iconUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        val canCarryFiles = ctx.manifest.type == BackupType.FULL || ctx.manifest.type == BackupType.LAYOUT
        val localKey = if (canCarryFiles && app.isLocal) localAppKey(app) else null
        if (app.isLocal && (localKey == null || !ctx.manifest.includesLocalApps)) {
            ctx.warnings += "本地应用「$title」的网页文件未包含在备份中，导入后可能无法打开"
        }
        val runtimeKey = if (
            ctx.manifest.type == BackupType.FULL &&
            ctx.manifest.includesRuntimeData &&
            ctx.runtimeRestoreSupported
        ) app.sourceId else null
        val entity = WebAppEntity(
            id = ctx.idGenerator(),
            title = title,
            url = app.url,
            iconUrl = remoteIcon,
            desktopMode = app.desktopMode,
            darkMode = app.darkMode,
            keepAlive = app.keepAlive,
            isFavorite = app.isFavorite,
            homePage = page,
            homeCellIndex = cell,
            folderId = folderId,
            createdAt = ctx.nextCreatedAt(),
            isLocal = app.isLocal,
            externalLinksToBrowser = app.externalLinksToBrowser,
            textZoomPercent = app.textZoomPercent.coerceIn(80, 130),
            folderName = folderName,
            folderCellIndex = folderCellIndex,
            siteShellNewWindowPolicy = app.siteShellNewWindowPolicy,
            importSourceKey = app.importSourceKey,
        )
        return PlannedApp(
            entity = entity,
            bundledIconArchivePath = bundledIcon,
            runtimeProfileKey = runtimeKey,
            localAppSourceKey = localKey,
        )
    }

    /** 计划上下文：命名占用集、追加槽位分配与确定性的 createdAt 序列。 */
    private class PlanContext(
        val manifest: BackupManifest,
        val existing: List<WebAppEntity>,
        val capacity: Int,
        val autoArrange: Boolean,
        val runtimeRestoreSupported: Boolean,
        val idGenerator: () -> String,
        val folderIdGenerator: () -> String,
        val now: () -> Long,
    ) {
        val warnings = mutableListOf<String>()

        /** 主页作用域命名：已有应用名 + 已有文件夹名 + 本次已分配名。 */
        val homeNames = NameAllocator(
            existing.map { it.title } + existing.mapNotNull { it.folderName }.distinct(),
        )

        /** 追加槽位的占用视图：本机已有 + 本次已落位。 */
        private val placed = existing.toMutableList()

        private var order = 0L

        /** 同一批导入按文件顺序递增 createdAt，保证持久层按 createdAt 排序时还原导入顺序。 */
        fun nextCreatedAt(): Long = now() + order++

        /** 自动整理 → (0, -1) 交给密集重排；自由摆放 → 末页首个空槽。 */
        fun nextSlot(): Pair<Int, Int> =
            if (autoArrange) 0 to -1 else HomeSlotAllocator.appendSlot(placed, capacity)

        fun markPlaced(entities: List<WebAppEntity>) {
            placed.addAll(entities)
        }
    }
}
