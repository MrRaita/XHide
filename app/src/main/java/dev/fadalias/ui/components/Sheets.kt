package dev.fadalias.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.fadalias.R
import dev.fadalias.data.AppItem
import dev.fadalias.data.AppSort

@Composable
fun sortLabel(sort: AppSort): String = stringResource(
    when (sort) {
        AppSort.CHANGED_FIRST -> R.string.sort_changed_first
        AppSort.NAME_ASC -> R.string.sort_name_asc
        AppSort.NAME_DESC -> R.string.sort_name_desc
        AppSort.SIZE_ASC -> R.string.sort_size_asc
        AppSort.SIZE_DESC -> R.string.sort_size_desc
        AppSort.INSTALLED_NEWEST -> R.string.sort_installed
        AppSort.UPDATED_NEWEST -> R.string.sort_updated
    },
)

/**
 * Sort + "show system apps" in one rounded card that grows out of the button it hangs from (a dropdown anchored to the
 * button, scaling from its corner), instead of a bottom sheet.
 */
@Composable
fun SortMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    current: AppSort,
    onSelect: (AppSort) -> Unit,
    showSystem: Boolean,
    onShowSystemChange: (Boolean) -> Unit,
) {
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.width(300.dp),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = stringResource(R.string.sort_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 6.dp),
            )
            AppSort.entries.forEach { option ->
                val selected = option == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { onSelect(option); onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Text(
                        text = sortLabel(option),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onShowSystemChange(!showSystem) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.show_system_apps), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.show_system_apps_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(checked = showSystem, onCheckedChange = { onShowSystemChange(it) })
            }
        }
    }
}

/** First time the system-app switch is turned on: explains the risk. Cancelling asks again next time; confirming never again. */
@Composable
fun SystemAppsWarning(onCancel: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.sys_warn_title)) },
        text = { Text(stringResource(R.string.sys_warn_body), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.protected_ok)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.protected_cancel)) } },
    )
}

/** Shown when the user taps "customize icon" while the SELinux rule module is missing. */
@Composable
fun ModuleRequiredSheet(onInstall: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShapedIcon(
                icon = Icons.Rounded.Extension,
                background = MaterialTheme.colorScheme.tertiaryContainer,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                size = 88.dp,
                shape = cookieShape(),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.module_required_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.module_required_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onInstall, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.module_install), modifier = Modifier.padding(start = 8.dp))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.not_now))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun RenameSheet(app: AppItem, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(app.customLabel ?: app.label) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.navigationBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.rename_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            // live preview of how the label looks under the icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 56.dp)
                Text(
                    text = text.ifBlank { app.label },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.rename_field)) },
                supportingText = { Text(stringResource(R.string.original_name_fmt, app.label)) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onSave(null); onDismiss() },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(stringResource(R.string.reset)) }
                Button(
                    onClick = { onSave(text.takeIf { it.trim() != app.label }); onDismiss() },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(stringResource(R.string.save)) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun IconPickerSheet(
    app: AppItem,
    onGallery: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.icon_picker_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.icon_picker_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            AppIcon(app, 96.dp)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onGallery, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.icon_from_gallery))
            }
            if (app.iconChanged) {
                TextButton(onClick = { onReset(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.icon_reset))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
