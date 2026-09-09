package com.webshell.core.data.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 备份文件类型：URLS=仅链接清单；LAYOUT=含主页布局坐标；FULL=链接+文件夹+设置+运行数据；FOLDER=单个文件夹。 */
enum class BackupType { URLS, LAYOUT, FULL, FOLDER }

@Serializable
data class BackupApp(
    val title: String,
    val url: String,
    /** 远程 http(s) 图标地址，或压缩包内相对路径 "icons/<file>"（打包的本地图标），或 null。 */
    val iconUrl: String? = null,
    val desktopMode: Boolean = false,
    val darkMode: Boolean = false,
    val keepAlive: Boolean = false,
    val isFavorite: Boolean = false,
    val externalLinksToBrowser: Boolean = false,
    val textZoomPercent: Int = 100,
    val isLocal: Boolean = false,
    /** 仅 LAYOUT 导出携带：主页页号。 */
    val homePage: Int? = null,
    /** 仅 LAYOUT 导出携带：页内槽位。 */
    val homeCellIndex: Int? = null,
    /** 所属文件夹 key（对应 BackupFolder.key）；独立应用为 null。 */
    val folderKey: String? = null,
    /** 文件夹内相对顺序。 */
    val folderCellIndex: Int? = null,
    /** 原始应用映射键（localapps/ 与旧版 profiles/ 归档仍使用；共享 Profile 不新增此目录）。 */
    val sourceId: String? = null,
)

@Serializable
data class BackupFolder(
    val key: String,
    val name: String,
    /** 仅 LAYOUT 导出携带：主页页号。 */
    val homePage: Int? = null,
    /** 仅 LAYOUT 导出携带：页内槽位。 */
    val homeCellIndex: Int? = null,
)

@Serializable
data class BackupManifest(
    val format: String = BackupCodec.FORMAT,
    val type: BackupType,
    val version: Int = 1,
    val exportedAt: Long,
    val gridColumns: Int? = null,
    val gridRows: Int? = null,
    val folders: List<BackupFolder> = emptyList(),
    val apps: List<BackupApp> = emptyList(),
    /** 仅 FULL：设置快照（字符串编码的白名单子集）。 */
    val settings: Map<String, String>? = null,
    /** FULL：旧版兼容包是否含 profiles/ 运行数据。共享 Profile 运行数据另行迁移。 */
    val includesRuntimeData: Boolean = false,
    /** 压缩包内是否含 localapps/ 本地网页文件。 */
    val includesLocalApps: Boolean = false,
)

/** 备份格式错误。reason 区分「不是备份文件 / 版本过新 / 内容损坏」，供导入侧映射拒绝原因。 */
class BackupFormatException(
    message: String,
    val reason: Reason = Reason.CORRUPTED,
) : Exception(message) {
    enum class Reason { NOT_BACKUP, UNSUPPORTED_VERSION, CORRUPTED }
}

object BackupCodec {
    const val SUPPORTED_VERSION = 1
    const val MANIFEST_ENTRY = "backup.json"
    const val FILE_EXTENSION = "pws"
    const val FORMAT = "pocketwebshell-backup"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(manifest: BackupManifest): String = json.encodeToString(BackupManifest.serializer(), manifest)

    /** @throws BackupFormatException 格式串不符 / 版本过新 / JSON 损坏。 */
    fun decode(json: String): BackupManifest {
        val manifest = try {
            this.json.decodeFromString(BackupManifest.serializer(), json)
        } catch (e: SerializationException) {
            throw BackupFormatException("备份清单解析失败：${e.message}", BackupFormatException.Reason.CORRUPTED)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("备份清单解析失败：${e.message}", BackupFormatException.Reason.CORRUPTED)
        }
        if (manifest.format != FORMAT) {
            throw BackupFormatException("不是 PocketWebShell 备份文件", BackupFormatException.Reason.NOT_BACKUP)
        }
        if (manifest.version > SUPPORTED_VERSION) {
            throw BackupFormatException(
                "备份版本过新（v${manifest.version}），请升级应用",
                BackupFormatException.Reason.UNSUPPORTED_VERSION,
            )
        }
        return manifest
    }
}
