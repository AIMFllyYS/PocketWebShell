package com.webshell.core.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.webshell.core.model.AppLog
import com.webshell.core.model.DownloadItem
import com.webshell.core.model.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.downloadStore by preferencesDataStore(name = "webshell_downloads")

/**
 * Owns DownloadManager enqueue, public-Downloads destination, progress, and
 * process-restart reconciliation. Does not store a full URL, query string, or Cookie.
 */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val blobIds = AtomicLong(-1L)
    private val lock = Any()
    private val surfaced = mutableSetOf<Long>()
    private val dismissed = mutableSetOf<Long>()

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private val _visibleItems = MutableStateFlow<List<DownloadItem>>(emptyList())
    val visibleItems: StateFlow<List<DownloadItem>> = _visibleItems.asStateFlow()

    private var pollJob: Job? = null
    private var completeReceiver: BroadcastReceiver? = null

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        registerCompleteReceiver()
        scope.launch {
            reconcile()
            ensurePolling()
        }
    }

    private fun registerCompleteReceiver() {
        if (completeReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                onSystemComplete(id)
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            completeReceiver = receiver
        }
    }

    fun startHttp(
        url: String,
        fileName: String,
        mimeType: String?,
        userAgent: String?,
        referer: String?,
        cookie: String?,
    ): Long? {
        val name = fileName.ifBlank { "download" }
        val host = logHost(url)
        if (!isEnqueueableHttp(url)) {
            AppLog.warn(TAG, "拒绝下载 host=$host name=$name")
            return null
        }
        val dm = downloadManager() ?: run {
            AppLog.warn(TAG, "DownloadManager 不可用 host=$host name=$name")
            return null
        }
        return runCatching {
            val request = DownloadManager.Request(url.toUri())
                .setMimeType(mimeType)
                .setTitle(name)
                .setDescription(context.getString(R.string.download_notification_description))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            requestHeaders(url, userAgent, referer, cookie).forEach { (header, value) ->
                request.addRequestHeader(header, value)
            }
            val mediaUri = attachDestination(request, name, mimeType)
            val id = dm.enqueue(request)
            AppLog.log(TAG, "enqueue host=$host name=$name id=$id")
            upsert(
                DownloadItem(
                    id = id,
                    displayName = name,
                    status = DownloadStatus.Queued,
                    documentUri = mediaUri?.toString(),
                ),
                surface = true,
            )
            id
        }.onFailure {
            AppLog.warn(TAG, "enqueue 失败 host=$host name=$name")
        }.getOrNull()
    }

    fun completeBlob(fileName: String, uri: Uri) {
        val name = fileName.ifBlank { "download" }
        val id = nextBlobId()
        AppLog.log(TAG, "blob 完成 name=$name")
        upsert(
            DownloadItem(id = id, displayName = name, status = DownloadStatus.Running, documentUri = uri.toString()),
            surface = true,
        )
        scope.launch {
            val publicUri = copyToPublicDownloads(uri, name)
            upsert(
                DownloadItem(
                    id = id,
                    displayName = name,
                    status = DownloadStatus.Success,
                    documentUri = (publicUri ?: uri).toString(),
                ),
                surface = true,
            )
        }
    }

    fun onSystemComplete(id: Long) {
        if (id < 0L) return
        val updated = queryOne(id)
        if (updated != null) {
            AppLog.log(TAG, "完成 name=${updated.displayName} id=$id")
            updated.documentUri?.toUriOrNull()?.let { clearPending(it) }
            upsert(updated, surface = true)
        } else {
            val existing = synchronized(lock) { _items.value.find { it.id == id } }
            if (existing != null) {
                AppLog.warn(TAG, "失败 name=${existing.displayName} id=$id")
                upsert(existing.copy(status = DownloadStatus.Failed), surface = true)
            }
        }
    }

    fun item(id: Long): DownloadItem? = synchronized(lock) { _items.value.find { it.id == id } }

    fun downloadedFileUri(id: Long): Uri? {
        if (id <= 0L) return item(id)?.documentUri?.toUriOrNull()
        return runCatching { downloadManager()?.getUriForDownloadedFile(id) }.getOrNull()
            ?: item(id)?.documentUri?.toUriOrNull()
    }

    fun dismiss(id: Long) {
        synchronized(lock) {
            dismissed.add(id)
            surfaced.remove(id)
        }
        republish()
    }

    fun remove(id: Long) {
        val existing = item(id)
        if (id > 0L) runCatching { downloadManager()?.remove(id) }
        existing?.documentUri?.toUriOrNull()?.let { uri ->
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        synchronized(lock) {
            _items.value = _items.value.filterNot { it.id == id }
            surfaced.remove(id)
            dismissed.add(id)
        }
        AppLog.log(TAG, "删除记录 name=${existing?.displayName ?: id}")
        republish()
        persistAsync()
    }

    private suspend fun reconcile() {
        val persisted = runCatching {
            val raw = context.downloadStore.data.first()[Keys.RECORDS].orEmpty()
            if (raw.isBlank()) emptyList() else json.decodeFromString<List<PersistedDownload>>(raw)
        }.getOrElse { emptyList() }
        val memory = synchronized(lock) { _items.value.associateBy { it.id } }
        val loaded = persisted.map { record ->
            memory[record.id] ?: hydrate(record)
        }
        val loadedIds = loaded.map { it.id }.toSet()
        val extras = memory.values.filter { it.id !in loadedIds }
        val merged = (loaded + extras).takeLast(MAX_RECORDS)
        val minBlob = merged.minOfOrNull { it.id } ?: -1L
        if (minBlob < 0L) blobIds.updateAndGet { current -> minOf(current, minBlob - 1L) }
        synchronized(lock) {
            _items.value = merged
            surfaced.clear()
            val previous = persisted.associateBy { it.id }
            merged.forEach { item ->
                val was = previous[item.id]?.status
                val becameTerminal = !item.inProgress &&
                    (was == DownloadStatus.Queued.name || was == DownloadStatus.Running.name)
                if (item.inProgress || becameTerminal) surfaced.add(item.id)
            }
        }
        republish()
        persistAsync()
    }

    private fun hydrate(record: PersistedDownload): DownloadItem {
        val stored = record.toItem()
        if (record.id <= 0L) return stored
        val live = queryOne(record.id) ?: return when (stored.status) {
            DownloadStatus.Success -> stored
            else -> stored.copy(status = DownloadStatus.Failed)
        }
        return live.copy(
            displayName = stored.displayName.ifBlank { live.displayName },
            documentUri = stored.documentUri ?: live.documentUri,
        )
    }

    private fun attachDestination(
        request: DownloadManager.Request,
        fileName: String,
        mimeType: String?,
    ): Uri? {
        val publicOk = runCatching {
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "$PUBLIC_FOLDER/$fileName",
            )
        }.isSuccess
        if (publicOk) return null
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType ?: "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, PUBLIC_RELATIVE_DIR)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("mediastore-insert-failed")
        request.setDestinationUri(uri)
        return uri
    }

    private fun copyToPublicDownloads(source: Uri, fileName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.RELATIVE_PATH, PUBLIC_RELATIVE_DIR)
            put(MediaStore.MediaColumns.MIME_TYPE, resolver.getType(source) ?: "application/octet-stream")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                resolver.openInputStream(source)?.use { input -> input.copyTo(out) }
                    ?: error("no-input")
            } ?: error("no-output")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (_: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            AppLog.warn(TAG, "blob 写入公共目录失败 name=$fileName")
            null
        }
    }

    private fun queryOne(id: Long): DownloadItem? {
        val dm = downloadManager() ?: return null
        val cursor = runCatching {
            dm.query(DownloadManager.Query().setFilterById(id))
        }.getOrNull() ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val title = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty()
            val localIndex = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val localUri = if (localIndex >= 0) it.getString(localIndex) else null
            val existing = synchronized(lock) { _items.value.find { item -> item.id == id } }
            return DownloadItem(
                id = id,
                displayName = existing?.displayName?.takeIf { name -> name.isNotBlank() } ?: title.ifBlank { "download" },
                status = mapStatus(status),
                documentUri = existing?.documentUri ?: localUri,
                bytesDownloaded = downloaded,
                totalBytes = total,
            )
        }
    }

    private fun ensurePolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                val activeIds = synchronized(lock) {
                    _items.value.filter { it.inProgress && it.id > 0L }.map { it.id }
                }
                if (activeIds.isNotEmpty()) {
                    activeIds.forEach { id ->
                        queryOne(id)?.let { upsert(it, surface = it.inProgress || it.status == DownloadStatus.Success || it.status == DownloadStatus.Failed) }
                    }
                }
                delay(POLL_MS)
            }
        }
    }

    private fun upsert(item: DownloadItem, surface: Boolean) {
        synchronized(lock) {
            val next = _items.value.filterNot { it.id == item.id } + item
            _items.value = next.takeLast(MAX_RECORDS)
            if (surface) {
                surfaced.add(item.id)
                dismissed.remove(item.id)
            }
        }
        republish()
        persistAsync()
        ensurePolling()
    }

    private fun republish() {
        val show = synchronized(lock) { surfaced.toSet() }
        _visibleItems.value = _items.value.filter { it.id in show }
    }

    private fun persistAsync() {
        val snapshot = synchronized(lock) { _items.value }
        scope.launch {
            runCatching {
                context.downloadStore.edit { prefs ->
                    prefs[Keys.RECORDS] = json.encodeToString(
                        snapshot.map {
                            PersistedDownload(
                                id = it.id,
                                displayName = it.displayName,
                                status = it.status.name,
                                documentUri = it.documentUri,
                            )
                        },
                    )
                }
            }
        }
    }

    private fun nextBlobId(): Long {
        val floor = synchronized(lock) { _items.value.minOfOrNull { it.id } ?: -1L }
        blobIds.updateAndGet { current -> minOf(current, if (floor < 0L) floor - 1L else -1L) }
        return blobIds.getAndDecrement()
    }

    private fun downloadManager(): DownloadManager? =
        context.getSystemService(DownloadManager::class.java)

    private fun clearPending(uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        runCatching { context.contentResolver.update(uri, values, null, null) }
    }

    private fun requestHeaders(
        url: String,
        userAgent: String?,
        referer: String?,
        cookie: String?,
    ): Map<String, String> = buildMap {
        userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
        val targetHost = runCatching { URI(url).host }.getOrNull()
        val refererHost = runCatching { URI(referer.orEmpty()).host }.getOrNull()
        if (!referer.isNullOrBlank() && targetHost != null && targetHost.equals(refererHost, ignoreCase = true)) {
            put("Referer", referer)
        }
        cookie?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
    }

    private companion object {
        const val TAG = "download"
        const val MAX_RECORDS = 20
        const val POLL_MS = 500L
        const val PUBLIC_FOLDER = "PocketWebShell"
        const val PUBLIC_RELATIVE_DIR = "Download/PocketWebShell/"
    }

    private object Keys {
        val RECORDS = stringPreferencesKey("download_records")
    }
}

@Serializable
private data class PersistedDownload(
    val id: Long,
    val displayName: String,
    val status: String,
    val documentUri: String? = null,
)

private fun PersistedDownload.toItem(): DownloadItem = DownloadItem(
    id = id,
    displayName = displayName,
    status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.Failed),
    documentUri = documentUri,
)

private fun mapStatus(status: Int): DownloadStatus = when (status) {
    DownloadManager.STATUS_PENDING -> DownloadStatus.Queued
    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> DownloadStatus.Running
    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.Success
    DownloadManager.STATUS_FAILED -> DownloadStatus.Failed
    else -> DownloadStatus.Running
}

private fun isEnqueueableHttp(url: String): Boolean {
    if (url.startsWith("data:", ignoreCase = true)) return false
    val scheme = runCatching { URI(url).scheme }.getOrNull()?.lowercase() ?: return false
    return scheme == "http" || scheme == "https"
}

private fun logHost(url: String): String =
    runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"

private fun String.toUriOrNull(): Uri? = runCatching { toUri() }.getOrNull()
