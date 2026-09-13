package com.webshell.core.data.backup

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import com.webshell.core.data.HomeSettings
import com.webshell.core.data.SCROLL_MODE_PAGER
import com.webshell.core.data.SCROLL_MODE_VERTICAL
import com.webshell.core.data.SettingsRepository
import com.webshell.core.data.THEME_MODE_DARK
import com.webshell.core.data.THEME_MODE_LIGHT
import com.webshell.core.data.THEME_MODE_PHOTO
import com.webshell.core.data.THEME_MODE_SYSTEM
import com.webshell.core.data.TRANSITION_FADE
import com.webshell.core.data.TRANSITION_NONE
import com.webshell.core.data.TRANSITION_SCALE
import com.webshell.core.data.TRANSITION_SLIDE
import com.webshell.core.data.WebAppDao
import com.webshell.core.data.WebAppEntity
import com.webshell.core.model.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** 导入终局：Success=已落库；Rejected=计划器拒绝（未触碰任何数据）；Failure=IO/意外失败。 */
sealed interface ImportOutcome {
    data class Success(
        val importedApps: Int,
        val folderCount: Int,
        val warnings: List<String>,
        val settingsOffered: Map<String, String>?,
    ) : ImportOutcome

    data class Rejected(val reason: ImportRejection) : ImportOutcome

    /** IO 或意外错误；导入是事务性的：落库全有或全无，文件物化失败时尽量回滚。 */
    data class Failure(val message: String) : ImportOutcome
}

