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
import androidx.compose.ui.unit.dp
import com.mejdi14.kmpstartupmvp.ui.components.AppTextField
import com.mejdi14.kmpstartupmvp.ui.userMessage

/**
 * Port of Flutter's `ResetPasswordDialog` (§1.1), shown after the `/reset` deep link signs the
 * user in. Rough edge #4 fixed: the new-password field is obscured (Flutter's was plain text).
 */
@Composable
fun ResetPasswordDialog(
    onSubmit: (password: String, onResult: (Result<Unit>) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Reset Password") },
        text = {
            Column {
                Text("Set New Password")
                Spacer(Modifier.height(24.dp))
                AppTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = "Password",
                    enabled = !loading,
                    error = error,
                    obscure = true,
                    autofillType = ContentType.NewPassword,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancel") } },
        confirmButton = {
            TextButton(
                enabled = !loading,
                onClick = {
                    val invalid = EmailFormValidation.password(password)
                    if (invalid != null) {
                        error = invalid
                        return@TextButton
                    }
                    loading = true
                    onSubmit(password) { result ->
                        loading = false
                        result.fold(
                            onSuccess = { onDismiss() },
                            onFailure = { error = it.userMessage() },
                        )
                    }
                },
            ) { Text("Submit") }
        },
    )
}
