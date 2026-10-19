package com.mejdi14.kmpstartupmvp.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mejdi14.kmpstartupmvp.ui.components.AppTextField

/** Port of Flutter's `RecoverPasswordDialog` (§1.1): request a reset email for [initialEmail]. */
@Composable
fun RecoverPasswordDialog(
    initialEmail: String,
    onSend: (email: String, onResult: (Result<Unit>) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Recover Password") },
        text = {
            Column {
                Text("Get instructions sent to this email that explain how to reset your password")
                Spacer(Modifier.height(24.dp))
                AppTextField(
                    value = email,
                    onValueChange = { email = it; error = null },
                    label = "Email",
                    enabled = !loading,
                    error = error,
                    keyboardType = KeyboardType.Email,
                    autofillType = ContentType.EmailAddress,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancel") } },
        confirmButton = {
            TextButton(
                enabled = !loading,
                onClick = {
                    val invalid = EmailFormValidation.email(email.trim())
                    if (invalid != null) {
                        error = invalid
                        return@TextButton
                    }
                    loading = true
                    onSend(email.trim()) { result ->
                        loading = false
                        result.fold(
                            onSuccess = { onDismiss() },
                            onFailure = { error = it.message ?: "Something went wrong" },
                        )
                    }
                },
            ) { Text("Send") }
        },
    )
}
