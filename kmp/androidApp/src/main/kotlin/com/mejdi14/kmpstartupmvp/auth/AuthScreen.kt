package com.mejdi14.kmpstartupmvp.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mejdi14.kmpstartupmvp.AuthViewModel

/**
 * Port of Flutter's `AuthScreen` + `EmailForm` (§1.1). Email/password only; SSO
 * buttons (task 10) and forgot-password (task 9) are not here yet. The Flutter
 * "or continue with email" reveal only existed to sit under the SSO buttons, so
 * the form is shown directly. Navigation on success is the auth gate's job (task 5).
 */
@Composable
fun AuthScreen(viewModel: AuthViewModel, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            EmailForm(viewModel)
        }
    }
}

@Composable
private fun EmailForm(viewModel: AuthViewModel) {
    // Flutter defaults to sign-up.
    var action by rememberSaveable { mutableStateOf(AuthAction.SignUp) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    // Field errors only appear after a submit attempt, like Flutter's Form.validate().
    var submitted by rememberSaveable { mutableStateOf(false) }
    var showCheckEmail by rememberSaveable { mutableStateOf(false) }

    val isSignUp = action == AuthAction.SignUp
    val emailError = EmailFormValidation.email(email).takeIf { submitted }
    val passwordError = EmailFormValidation.password(password).takeIf { submitted }
    val confirmError = EmailFormValidation.confirmPassword(password, confirm).takeIf { submitted && isSignUp }

    fun submit() {
        submitted = true
        val valid = EmailFormValidation.email(email) == null &&
            EmailFormValidation.password(password) == null &&
            (!isSignUp || EmailFormValidation.confirmPassword(password, confirm) == null)
        if (!valid) return
        loading = true
        errorMessage = ""
        val onResult: (Result<Unit>) -> Unit = { result ->
            loading = false
            result.onFailure { errorMessage = it.message ?: "Something went wrong" }
        }
        if (isSignUp) {
            viewModel.signUp(email, password) { result ->
                onResult(result)
                if (result.isSuccess) {
                    // Email confirmation is on (§2.8): no session, so flip to login + prompt.
                    action = AuthAction.SignIn
                    submitted = false
                    showCheckEmail = true
                }
            }
        } else {
            viewModel.signInWithPassword(email, password, onResult)
        }
    }

    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Text(if (isSignUp) "Create an account" else "Login", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            enabled = !loading,
            singleLine = true,
            isError = emailError != null,
            supportingText = emailError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.EmailAddress },
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            enabled = !loading,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = passwordError != null,
            supportingText = passwordError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Password },
        )
        if (isSignUp) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text("Confirm Password") },
                enabled = !loading,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = confirmError != null,
                supportingText = confirmError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Password },
            )
        }
        if (errorMessage.isNotEmpty()) {
            Text(
                errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = {
                action = if (isSignUp) AuthAction.SignIn else AuthAction.SignUp
                errorMessage = ""
                submitted = false
            }) {
                Text(if (isSignUp) "Have an account?" else "Don't have an account?")
            }
        }
        Button(onClick = ::submit, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (isSignUp) "Create Account" else "Login",
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    }

    if (showCheckEmail) {
        AlertDialog(
            onDismissRequest = { showCheckEmail = false },
            title = { Text("Check your Email!") },
            text = { Text("We sent an email from hi@devtodollars.com to verify your email") },
            confirmButton = { TextButton(onClick = { showCheckEmail = false }) { Text("Ok") } },
        )
    }
}
