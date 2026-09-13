package com.webshell.feature.add

/** New-site draft only. Legacy website text zoom remains in persistence, not in this editor. */
data class AddDraft(
    val appId: String = "",
    val url: String = "",
    val title: String = "",
    val iconUrl: String = "",
    val desktopMode: Boolean = false,
    val darkMode: Boolean = false,
    val keepAlive: Boolean = true,
    val externalLinksToBrowser: Boolean = false,
    val isLocal: Boolean = false,
    val siteShellNewWindowPolicy: String? = null,
    val importSourceKey: String? = null,
)

data class HtmlPickerCrumb(
    val title: String,
    val path: String,
)

data class HtmlPickerRootUi(
    val kind: HtmlImportRootKind,
    val path: String,
    val locked: Boolean,
)

data class HtmlPickerDirUi(
    val name: String,
    val path: String,
)

data class HtmlPickerFileUi(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val location: String = "",
)

enum class HtmlPickerListingStatus {
    Ready,
    AccessDenied,
    Empty,
}

data class HtmlPickerPermissionBanner(
    val showManageRationale: Boolean,
    val showXiaomiHint: Boolean,
    val permanentlyDenied: Boolean,
)

sealed interface AddUiState {
    data object Input : AddUiState
    data object Loading : AddUiState
    data class PickingLocal(
        val crumbs: List<HtmlPickerCrumb> = emptyList(),
        val roots: List<HtmlPickerRootUi> = emptyList(),
        val folders: List<HtmlPickerDirUi> = emptyList(),
        val files: List<HtmlPickerFileUi> = emptyList(),
        val listingStatus: HtmlPickerListingStatus = HtmlPickerListingStatus.Ready,
        val permissionBanner: HtmlPickerPermissionBanner? = null,
        val isLoading: Boolean = false,
        val query: String = "",
        val discovered: List<HtmlPickerFileUi> = emptyList(),
        val isDiscovering: Boolean = false,
    ) : AddUiState
    data class Edit(
        val draft: AddDraft,
        val fetchFailed: Boolean = false,
        val isSaving: Boolean = false,
        val isImportingIcon: Boolean = false,
    ) : AddUiState
}
