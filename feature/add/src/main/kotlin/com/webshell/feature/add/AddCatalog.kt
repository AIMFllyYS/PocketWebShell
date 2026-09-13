package com.webshell.feature.add

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.webshell.core.designsystem.catalog.CatalogCategory
import com.webshell.core.designsystem.catalog.CatalogEntry

/** The app hosts these scenes; all input is in memory and callbacks never invoke business code. */
fun addCatalog(): List<CatalogEntry> = listOf(
    CatalogEntry("add.input", CatalogCategory.FORMS, R.string.add_catalog_input, R.string.add_catalog_input_detail) {
        CatalogInput(error = false)
    },
    CatalogEntry("add.input.error", CatalogCategory.FORMS, R.string.add_catalog_error, R.string.add_catalog_error_detail) {
        CatalogInput(error = true)
    },
    CatalogEntry("add.loading", CatalogCategory.CONTENT, R.string.add_catalog_loading, R.string.add_catalog_loading_detail) {
        AddLoadingContent()
    },
    CatalogEntry("add.editor", CatalogCategory.FORMS, R.string.add_catalog_editor, R.string.add_catalog_editor_detail) {
        CatalogEditor(local = false, fetchFailed = false)
    },
    CatalogEntry("add.editor.manual", CatalogCategory.FORMS, R.string.add_catalog_manual, R.string.add_catalog_manual_detail) {
        CatalogEditor(local = false, fetchFailed = true)
    },
    CatalogEntry("add.editor.local", CatalogCategory.FORMS, R.string.add_catalog_local, R.string.add_catalog_local_detail) {
        CatalogEditor(local = true, fetchFailed = false)
    },
    CatalogEntry("add.html.picker", CatalogCategory.FORMS, R.string.add_catalog_html_picker, R.string.add_catalog_html_picker_detail) {
        CatalogHtmlPicker(
            AddUiState.PickingLocal(
                roots = catalogRoots(locked = false),
                discovered = listOf(
                    HtmlPickerFileUi("notes.html", "/catalog/Download/notes.html", 2048L, 0L, "Download"),
                    HtmlPickerFileUi("chat.htm", "/catalog/Tencent/MicroMsg/Download/chat.htm", 1024L, 0L, "Tencent/MicroMsg/Download"),
                ),
            ),
        )
    },
    CatalogEntry("add.html.picker.files", CatalogCategory.FORMS, R.string.add_catalog_html_picker_files, R.string.add_catalog_html_picker_files_detail) {
        val download = stringResource(R.string.add_html_picker_root_download)
        CatalogHtmlPicker(
            AddUiState.PickingLocal(
                crumbs = listOf(HtmlPickerCrumb(download, "/catalog/Download")),
                roots = catalogRoots(locked = false),
                folders = listOf(HtmlPickerDirUi("notes", "/catalog/Download/notes")),
                files = listOf(
                    HtmlPickerFileUi("page.html", "/catalog/Download/page.html", 2048L, 0L),
                    HtmlPickerFileUi("readme.htm", "/catalog/Download/readme.htm", 512L, 0L),
                ),
            ),
        )
    },
    CatalogEntry("add.html.picker.empty", CatalogCategory.FORMS, R.string.add_catalog_html_picker_empty, R.string.add_catalog_html_picker_empty_detail) {
        val documents = stringResource(R.string.add_html_picker_root_documents)
        CatalogHtmlPicker(
            AddUiState.PickingLocal(
                crumbs = listOf(HtmlPickerCrumb(documents, "/catalog/Documents")),
                roots = catalogRoots(locked = false),
                listingStatus = HtmlPickerListingStatus.Empty,
            ),
        )
    },
    CatalogEntry("add.html.picker.permission", CatalogCategory.FORMS, R.string.add_catalog_html_picker_permission, R.string.add_catalog_html_picker_permission_detail) {
        CatalogHtmlPicker(
            AddUiState.PickingLocal(
                roots = catalogRoots(locked = true),
                permissionBanner = HtmlPickerPermissionBanner(
                    showManageRationale = true,
                    showXiaomiHint = true,
                    permanentlyDenied = false,
                ),
            ),
        )
    },
    CatalogEntry("add.html.picker.search.empty", CatalogCategory.FORMS, R.string.add_catalog_html_picker_search, R.string.add_catalog_html_picker_search_detail) {
        CatalogHtmlPicker(
            AddUiState.PickingLocal(
                roots = catalogRoots(locked = false),
                query = "telegram",
                discovered = listOf(
                    HtmlPickerFileUi("notes.html", "/catalog/Download/notes.html", 2048L, 0L, "Download"),
                    HtmlPickerFileUi("chat.htm", "/catalog/Download/WeiXin/chat.htm", 1024L, 0L, "Download/WeiXin"),
                ),
            ),
        )
    },
)

@Composable
private fun CatalogInput(error: Boolean) {
    var url by remember { mutableStateOf(if (error) "https://" else "") }
    var invalid by remember { mutableStateOf(error) }
    AddInputContent(
        url = url, showError = invalid,
        onUrlChange = { url = it; invalid = false },
        onContinue = { invalid = AddUrl.normalize(url) == null },
        onImportLocal = {},
    )
}

@Composable
private fun CatalogEditor(local: Boolean, fetchFailed: Boolean) {
    val title = stringResource(if (local) R.string.add_catalog_local_name else R.string.add_catalog_site_name)
    var draft by remember(local) {
        mutableStateOf(AddDraft(
            appId = "catalog-add", title = title, isLocal = local,
            url = if (local) "local://catalog-add/index.html" else "https://example.invalid/reading",
        ))
    }
    AddEditorContent(
        state = AddUiState.Edit(draft = draft, fetchFailed = fetchFailed),
        onUpdate = { transform -> draft = transform(draft) },
        onSave = {}, onBack = {}, onPickIcon = {},
    )
}

@Composable
private fun CatalogHtmlPicker(state: AddUiState.PickingLocal) {
    HtmlFilePickerScreen(
        state = state,
        onBack = {},
        onCancel = {},
        onGrantStorage = {},
        onOpenSystemPicker = {},
        onOpenRoot = {},
        onOpenDir = {},
        onImportFile = {},
        onOpenCrumb = {},
        onQueryChange = {},
    )
}

private fun catalogRoots(locked: Boolean): List<HtmlPickerRootUi> = listOf(
    HtmlPickerRootUi(HtmlImportRootKind.Download, "/catalog/Download", locked),
    HtmlPickerRootUi(HtmlImportRootKind.Documents, "/catalog/Documents", locked),
    HtmlPickerRootUi(HtmlImportRootKind.Storage, "/catalog/storage", locked),
)
