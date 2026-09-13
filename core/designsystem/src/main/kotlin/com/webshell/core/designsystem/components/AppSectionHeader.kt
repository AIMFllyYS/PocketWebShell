package com.webshell.core.designsystem.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.webshell.core.designsystem.theme.AppSpacing

/** Secondary, unaccented inset-grouped section caption. */
@Composable
fun AppSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    startPadding: Dp = AppSpacing.lg,
) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(
            start = startPadding,
            top = AppSpacing.xs,
            bottom = AppSpacing.sm,
        ),
    )
}
