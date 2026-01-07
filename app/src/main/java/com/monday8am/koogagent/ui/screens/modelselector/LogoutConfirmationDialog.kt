package com.monday8am.koogagent.ui.screens.modelselector

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.monday8am.koogagent.ui.theme.KoogAgentTheme

@Composable
fun LogoutConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Logout from HuggingFace") },
        text = {
            Text("Are you sure you want to logout? You will need to login again to download gated models.")
        },
        confirmButton = {
            Button(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text("Logout")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun LogoutConfirmationDialogPreview() {
    KoogAgentTheme {
        LogoutConfirmationDialog(
            onDismiss = {},
            onConfirm = {}
        )
    }
}
