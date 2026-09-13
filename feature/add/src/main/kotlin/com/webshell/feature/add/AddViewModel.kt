package com.webshell.feature.add

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webshell.core.data.HomeSlotAllocator
import com.webshell.core.data.IncomingSourceKey
import com.webshell.core.data.SettingsRepository
import com.webshell.core.data.UserIconRepository
import com.webshell.core.data.WebAppDao
import com.webshell.core.data.metadata.SiteMetadataFetcher
import com.webshell.core.model.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URLDecoder
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class AddViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fetcher: SiteMetadataFetcher,
    private val importer: LocalAppImporter,
    private val dao: WebAppDao,
    private val settingsRepository: SettingsRepository,
    private val icons: UserIconRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<AddUiState>(AddUiState.Input)
    val state: StateFlow<AddUiState> = _state.asStateFlow()
    private val _created = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val created: SharedFlow<Unit> = _created.asSharedFlow()
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    /** String resources only: failure details and local file paths are not presented or logged. */
    val messages: SharedFlow<Int> = _messages.asSharedFlow()
    private var preparation: Job? = null
    private var pickerJob: Job? = null
    private var discoverJob: Job? = null
    private var revision = 0L
    private var pickerGeneration = 0
    private var pickerStack: List<PickerFrame> = emptyList()
    private var readPermanentlyDenied = false
    private var pickerQuery = ""
    private var discoveredCache: List<HtmlPickerFileUi> = emptyList()
    private var discoverFinished = false
    private var lastDiscoveryFingerprint: StorageFingerprint? = null

    fun confirmUrl(rawUrl: String) {
        val normalized = AddUrl.normalize(rawUrl) ?: return
        preparation?.cancel()
        val expectedRevision = ++revision
        _state.value = AddUiState.Loading
        preparation = viewModelScope.launch {
            val metadata = fetcher.fetch(normalized).getOrNull()
            if (revision != expectedRevision) return@launch
            _state.value = if (metadata == null) {
                AppLog.warn("add", "Metadata unavailable; manual editor shown")
                AddUiState.Edit(
                    draft = AddDraft(
                        appId = newAppId(),
                        url = normalized,
                        title = AddUrl.hostLabel(normalized),
                        iconUrl = fetcher.displayFallbackIconUrl(normalized).orEmpty(),
                    ),
                    fetchFailed = true,
                )
            } else {
                AddUiState.Edit(
                    draft = AddDraft(
                        appId = newAppId(),
                        url = metadata.finalUrl,
                        title = metadata.title,
                        iconUrl = metadata.iconUrl.orEmpty(),
                    ),
                )
            }
        }
    }

    fun importLocal(uris: List<Uri>) {
        if (uris.isEmpty()) return
        enterImportedDraft { importer.import(it, uris) }
    }

    fun openHtmlPicker(readPermanentlyDenied: Boolean = false) {
        this.readPermanentlyDenied = readPermanentlyDenied
        pickerStack = emptyList()
        pickerQuery = ""
        discoveredCache = emptyList()
        discoverFinished = false
        lastDiscoveryFingerprint = null
        startDiscovery()
        loadPicker()
    }

    fun setPickerQuery(query: String) {
        pickerQuery = query
        val current = _state.value as? AddUiState.PickingLocal ?: return
        _state.value = current.copy(query = query)
    }

    fun refreshPicker() {
        if (_state.value !is AddUiState.PickingLocal) return
        if (storageFingerprint() != lastDiscoveryFingerprint) startDiscovery()
        loadPicker()
    }

    fun openRoot(path: String, kind: HtmlImportRootKind) {
        pickerStack = listOf(PickerFrame(rootTitle(kind), path, kind, allowAndroidData = false))
        loadPicker()
    }

    fun openDir(path: String) {
        val current = _state.value as? AddUiState.PickingLocal ?: return
        val name = current.folders.firstOrNull { it.path == path }?.name ?: File(path).name
        val parent = pickerStack.lastOrNull()
        val allowAndroidData = parent?.allowAndroidData == true || isAppOwnedPath(File(path))
        pickerStack = pickerStack + PickerFrame(name, path, parent?.kind, allowAndroidData)
        loadPicker()
    }

    fun openCrumb(index: Int) {
        if (index !in pickerStack.indices) return
        pickerStack = pickerStack.take(index + 1)
        loadPicker()
    }

    fun onPickerBack() {
        if (pickerStack.isEmpty()) {
            cancelPicker()
            return
        }
        pickerStack = pickerStack.dropLast(1)
        loadPicker()
    }

    fun cancelPicker() {
        pickerJob?.cancel()
        discoverJob?.cancel()
        pickerGeneration++
        pickerStack = emptyList()
        pickerQuery = ""
        discoveredCache = emptyList()
        discoverFinished = false
        lastDiscoveryFingerprint = null
        if (_state.value is AddUiState.PickingLocal) _state.value = AddUiState.Input
    }

    fun importPicked(path: String) {
        val file = File(path)
        enterImportedDraft(IncomingSourceKey.fromFilesystemPath(file.absolutePath)) {
            importer.importFiles(it, listOf(file))
        }
    }

    fun importIcon(uri: Uri) {
        val editor = _state.value as? AddUiState.Edit ?: return
        if (editor.isSaving || editor.isImportingIcon) return
        val expectedId = editor.draft.appId
        _state.value = editor.copy(isImportingIcon = true)
        viewModelScope.launch {
            val result = icons.importIcon(uri)
            val current = _state.value as? AddUiState.Edit ?: return@launch
            if (current.draft.appId != expectedId) return@launch
            _state.value = current.copy(
                draft = result.getOrNull()?.let { current.draft.copy(iconUrl = it) } ?: current.draft,
                isImportingIcon = false,
            )
            if (result.isFailure) _messages.tryEmit(R.string.add_icon_import_failed)
        }
    }

    fun updateDraft(transform: (AddDraft) -> AddDraft) {
        val editor = _state.value as? AddUiState.Edit ?: return
        if (!editor.isSaving && !editor.isImportingIcon) _state.value = editor.copy(draft = transform(editor.draft))
    }

    fun save() {
        val editor = _state.value as? AddUiState.Edit ?: return
        if (editor.isSaving || editor.isImportingIcon) return
        val draft = editor.draft
        if (!draft.isLocal && AddUrl.normalize(draft.url) == null) {
            _messages.tryEmit(R.string.add_address_error)
            return
        }
        _state.value = editor.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                val (page, slot) = if (settings.autoArrangeHome) 0 to -1 else {
                    HomeSlotAllocator.appendSlot(
                        apps = dao.observeAll().first(),
                        pageCapacity = (settings.gridColumns * settings.gridRows).coerceAtLeast(1),
                    )
                }
                dao.upsert(draft.toNewEntity(page, slot, System.currentTimeMillis(), newAppId()))
                AppLog.log("add", "Site shortcut created")
                _messages.tryEmit(R.string.add_saved)
                _created.tryEmit(Unit)
                _state.value = AddUiState.Input
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AppLog.warn("add", "Site save failed")
                _state.value = editor
                _messages.tryEmit(R.string.add_save_failed)
            }
        }
    }

    fun reset() {
        if ((_state.value as? AddUiState.Edit)?.isSaving == true) return
        preparation?.cancel()
        pickerJob?.cancel()
        discoverJob?.cancel()
        revision++
        pickerGeneration++
        pickerStack = emptyList()
        pickerQuery = ""
        discoveredCache = emptyList()
        discoverFinished = false
        lastDiscoveryFingerprint = null
        _state.value = AddUiState.Input
    }

    private fun enterImportedDraft(
        sourceKey: String? = null,
        import: suspend (String) -> Result<LocalAppImporter.ImportedApp>,
    ) {
        preparation?.cancel()
        pickerJob?.cancel()
        discoverJob?.cancel()
        val expectedRevision = ++revision
        _state.value = AddUiState.Loading
        preparation = viewModelScope.launch {
            val appId = newAppId()
            val result = import(appId)
            if (revision != expectedRevision) return@launch
            result.onSuccess { imported ->
                val entryName = runCatching {
                    URLDecoder.decode(imported.entryUrl.substringAfterLast('/'), Charsets.UTF_8)
                }.getOrDefault("index.html")
                _state.value = AddUiState.Edit(
                    AddDraft(
                        appId = appId,
                        url = imported.entryUrl,
                        title = entryName.substringBeforeLast('.'),
                        iconUrl = imported.iconPath.orEmpty(),
                        isLocal = true,
                        importSourceKey = sourceKey,
                    ),
                )
            }.onFailure {
                AppLog.warn("add", "Local import failed")
                _state.value = AddUiState.Input
                _messages.tryEmit(R.string.add_import_failed)
            }
        }
    }

    private fun loadPicker() {
        pickerJob?.cancel()
        val generation = ++pickerGeneration
        val current = _state.value as? AddUiState.PickingLocal
        _state.value = (current ?: AddUiState.PickingLocal()).copy(
            isLoading = true,
            query = pickerQuery,
            discovered = discoveredCache,
            isDiscovering = !discoverFinished,
        )
        pickerJob = viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { buildPickerState() }
            if (generation != pickerGeneration) return@launch
            _state.value = snapshot
        }
    }

    private fun startDiscovery() {
        lastDiscoveryFingerprint = storageFingerprint()
        discoverFinished = false
        discoverJob?.cancel()
        discoverJob = viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { discoverHtml() }
            discoveredCache = found
            discoverFinished = true
            val current = _state.value as? AddUiState.PickingLocal ?: return@launch
            _state.value = current.copy(discovered = found, isDiscovering = false)
        }
    }

    private fun buildPickerState(): AddUiState.PickingLocal {
        val fingerprint = storageFingerprint()
        val sdk = fingerprint.sdk
        val readGranted = fingerprint.readGranted
        val allFiles = fingerprint.allFiles
        val publicListingAllowed = HtmlImportStorageAccess.canAttemptPublicListing(sdk, readGranted, allFiles)
        val volume = HtmlImportRoots.volumeRoot(context, sdk)
        val roots = HtmlImportRoots.resolve(context, sdk).map { root ->
            val owned = isAppOwnedPath(root.directory)
            HtmlPickerRootUi(
                kind = root.kind,
                path = root.directory.absolutePath,
                locked = (!publicListingAllowed && !owned) || !HtmlImportRoots.isListable(root.directory),
            )
        }
        val publicUsable = roots.any { !it.locked && it.kind != HtmlImportRootKind.MiuiDownloads }
        val banner = permissionBanner(sdk, readGranted, allFiles, publicUsable)
        val frame = pickerStack.lastOrNull()
        if (frame == null) {
            return AddUiState.PickingLocal(
                crumbs = emptyList(),
                roots = roots,
                listingStatus = HtmlPickerListingStatus.Ready,
                permissionBanner = banner,
                isLoading = false,
                query = pickerQuery,
                discovered = discoveredCache,
                isDiscovering = !discoverFinished,
            )
        }
        val listed = listFrame(frame, volume, publicListingAllowed)
        return AddUiState.PickingLocal(
            crumbs = pickerStack.map { HtmlPickerCrumb(it.title, it.path) },
            roots = roots,
            folders = listed.folders,
            files = listed.files,
            listingStatus = listed.status,
            permissionBanner = banner,
            isLoading = false,
            query = pickerQuery,
            discovered = discoveredCache,
            isDiscovering = !discoverFinished,
        )
    }

    private fun discoverHtml(): List<HtmlPickerFileUi> {
        val fingerprint = lastDiscoveryFingerprint ?: storageFingerprint()
        val sdk = fingerprint.sdk
        val volume = HtmlImportRoots.volumeRoot(context, sdk)
        val extraSeeds = HtmlImportRoots.resolve(context, sdk)
            .filter { it.kind != HtmlImportRootKind.Storage }
            .map { it.directory }
        val owned = listOfNotNull(
            HtmlImportRoots.appOwnedFallback(context, HtmlImportRootKind.Download),
            HtmlImportRoots.appOwnedFallback(context, HtmlImportRootKind.Documents),
        )
        val publicListingAllowed = HtmlImportStorageAccess.canAttemptPublicListing(
            fingerprint.sdk,
            fingerprint.readGranted,
            fingerprint.allFiles,
        )
        return HtmlDiscovery.discover(context, volume, extraSeeds, owned, publicListingAllowed).map { item ->
            HtmlPickerFileUi(
                name = item.name,
                path = item.file.absolutePath,
                sizeBytes = item.sizeBytes,
                lastModifiedMillis = item.lastModified,
                location = item.location,
            )
        }
    }

    private fun listFrame(
        frame: PickerFrame,
        volume: File?,
        publicListingAllowed: Boolean,
    ): ListedContent {
        val dir = File(frame.path)
        val owned = frame.allowAndroidData || isAppOwnedPath(dir)
        if (!publicListingAllowed && !owned) {
            val extras = if (pickerStack.size == 1 && frame.kind != null) {
                fallbackEntries(frame.kind, publicDenied = true)
            } else {
                emptyList()
            }
            if (extras.isEmpty()) {
                return ListedContent(emptyList(), emptyList(), HtmlPickerListingStatus.AccessDenied)
            }
            return listedFromEntries(extras, volume)
        }
        val outcome = HtmlDirectoryLister.list(
            dir = dir,
            mustStayUnder = if (frame.allowAndroidData) dir else volume,
            allowAndroidData = frame.allowAndroidData,
        )
        val publicEntries = when (outcome) {
            HtmlDirectoryLister.Outcome.AccessDenied -> null
            is HtmlDirectoryLister.Outcome.Success -> outcome.entries
        }
        val extras = if (pickerStack.size == 1 && frame.kind != null) {
            fallbackEntries(frame.kind, publicEntries == null)
        } else {
            emptyList()
        }
        val merged = (publicEntries.orEmpty() + extras).distinctBy {
            runCatching { it.file.canonicalPath }.getOrDefault(it.file.absolutePath)
        }
        if (publicEntries == null && extras.isEmpty()) {
            return ListedContent(emptyList(), emptyList(), HtmlPickerListingStatus.AccessDenied)
        }
        return listedFromEntries(merged, volume)
    }

    private fun listedFromEntries(
        merged: List<HtmlDirectoryLister.HtmlEntry>,
        volume: File?,
    ): ListedContent {
        val folders = merged.filterIsInstance<HtmlDirectoryLister.HtmlEntry.Directory>().map {
            HtmlPickerDirUi(it.name, it.file.absolutePath)
        }
        val files = merged.filterIsInstance<HtmlDirectoryLister.HtmlEntry.HtmlFile>()
            .sortedByDescending { it.lastModified }
            .map {
                HtmlPickerFileUi(
                    name = it.name,
                    path = it.file.absolutePath,
                    sizeBytes = it.sizeBytes,
                    lastModifiedMillis = it.lastModified,
                    location = HtmlDiscovery.locationLabel(it.file, volume),
                )
            }
        val status = if (folders.isEmpty() && files.isEmpty()) {
            HtmlPickerListingStatus.Empty
        } else {
            HtmlPickerListingStatus.Ready
        }
        return ListedContent(folders, files, status)
    }

    private fun fallbackEntries(kind: HtmlImportRootKind, publicDenied: Boolean): List<HtmlDirectoryLister.HtmlEntry> {
        val ownedDir = HtmlImportRoots.appOwnedFallback(context, kind)
        val fromDir = if (ownedDir != null) {
            when (val owned = HtmlDirectoryLister.list(ownedDir, mustStayUnder = ownedDir, allowAndroidData = true)) {
                HtmlDirectoryLister.Outcome.AccessDenied -> emptyList()
                is HtmlDirectoryLister.Outcome.Success -> owned.entries
            }
        } else {
            emptyList()
        }
        val fromStore = if (publicDenied && (kind == HtmlImportRootKind.Download || kind == HtmlImportRootKind.MiuiDownloads)) {
            HtmlImportRoots.appOwnedHtmlFiles(context).map { file ->
                HtmlDirectoryLister.HtmlEntry.HtmlFile(file.name, file, file.length(), file.lastModified())
            }
        } else {
            emptyList()
        }
        return fromDir + fromStore
    }

    private fun permissionBanner(
        sdk: Int,
        readGranted: Boolean,
        allFiles: Boolean,
        publicUsable: Boolean,
    ): HtmlPickerPermissionBanner? {
        val xiaomi = isXiaomiManufacturer()
        if (sdk >= 30 && !allFiles) {
            return HtmlPickerPermissionBanner(
                showManageRationale = true,
                showXiaomiHint = xiaomi,
                permanentlyDenied = sdk >= 33 || !publicUsable,
            )
        }
        if (sdk in 29..32 && !readGranted) {
            return HtmlPickerPermissionBanner(
                showManageRationale = false,
                showXiaomiHint = false,
                permanentlyDenied = readPermanentlyDenied,
            )
        }
        return null
    }

    private fun isAppOwnedPath(file: File): Boolean {
        val owned = listOfNotNull(
            HtmlImportRoots.appOwnedFallback(context, HtmlImportRootKind.Download),
            HtmlImportRoots.appOwnedFallback(context, HtmlImportRootKind.Documents),
        )
        return owned.any { HtmlImportRoots.isUnder(file, it) }
    }

    private fun rootTitle(kind: HtmlImportRootKind): String = context.getString(
        when (kind) {
            HtmlImportRootKind.Download -> R.string.add_html_picker_root_download
            HtmlImportRootKind.Documents -> R.string.add_html_picker_root_documents
            HtmlImportRootKind.Storage -> R.string.add_html_picker_root_storage
            HtmlImportRootKind.MiuiDownloads -> R.string.add_html_picker_root_download
        },
    )

    private fun isXiaomiManufacturer(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")
    }

    private fun storageFingerprint(): StorageFingerprint {
        val sdk = Build.VERSION.SDK_INT
        val readPermission = HtmlImportStorageAccess.runtimeReadPermission(sdk)
        val readGranted = readPermission != null &&
            context.checkSelfPermission(readPermission) == PackageManager.PERMISSION_GRANTED
        return StorageFingerprint(
            sdk = sdk,
            readGranted = readGranted,
            allFiles = HtmlImportStorageAccess.hasAllFilesAccess(),
        )
    }

    private fun newAppId(): String = "app-" + UUID.randomUUID().toString().take(8)

    private data class StorageFingerprint(
        val sdk: Int,
        val readGranted: Boolean,
        val allFiles: Boolean,
    )

    private data class PickerFrame(
        val title: String,
        val path: String,
        val kind: HtmlImportRootKind?,
        val allowAndroidData: Boolean,
    )

    private data class ListedContent(
        val folders: List<HtmlPickerDirUi>,
        val files: List<HtmlPickerFileUi>,
        val status: HtmlPickerListingStatus,
    )
}
