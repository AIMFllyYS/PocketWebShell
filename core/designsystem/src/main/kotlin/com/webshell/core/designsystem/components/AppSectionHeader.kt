package com.webshell.core.designsystem.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.webshell.core.designsystem.theme.AppSpacing

/** 分组标题：小字号 + primary 强调色，出现在分组卡片上方。 */
@Composable
fun AppSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(
            start = AppSpacing.lg,
            top = AppSpacing.xs,
            bottom = AppSpacing.sm,
        ),
    )
}