/**
 * 备份导出/导入核心。容器为 zip（自定义扩展名 .pws），清单为 backup.json，
 * 业务校验全部委托给纯函数的 [ImportPlanner]。
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webAppDao: WebAppDao,
    private val settingsRepository: SettingsRepository,
) {

    // ---------- 导出 ----------

    /** @param folderId FOLDER 类型必填；导出文件位于 cacheDir/backup/。 */
    suspend fun export(type: BackupType, folderId: String? = null): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            AppLog.log(TAG, "开始导出：$type")
            if (type == BackupType.FULL) flushCookies()
            val apps = webAppDao.observeAll().first()
            val settings = settingsRepository.settings.first()
            val bundle = ExportBuilder(type, apps, settings, folderId).build()
            val file = writeArchive(type, bundle)
            AppLog.log(TAG, "导出完成：${file.name}")
            file
        }.onFailure { AppLog.error(TAG, "导出失败（$type）：${it.message}") }
    }

    /** FULL 导出前把内存中的 Cookie 落盘；必须在主线程调用，失败仅降级不阻断。 */
    private suspend fun flushCookies() {
        runCatching {
            withContext(Dispatchers.Main) { CookieManager.getInstance().flush() }
        }.onFailure { AppLog.warn(TAG, "Cookie flush 不可用，运行数据可能不是最新") }
    }

    private inner class ExportBuilder(
        private val type: BackupType,
        private val apps: List<WebAppEntity>,
        private val settings: HomeSettings,
        private val folderId: String?,
    ) {
        private val iconSources = linkedMapOf<String, File>()
        private val localAppSources = linkedMapOf<String, File>()
        private val profileSources = linkedMapOf<String, File>()

        fun build(): ExportBundle {
            val exportedAt = System.currentTimeMillis()
            val manifest = when (type) {
                BackupType.URLS -> BackupManifest(
                    type = type,
                    exportedAt = exportedAt,
                    apps = apps.map { toBackupApp(it, includePosition = false, includeFolder = false) },
                )

                BackupType.FOLDER -> {
                    requireNotNull(folderId) { "FOLDER 导出必须指定 folderId" }
                    val members = apps.filter { it.folderId == folderId }
                    require(members.isNotEmpty()) { "目标文件夹不存在或为空" }
                    val name = members.firstNotNullOfOrNull { it.folderName } ?: DEFAULT_FOLDER_NAME
                    BackupManifest(
                        type = type,
                        exportedAt = exportedAt,
                        folders = listOf(BackupFolder(key = folderId, name = name)),
                        apps = memberOrder(members).mapIndexed { index, entity ->
                            toBackupApp(entity, includePosition = false, includeFolder = true)
                                .copy(folderCellIndex = index)
                        },
                    )
                }

                BackupType.LAYOUT -> BackupManifest(
                    type = type,
                    exportedAt = exportedAt,
                    gridColumns = settings.gridColumns,
                    gridRows = settings.gridRows,
                    folders = buildFolders(includePosition = true),
                    apps = apps.map { toBackupApp(it, includePosition = true, includeFolder = true) },
                )

                BackupType.FULL -> BackupManifest(
                    type = type,
                    exportedAt = exportedAt,
                    folders = buildFolders(includePosition = false),
                    apps = apps.map { toBackupApp(it, includePosition = false, includeFolder = true) },
                    settings = settingsSnapshot(settings),
                    includesRuntimeData = true,
                )
            }
            if (type == BackupType.FULL) {
                for (app in apps) {
                    val dir = File(context.dataDir, "app_webview/profiles/${app.id}")
                    if (dir.isDirectory) profileSources[app.id] = dir
                }
            }
            return ExportBundle(
                manifest = manifest.copy(includesLocalApps = localAppSources.isNotEmpty()),
                iconSources = iconSources,
                localAppSources = localAppSources,
                profileSources = profileSources,
            )
        }

        private fun toBackupApp(entity: WebAppEntity, includePosition: Boolean, includeFolder: Boolean): BackupApp {
            val iconEntry = bundleLocalIcon(entity)
            if (includeLocalAppFiles() && entity.isLocal) {
                localAppDir(entity.id).takeIf { it.isDirectory }?.let { localAppSources[entity.id] = it }
            }
            return BackupApp(
                title = entity.title,
                url = entity.url,
                iconUrl = iconEntry ?: entity.iconUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") },
                desktopMode = entity.desktopMode,
                darkMode = entity.darkMode,
                keepAlive = entity.keepAlive,
                isFavorite = entity.isFavorite,
                externalLinksToBrowser = entity.externalLinksToBrowser,
                textZoomPercent = entity.textZoomPercent,
                isLocal = entity.isLocal,
                homePage = if (includePosition) entity.homePage else null,
                homeCellIndex = if (includePosition) entity.homeCellIndex else null,
                folderKey = if (includeFolder) entity.folderId else null,
                folderCellIndex = if (includeFolder) entity.folderCellIndex else null,
                sourceId = entity.id,
                siteShellNewWindowPolicy = entity.siteShellNewWindowPolicy,
                importSourceKey = entity.importSourceKey,
            )
        }

        /** 本地图标打包为 icons/<appId>，返回归档路径；文件缺失时降级为无图标并记日志。 */
        private fun bundleLocalIcon(entity: WebAppEntity): String? {
            val path = entity.iconUrl ?: return null
            if (!path.startsWith("/")) return null
            val source = File(path)
            if (!source.isFile) {
                AppLog.warn(TAG, "本地图标缺失，未打包：${entity.title}")
                return null
            }
            val entryPath = "icons/${entity.id}"
            iconSources[entryPath] = source
            return entryPath
        }

        /** 本地网页文件仅 LAYOUT/FULL 随包携带。 */
        private fun includeLocalAppFiles(): Boolean =
            type == BackupType.LAYOUT || type == BackupType.FULL

        private fun buildFolders(includePosition: Boolean): List<BackupFolder> =
            apps.filter { it.folderId != null }
                .groupBy { it.folderId!! }
                .entries
                .sortedBy { (_, members) -> members.minOf { it.createdAt } }
                .map { (key, members) ->
                    val anchor = members.first()
                    BackupFolder(
                        key = key,
                        name = members.firstNotNullOfOrNull { it.folderName } ?: DEFAULT_FOLDER_NAME,
                        homePage = if (includePosition) anchor.homePage else null,
                        homeCellIndex = if (includePosition) anchor.homeCellIndex else null,
                    )
                }

        private fun memberOrder(members: List<WebAppEntity>): List<WebAppEntity> =
            members.sortedWith(compareBy({ it.folderCellIndex == null }, { it.folderCellIndex ?: 0 }, { it.createdAt }))
    }

    private class ExportBundle(
        val manifest: BackupManifest,
        val iconSources: Map<String, File>,
        val localAppSources: Map<String, File>,
        val profileSources: Map<String, File>,
    )

    private fun writeArchive(type: BackupType, bundle: ExportBundle): File {
        val directory = File(context.cacheDir, "backup").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(bundle.manifest.exportedAt))
        val file = File(directory, "PocketWebShell-${type.name.lowercase(Locale.US)}-$stamp.${BackupCodec.FILE_EXTENSION}")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupCodec.MANIFEST_ENTRY))
            zip.write(BackupCodec.encode(bundle.manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((entryPath, source) in bundle.iconSources) addFileEntry(zip, entryPath, source)
            for ((appId, dir) in bundle.localAppSources) addTreeEntries(zip, "localapps/$appId", dir)
            for ((appId, dir) in bundle.profileSources) addTreeEntries(zip, "profiles/$appId", dir)
        }
        return file
    }

    private fun addFileEntry(zip: ZipOutputStream, entryPath: String, source: File) {
        runCatching {
            zip.putNextEntry(ZipEntry(entryPath))
            source.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }.onFailure { AppLog.warn(TAG, "打包跳过不可读文件：$entryPath") }
    }

    private fun addTreeEntries(zip: ZipOutputStream, archivePrefix: String, dir: File) {
        val rootPath = dir.absolutePath
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.absolutePath.removePrefix(rootPath).trimStart(File.separatorChar).replace(File.separatorChar, '/')
            addFileEntry(zip, "$archivePrefix/$relative", file)
        }
    }

    // ---------- 导入 ----------

    /** 读取清单用于导入前预览；不校验 zip 其余内容。 */
    suspend fun readManifest(uri: Uri): Result<BackupManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val tempCopy = copyToTemp(uri)
            try {
                if (!isZipArchive(tempCopy)) {
                    throw BackupFormatException("不是 PocketWebShell 备份文件", BackupFormatException.Reason.NOT_BACKUP)
                }
                readManifestEntry(tempCopy)
            } finally {
                tempCopy.delete()
            }
        }
    }

    /**
     * 事务性导入：计划器拒绝则原样返回；先物化全部文件，任何致命失败在任何数据库写入之前返回，
     * 最后一次性 upsertAll。
     */
    suspend fun import(uri: Uri, runtimeRestoreSupported: Boolean): ImportOutcome = withContext(Dispatchers.IO) {
        try {
            val tempCopy = copyToTemp(uri)
            try {
                if (!isZipArchive(tempCopy)) return@withContext ImportOutcome.Rejected(ImportRejection.NotBackupFile)
                val manifest = try {
                    readManifestEntry(tempCopy)
                } catch (e: BackupFormatException) {
                    return@withContext ImportOutcome.Rejected(
                        when (e.reason) {
                            BackupFormatException.Reason.NOT_BACKUP -> ImportRejection.NotBackupFile
                            BackupFormatException.Reason.UNSUPPORTED_VERSION -> ImportRejection.UnsupportedVersion
                            BackupFormatException.Reason.CORRUPTED -> ImportRejection.Corrupted
                        },
                    )
                }
                val existing = webAppDao.observeAll().first()
                val settings = settingsRepository.settings.first()
                val capacity = (settings.gridColumns * settings.gridRows).coerceAtLeast(1)
                val plan = when (
                    val result = ImportPlanner.plan(
                        manifest = manifest,
                        existing = existing,
                        pageCapacity = capacity,
                        autoArrange = settings.autoArrangeHome,
                        runtimeRestoreSupported = runtimeRestoreSupported,
                    )
                ) {
                    is Either.Left -> return@withContext ImportOutcome.Rejected(result.value)
                    is Either.Right -> result.value
                }
                executePlan(tempCopy, plan)
            } finally {
                tempCopy.delete()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            AppLog.error(TAG, "导入失败：${e.message}")
            ImportOutcome.Failure(e.message ?: "导入失败")
        }
    }

    private fun copyToTemp(uri: Uri): File {
        val temp = File.createTempFile("pws-import-", ".zip", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("无法读取所选文件")
            input.use { temp.outputStream().use { output -> it.copyTo(output) } }
            return temp
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    private fun isZipArchive(file: File): Boolean {
        if (file.length() < 4) return false
        val header = ByteArray(4)
        file.inputStream().use { it.read(header) }
        return header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() &&
            (header[2] == 3.toByte() || header[2] == 5.toByte() || header[2] == 7.toByte())
    }

    private fun readManifestEntry(zipFile: File): BackupManifest {
        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry(BackupCodec.MANIFEST_ENTRY)
                ?: throw BackupFormatException("不是 PocketWebShell 备份文件", BackupFormatException.Reason.NOT_BACKUP)
            val json = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
            return BackupCodec.decode(json)
        }
    }

    /** 执行已批准的计划：staging 物化 → 移动到最终位置 → 单次 upsertAll；任何一步失败都在落库前返回。 */
    private suspend fun executePlan(zipSource: File, plan: ImportPlan): ImportOutcome {
        val warnings = plan.warnings.toMutableList()
        val stagingRoot = File(context.cacheDir, "backup-import-${UUID.randomUUID()}")
        // 第一阶段：全部物化到暂存区，任何致命失败都在数据库写入之前
        val staged: List<StagedApp> = try {
            ZipFile(zipSource).use { zip ->
                plan.apps.map { planned -> stageFiles(zip, stagingRoot, planned, warnings) }
            }
        } catch (e: Exception) {
            stagingRoot.deleteRecursively()
            AppLog.error(TAG, "导入物化失败：${e.message}")
            return ImportOutcome.Failure("备份内容读取失败：${e.message}")
        }
        // 第二阶段：移动到最终位置；movedOut 记录已落位文件供回滚
        val movedOut = mutableListOf<File>()
        val finalEntities: List<WebAppEntity> = try {
            staged.map { materialize(it, movedOut, warnings) }
        } catch (e: Exception) {
            movedOut.forEach { it.deleteRecursively() }
            stagingRoot.deleteRecursively()
            AppLog.error(TAG, "导入文件落位失败：${e.message}")
            return ImportOutcome.Failure("备份文件写入失败：${e.message}")
        }
        // 第三阶段：单次全量落库；失败则尽力删除已落位文件
        return try {
            webAppDao.upsertAll(finalEntities)
            stagingRoot.deleteRecursively()
            AppLog.log(TAG, "导入完成：${plan.apps.size} 个应用、${plan.folderCount} 个文件夹（${plan.type}）")
            ImportOutcome.Success(
                importedApps = plan.apps.size,
                folderCount = plan.folderCount,
                warnings = warnings,
                settingsOffered = plan.settingsOffered,
            )
        } catch (e: Exception) {
            movedOut.forEach { it.deleteRecursively() }
            stagingRoot.deleteRecursively()
            AppLog.error(TAG, "导入落库失败：${e.message}")
            ImportOutcome.Failure("写入数据库失败：${e.message}")
        }
    }

    /** 单个应用的暂存结果。 */
    private class StagedApp(
        val planned: PlannedApp,
        val stagedIcon: File?,
        val stagedLocalApp: File?,
        val stagedProfile: File?,
    )

    /**
     * 物化单个应用的随包文件到暂存区；条目缺失只降级（警告），IO 异常上抛（整体失败）。
     */
    private fun stageFiles(
        zip: ZipFile,
        stagingRoot: File,
        planned: PlannedApp,
        warnings: MutableList<String>,
    ): StagedApp {
        val stagedIcon = planned.bundledIconArchivePath?.let { archivePath ->
            val entry = zip.getEntry(archivePath)
            if (entry == null) {
                warnings += "「${planned.entity.title}」的图标未包含在备份中"
                null
            } else {
                val target = File(stagingRoot, "icons/icon_${UUID.randomUUID()}.img")
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                target
            }
        }
        val stagedLocalApp = planned.localAppSourceKey?.let { key ->
            val target = File(stagingRoot, "localapps/${planned.entity.id}")
            // 计划器已就缺失的本地网页文件给出警告
            if (extractTree(zip, "localapps/$key/", target)) target else null
        }
        val stagedProfile = planned.runtimeProfileKey?.let { key ->
            val target = File(stagingRoot, "profiles/${planned.entity.id}")
            if (extractTree(zip, "profiles/$key/", target)) {
                target
            } else {
                warnings += "「${planned.entity.title}」的运行数据未包含在备份中"
                null
            }
        }
        return StagedApp(planned, stagedIcon, stagedLocalApp, stagedProfile)
    }

    /** 把暂存文件移动到最终位置并回填 entity；profile 目标已存在时跳过覆盖（警告）。 */
    private fun materialize(
        staged: StagedApp,
        movedOut: MutableList<File>,
        warnings: MutableList<String>,
    ): WebAppEntity {
        var entity = staged.planned.entity
        staged.stagedIcon?.let { source ->
            val iconDir = File(context.filesDir, "icons").apply { mkdirs() }
            val target = File(iconDir, source.name)
            moveTree(source, target)
            movedOut += target
            entity = entity.copy(iconUrl = target.absolutePath)
        }
        staged.stagedLocalApp?.let { source ->
            val target = File(context.filesDir, "localapps/${entity.id}")
            moveTree(source, target)
            movedOut += target
            staged.planned.localAppSourceKey?.let { key ->
                entity = entity.copy(url = entity.url.replaceFirst("local://$key", "local://${entity.id}"))
            }
        }
        staged.stagedProfile?.let { source ->
            val target = File(context.dataDir, "app_webview/profiles/${entity.id}")
            if (target.exists()) {
                warnings += "「${entity.title}」已存在运行数据，跳过覆盖"
            } else {
                target.parentFile?.mkdirs()
                moveTree(source, target)
                movedOut += target
            }
        }
        return entity
    }

    /** 同卷优先 rename，跨卷回退 copy+delete。 */
    private fun moveTree(source: File, target: File) {
        if (source.renameTo(target)) return
        if (!source.copyRecursively(target, overwrite = false)) throw IOException("无法写入 ${target.name}")
        source.deleteRecursively()
    }

    /** 解压 zip 中 prefix 下的全部文件到 target；prefix 无任何条目时返回 false。 */
    private fun extractTree(zip: ZipFile, prefix: String, target: File): Boolean {
        val entries = zip.entries().toList().filter { !it.isDirectory && it.name.startsWith(prefix) }
        if (entries.isEmpty()) return false
        for (entry in entries) {
            val relative = entry.name.removePrefix(prefix)
            val out = File(target, relative)
            out.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
        }
        return true
    }

    // ---------- 设置快照 ----------

    /** 仅应用白名单内的类型化设置；未知键与非法值静默忽略。 */
    suspend fun applyImportedSettings(settings: Map<String, String>) {
        settings["gridColumns"]?.toIntOrNull()?.let { settingsRepository.setGridColumns(it) }
        settings["gridRows"]?.toIntOrNull()?.let { settingsRepository.setGridRows(it) }
        settings["iconCornerRadiusPercent"]?.toIntOrNull()?.let { settingsRepository.setIconCornerRadiusPercent(it) }
        settings["iconSizeDp"]?.toIntOrNull()?.let { settingsRepository.setIconSizeDp(it) }
        settings["showLabels"]?.toBooleanStrictOrNull()?.let { settingsRepository.setShowLabels(it) }
        settings["showPageIndicator"]?.toBooleanStrictOrNull()?.let { settingsRepository.setShowPageIndicator(it) }
        settings["themeMode"]
            ?.takeIf { it in setOf(THEME_MODE_SYSTEM, THEME_MODE_LIGHT, THEME_MODE_DARK, THEME_MODE_PHOTO) }
            ?.let { settingsRepository.setThemeMode(it) }
        settings["transitionStyle"]
            ?.takeIf { it in setOf(TRANSITION_SLIDE, TRANSITION_FADE, TRANSITION_SCALE, TRANSITION_NONE) }
            ?.let { settingsRepository.setTransitionStyle(it) }
        settings["autoArrangeHome"]?.toBooleanStrictOrNull()?.let { settingsRepository.setAutoArrangeHome(it) }
        settings["homeScrollMode"]
            ?.takeIf { it in setOf(SCROLL_MODE_PAGER, SCROLL_MODE_VERTICAL) }
            ?.let { settingsRepository.setHomeScrollMode(it) }
        settings["browserAutoCollapse"]?.toBooleanStrictOrNull()?.let { settingsRepository.setBrowserAutoCollapse(it) }
        settings["keepAliveServiceEnabled"]?.toBooleanStrictOrNull()?.let { settingsRepository.setKeepAliveServiceEnabled(it) }
        settings["pullToRefreshEnabled"]?.toBooleanStrictOrNull()?.let { settingsRepository.setPullToRefreshEnabled(it) }
        settings["forceEnableZoomEnabled"]?.toBooleanStrictOrNull()?.let { settingsRepository.setForceEnableZoomEnabled(it) }
        settings["siteShellOrbEnabled"]?.toBooleanStrictOrNull()?.let { settingsRepository.setSiteShellOrbEnabled(it) }
        val orbX = settings["siteShellOrbX"]?.toFloatOrNull()
        val orbY = settings["siteShellOrbY"]?.toFloatOrNull()
        if (orbX != null && orbY != null) settingsRepository.setSiteShellOrbPosition(orbX, orbY)
        settings["siteShellOrbParked"]?.toBooleanStrictOrNull()?.let { settingsRepository.setSiteShellOrbParked(it) }
        settings["downloadCapsuleEnabled"]?.toBooleanStrictOrNull()?.let { settingsRepository.setDownloadCapsuleEnabled(it) }
        if (settings.containsKey("appFontFamily") || settings.containsKey("appFontScalePercent")) {
            val current = settingsRepository.settings.first()
            settingsRepository.setAppTypography(
                fontFamily = settings["appFontFamily"] ?: current.appFontFamily,
                scalePercent = settings["appFontScalePercent"]?.toIntOrNull() ?: current.appFontScalePercent,
            )
        }
        AppLog.log(TAG, "已应用导入的设置（${settings.size} 项）")
    }

    private fun settingsSnapshot(s: HomeSettings): Map<String, String> = mapOf(
        "gridColumns" to s.gridColumns.toString(),
        "gridRows" to s.gridRows.toString(),
        "iconCornerRadiusPercent" to s.iconCornerRadiusPercent.toString(),
        "iconSizeDp" to s.iconSizeDp.toString(),
        "showLabels" to s.showLabels.toString(),
        "showPageIndicator" to s.showPageIndicator.toString(),
        "themeMode" to s.themeMode,
        "transitionStyle" to s.transitionStyle,
        "autoArrangeHome" to s.autoArrangeHome.toString(),
        "homeScrollMode" to s.homeScrollMode,
        "appFontFamily" to s.appFontFamily,
        "appFontScalePercent" to s.appFontScalePercent.toString(),
        "browserAutoCollapse" to s.browserAutoCollapse.toString(),
        "keepAliveServiceEnabled" to s.keepAliveServiceEnabled.toString(),
        "pullToRefreshEnabled" to s.pullToRefreshEnabled.toString(),
        "forceEnableZoomEnabled" to s.forceEnableZoomEnabled.toString(),
        "siteShellOrbEnabled" to s.siteShellOrbEnabled.toString(),
        "siteShellOrbX" to s.siteShellOrbX.toString(),
        "siteShellOrbY" to s.siteShellOrbY.toString(),
        "siteShellOrbParked" to s.siteShellOrbParked.toString(),
        "downloadCapsuleEnabled" to s.downloadCapsuleEnabled.toString(),
    )

    private fun localAppDir(appId: String): File = File(context.filesDir, "localapps/$appId")

    private companion object {
        const val TAG = "backup"
        const val DEFAULT_FOLDER_NAME = "未命名文件夹"
    }
}
