package com.webshell.feature.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webshell.core.designsystem.components.AppCard
import com.webshell.core.designsystem.components.AppListDivider
import com.webshell.core.designsystem.components.AppListRow
import com.webshell.core.designsystem.components.AppSectionHeader
import com.webshell.core.designsystem.components.SiteIcon

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BrowserStartPage(
    bookmarks: List<BrowserSavedPage>,
    recents: List<BrowserSavedPage>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            stringResource(R.string.browser_start_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        AppSectionHeader(stringResource(R.string.browser_start_favorites), Modifier.padding(top = 20.dp))
        if (bookmarks.isEmpty()) {
            Text(
                stringResource(R.string.browser_start_favorites_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                bookmarks.take(12).forEach { page ->
                    FavoriteTile(page, onClick = { onOpen(page.url) })
                }
            }
        }
        AppSectionHeader(stringResource(R.string.browser_start_recent), Modifier.padding(top = 24.dp))
        AppCard(contentPadding = PaddingValues(0.dp), modifier = Modifier.padding(bottom = 24.dp)) {
            if (recents.isEmpty()) {
                Text(
                    stringResource(R.string.browser_start_recent_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                )
            } else {
                recents.take(8).forEachIndexed { index, page ->
                    AppListRow(
                        title = page.title.ifBlank { page.url.stripScheme() },
                        subtitle = page.url.stripScheme(),
                        titleMaxLines = 1,
                        subtitleMaxLines = 1,
                        onClick = { onOpen(page.url) },
                    )
                    if (index < recents.take(8).lastIndex) AppListDivider(hasLeadingIcon = false)
                }
            }
        }
    }
}

@Composable
private fun FavoriteTile(page: BrowserSavedPage, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(72.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SiteIcon(title = page.title.ifBlank { page.url }, iconUrl = null, size = 56.dp)
        Text(
            page.title.ifBlank { page.url.stripScheme() },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
        )
    }
}
