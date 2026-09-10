package com.webshell.feature.add

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webshell.core.designsystem.components.AppCard
import com.webshell.core.designsystem.components.AppFormField
import com.webshell.core.designsystem.components.AppListRow
import com.webshell.core.designsystem.components.AppPrimaryButton
import com.webshell.core.designsystem.components.AppSectionHeader
import com.webshell.core.designsystem.components.BrandMark
import com.webshell.core.designsystem.theme.LocalOverlayClearance

/** Stateless production input, also used by Playbook with local-only callbacks. */
@Composable
internal fun AddInputContent(
    url: String,
    showError: Boolean,
    onUrlChange: (String) -> Unit,
    onContinue: () -> Unit,
    onImportLocal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .padding(bottom = LocalOverlayClearance.current),
    ) {
        Text(
            stringResource(R.string.add_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(R.string.add_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(
                modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.large),
            )
            Text(
                stringResource(R.string.add_home_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                stringResource(R.string.add_home_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        AppSectionHeader(stringResource(R.string.add_address_section))
        AppCard(contentPadding = PaddingValues(0.dp)) {
            AppFormField(
                label = stringResource(R.string.add_address),
                modifier = Modifier.padding(16.dp),
                value = url,
                onValueChange = onUrlChange,
                placeholder = stringResource(R.string.add_address_placeholder),
                isError = showError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onContinue() }),
            )
        }
        if (showError) {
            Text(
                stringResource(R.string.add_address_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        AppPrimaryButton(
            text = stringResource(R.string.add_continue),
            onClick = onContinue,
            enabled = url.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        Spacer(Modifier.height(28.dp))
        AppSectionHeader(stringResource(R.string.add_offline_section))
        AppCard(contentPadding = PaddingValues(0.dp)) {
            AppListRow(
                title = stringResource(R.string.add_import),
                subtitle = stringResource(R.string.add_import_hint),
                leadingIcon = Icons.Rounded.Description,
                leadingIconBackground = Color(0xFF8E8E93),
                onClick = onImportLocal,
                trailing = {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun AddLoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(24.dp).padding(bottom = LocalOverlayClearance.current),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        Text(
            stringResource(R.string.add_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
