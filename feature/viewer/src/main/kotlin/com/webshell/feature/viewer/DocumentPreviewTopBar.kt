package com.webshell.feature.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webshell.core.designsystem.components.PageLoadIndicator
import com.webshell.core.designsystem.components.staticGlassSurface
import com.webshell.core.designsystem.R as DesignR

/** Browser-like address chrome for temporary incoming documents. Path is display-only. */
@Composable
internal fun DocumentPreviewTopBar(
    displayPath: String,
    onBack: () -> Unit,
    loading: Boolean = false,
    progress: Int = 0,
    sessionKey: Any? = null,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .staticGlassSurface(
                    shape = RoundedCornerShape(26.dp),
                    tint = MaterialTheme.colorScheme.surface,
                    opacity = 0.36f,
                )
                .heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = stringResource(DesignR.string.designsystem_back),
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = displayPath.ifBlank { stringResource(R.string.viewer_opening) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            )
        }
        PageLoadIndicator(loading = loading, rawProgress = progress, sessionKey = sessionKey)
    }
}
