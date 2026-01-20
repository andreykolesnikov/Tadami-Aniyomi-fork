package eu.kanade.presentation.entries.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun AuthRequiredDialog(
    onDismissRequest: () -> Unit,
    onSettingsClicked: (() -> Unit)?,
    errorMessage: String,
    sourceName: String,
    isConfigurable: Boolean,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            if (isConfigurable) {
                TextButton(
                    onClick = {
                        onSettingsClicked?.invoke()
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.source_settings))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.login_title, sourceName))
        },
        text = {
            Text(text = errorMessage)
        },
    )
}
