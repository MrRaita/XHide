package dev.fadalias.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.fadalias.R

/** What the user is about to do that deserves a warning. */
enum class RiskKind { SYSTEM, SELF, SELF_HIDE }

/** Warning before changing a system app or Fadalias itself. Confirming continues; [onConfirm] gets the "don't show again" choice. */
@Composable
fun ProtectedDialog(kind: RiskKind, onCancel: () -> Unit, onConfirm: (dontShowAgain: Boolean) -> Unit) {
    var dontShow by remember { mutableStateOf(false) }
    val title = when (kind) {
        RiskKind.SYSTEM -> R.string.protected_title
        RiskKind.SELF -> R.string.self_title
        RiskKind.SELF_HIDE -> R.string.selfhide_title
    }
    val body = when (kind) {
        RiskKind.SYSTEM -> R.string.protected_body
        RiskKind.SELF -> R.string.self_body
        RiskKind.SELF_HIDE -> R.string.selfhide_body
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).clickable { dontShow = !dontShow },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = dontShow, onCheckedChange = { dontShow = it })
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.protected_dont_show), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(dontShow) }) { Text(stringResource(R.string.protected_ok)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.protected_cancel)) } },
    )
}
