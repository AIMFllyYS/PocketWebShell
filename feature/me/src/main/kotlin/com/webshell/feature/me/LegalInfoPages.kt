package com.webshell.feature.me

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.webshell.core.designsystem.components.AppCard
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppListRow
import com.webshell.core.designsystem.components.AppSettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object LegalUrls {
    const val REPO = "https://github.com/AIMFllyYS/PocketWebShell"
    const val LICENSE = "https://github.com/AIMFllyYS/PocketWebShell/blob/dev/LICENSE"
    const val CONTRIBUTING = "https://github.com/AIMFllyYS/PocketWebShell/blob/dev/CONTRIBUTING.md"
    const val CAMPUS = "https://github.com/AIMFllyYS/PocketWebShell/blob/dev/docs/OPEN-SOURCE.md"
    const val ISSUES = "https://github.com/AIMFllyYS/PocketWebShell/issues"
    const val SECURITY = "https://github.com/AIMFllyYS/PocketWebShell/blob/dev/SECURITY.md"
}

@Composable
internal fun LegalHubPage(
    onOpenSection: (MeSection) -> Unit,
    onOpenHttps: (String) -> Unit,
    onBack: () -> Unit,
) {
    DetailPage(title = stringResource(R.string.me_legal_section), onBack = onBack) {
        AppCard(contentPadding = PaddingValues(0.dp)) {
            LegalLinkRow(
                icon = Icons.Rounded.Policy,
                title = stringResource(R.string.me_privacy),
                subtitle = stringResource(R.string.me_privacy_hint),
                color = Color(0xFF34C759),
                onClick = { onOpenSection(MeSection.PRIVACY) },
            )
            AppListDivider()
            LegalLinkRow(
                icon = Icons.Rounded.Description,
                title = stringResource(R.string.me_terms),
                subtitle = stringResource(R.string.me_terms_hint),
                color = Color(0xFFAF52DE),
                onClick = { onOpenSection(MeSection.TERMS) },
            )
            AppListDivider()
            LegalLinkRow(
                icon = Icons.Rounded.Balance,
                title = stringResource(R.string.me_oss_license),
                subtitle = stringResource(R.string.me_oss_license_hint),
                color = Color(0xFFFF9500),
                onClick = { onOpenSection(MeSection.LICENSE) },
            )
            AppListDivider()
            LegalLinkRow(
                icon = Icons.Rounded.Code,
                title = stringResource(R.string.me_source_repo),
                subtitle = stringResource(R.string.me_source_repo_hint),
                color = Color(0xFF007AFF),
                onClick = { onOpenHttps(LegalUrls.REPO) },
            )
            AppListDivider()
            LegalLinkRow(
                icon = Icons.Rounded.VolunteerActivism,
                title = stringResource(R.string.me_contribute),
                subtitle = stringResource(R.string.me_contribute_hint),
                color = Color(0xFF5AC8FA),
                onClick = { onOpenSection(MeSection.CONTRIBUTE) },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun LegalMarkdownPage(
    title: String,
    @RawRes rawResId: Int,
    onOpenHttps: (String) -> Unit,
    onBack: () -> Unit,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val resources = LocalContext.current.resources
    val markdown by produceState<String?>(initialValue = null, resources, rawResId) {
        value = withContext(Dispatchers.IO) {
            resources.openRawResource(rawResId).bufferedReader().use { it.readText() }
        }
    }
    val openHttps = rememberUpdatedState(onOpenHttps)
    val uriHandler = remember {
        object : UriHandler {
            override fun openUri(uri: String) {
                acceptedHttpsUrl(uri)?.let(openHttps.value)
            }
        }
    }
    DetailPage(title = title, onBack = onBack) {
        val body = markdown
        if (body == null) {
            Text(
                stringResource(R.string.me_legal_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                SelectionContainer {
                    Markdown(content = body, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        footer()
    }
}

@Composable
internal fun LicenseNoticePage(
    onOpenHttps: (String) -> Unit,
    onBack: () -> Unit,
) {
    LegalMarkdownPage(
        title = stringResource(R.string.me_oss_license),
        rawResId = R.raw.license_notice,
        onOpenHttps = onOpenHttps,
        onBack = onBack,
    ) {
        Spacer(Modifier.height(24.dp))
        AppCard(contentPadding = PaddingValues(0.dp)) {
            AppListRow(
                title = stringResource(R.string.me_legal_read_gpl),
                leadingIcon = Icons.Rounded.Balance,
                leadingIconBackground = Color(0xFFFF9500),
                onClick = { onOpenHttps(LegalUrls.LICENSE) },
                trailing = { SettingsChevron() },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun ContributePage(
    onOpenHttps: (String) -> Unit,
    onBack: () -> Unit,
) {
    DetailPage(title = stringResource(R.string.me_contribute), onBack = onBack) {
        Text(
            stringResource(R.string.me_contribute_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        AppSettingsSection(stringResource(R.string.me_contribute_links)) {
            LegalLinkRow(
                icon = Icons.Rounded.Code,
                title = stringResource(R.string.me_source_repo),
                color = Color(0xFF007AFF),
                onClick = { onOpenHttps(LegalUrls.REPO) },
            )
            AppListDivider()
            LegalLinkRow(
                icon = Icons.Rounded.Description,
                title = stringResource(R.string.me_legal_open_contributing),
                color = Color(0xFFAF52DE),
                onClick = { onOpenHttps(LegalUrls.CONTRIBUTING) },
            )
            AppListDivider()
            LegalLinkRow(
                icon = Icons.Rounded.School,
                title = stringResource(R.string.me_legal_open_campus),
                color = Color(0xFF34C759),
                onClick = { onOpenHttps(LegalUrls.CAMPUS) },
            )
            AppListDivider()
            LegalLinkRow(
                icon = Icons.Rounded.BugReport,
                title = stringResource(R.string.me_legal_open_issues),
                color = Color(0xFFFF9500),
                onClick = { onOpenHttps(LegalUrls.ISSUES) },
            )
            AppListDivider()
            LegalLinkRow(
                icon = Icons.Rounded.Shield,
                title = stringResource(R.string.me_legal_open_security),
                color = Color(0xFF007AFF),
                onClick = { onOpenHttps(LegalUrls.SECURITY) },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LegalLinkRow(
    icon: ImageVector,
    title: String,
    color: Color,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    AppListRow(
        title = title,
        subtitle = subtitle,
        leadingIcon = icon,
        leadingIconBackground = color,
        onClick = onClick,
        trailing = { SettingsChevron() },
    )
}
