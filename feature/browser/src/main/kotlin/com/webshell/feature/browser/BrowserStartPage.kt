package com.webshell.feature.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.webshell.core.designsystem.components.staticGlassSurface
import com.webshell.core.designsystem.theme.LocalOverlayClearance

@Composable
internal fun BrowserStartPage(
    bookmarks: List<BrowserSavedPage>,
    recents: List<BrowserSavedPage>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = remember(recents) { recents.distinctBy { it.url.displayKey() }.take(6) }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 12.dp,
            bottom = 24.dp + LocalOverlayClearance.current,
        ),
    ) {
        item {
            Text(
                stringResource(R.string.browser_start_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        item {
            AppSectionHeader(
                stringResource(R.string.browser_start_favorites),
                Modifier.padding(top = 12.dp),
                startPadding = 0.dp,
            )
            if (bookmarks.isEmpty()) {
                FavoritesEmptyHint()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    bookmarks.take(12).chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { page ->
                                FavoriteTile(page, onClick = { onOpen(page.url) }, Modifier.weight(1f))
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        item {
            AppSectionHeader(
                stringResource(R.string.browser_start_recent),
                Modifier.padding(top = 16.dp),
                startPadding = 0.dp,
            )
            AppCard(contentPadding = PaddingValues(0.dp)) {
                if (shown.isEmpty()) {
                    Text(
                        stringResource(R.string.browser_start_recent_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    )
                } else {
                    shown.forEachIndexed { index, page ->
                        val host = page.url.host()
                        AppListRow(
                            title = page.title.ifBlank { host },
                            subtitle = host,
                            leading = {
                                SiteIcon(
                                    title = page.title.ifBlank { host },
                                    iconUrl = page.iconUrl,
                                    size = 30.dp,
                                    cornerRadiusPercent = 24,
                                )
                            },
                            titleMaxLines = 1,
                            subtitleMaxLines = 1,
                            onClick = { onOpen(page.url) },
                        )
                        if (index < shown.lastIndex) AppListDivider(hasLeadingIcon = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesEmptyHint() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(60.dp).staticGlassSurface(shape = RoundedCornerShape(26)))
        Text(
            stringResource(R.string.browser_start_favorites_guide),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FavoriteTile(page: BrowserSavedPage, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val host = page.url.host()
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SiteIcon(
            title = page.title.ifBlank { host },
            iconUrl = page.iconUrl,
            size = 60.dp,
        )
        Text(
            page.title.ifBlank { host },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
        )
    }
}
