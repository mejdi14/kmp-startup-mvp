package com.mejdi14.kmpstartupmvp.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.mejdi14.kmpstartupmvp.ui.components.ErrorText

/**
 * "Save your account" (D-010): collects the credentials that turn the current guest session into a
 * permanent account. Same rules as the sign-up form ([EmailFormValidation]) and the same
 * dialog shape as [RecoverPasswordDialog]; the outcome goes to [onSaved] because
 * [AccountConversion.ConfirmationRequired] needs a different sentence than a finished conversion.
 */
@Composable
fun SaveAccountDialog(
    onSave: (email: String, password: String, onResult: (Result<AccountConversion>) -> Unit) -> Unit,
    onSaved: (AccountConversion) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Save your account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Pick an email and password. You keep everything from this session — same account, just recoverable.")
                AppTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = "" },
                    label = "Email",
                    enabled = !loading,
                    error = EmailFormValidation.email(email.trim()).takeIf { submitted },
                    keyboardType = KeyboardType.Email,
                    autofillType = ContentType.EmailAddress,
                )
                AppTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = "" },
                    label = "Password",
                    enabled = !loading,
                    error = EmailFormValidation.password(password).takeIf { submitted },
                    obscure = true,
                    autofillType = ContentType.NewPassword,
                )
                AppTextField(
                    value = confirm,
                    onValueChange = { confirm = it; errorMessage = "" },
                    label = "Confirm Password",
                    enabled = !loading,
                    error = EmailFormValidation.confirmPassword(password, confirm).takeIf { submitted },
                    obscure = true,
                    autofillType = ContentType.NewPassword,
                )
                if (errorMessage.isNotEmpty()) ErrorText(errorMessage, Modifier)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancel") } },
        confirmButton = {
            TextButton(
                enabled = !loading,
                onClick = {
                    submitted = true
                    val trimmed = email.trim()
                    val valid = EmailFormValidation.email(trimmed) == null &&
                        EmailFormValidation.password(password) == null &&
                        EmailFormValidation.confirmPassword(password, confirm) == null
                    if (!valid) return@TextButton
                    loading = true
                    errorMessage = ""
                    onSave(trimmed, password) { result ->
                        loading = false
                        result.fold(
                            onSuccess = onSaved,
                            // A taken address carries its own explanation (it cannot be merged into).
                            onFailure = { errorMessage = it.message ?: "Could not save your account" },
                        )
                    }
                },
            ) { Text("Save") }
        },
    )
}
