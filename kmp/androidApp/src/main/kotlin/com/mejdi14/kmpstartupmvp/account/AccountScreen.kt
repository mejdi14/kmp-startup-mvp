package com.mejdi14.kmpstartupmvp.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mejdi14.kmpstartupmvp.MetadataViewModel
import com.mejdi14.kmpstartupmvp.auth.AsyncValue
import com.mejdi14.kmpstartupmvp.metadata.ProfileValidation
import com.mejdi14.kmpstartupmvp.ui.components.AppTextField
import com.mejdi14.kmpstartupmvp.ui.components.ErrorState
import com.mejdi14.kmpstartupmvp.ui.components.ErrorText
import com.mejdi14.kmpstartupmvp.ui.components.LoadingIndicator
import com.mejdi14.kmpstartupmvp.ui.components.PrimaryButton

/** Shown for delete-account: the fixed backend has no self-service deletion path (PORTING.md §2.1/§2.3/§2.4). */
internal const val DELETE_BLOCKED_MESSAGE =
    "Account deletion isn't available in the app yet. Please contact support to delete your account."

/** Profile editing (`full_name`/`avatar_url`) plus the delete-account entry point (task 9). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(metadataViewModel: MetadataViewModel, onBack: () -> Unit) {
    val state by metadataViewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).fillMaxSize()
        when (val s = state) {
            AsyncValue.Loading -> LoadingIndicator(modifier)
            is AsyncValue.Error -> ErrorState("Failed to load profile", modifier, onRetry = metadataViewModel::refresh)
            is AsyncValue.Data -> {
                val user = s.value
                if (user == null) {
                    ErrorState("Failed to load profile", modifier, onRetry = metadataViewModel::refresh)
                } else {
                    ProfileForm(
                        initialName = user.fullName.orEmpty(),
                        initialAvatar = user.avatarUrl.orEmpty(),
                        onSave = metadataViewModel::updateProfile,
                        modifier = modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileForm(
    initialName: String,
    initialAvatar: String,
    onSave: (String, String, (Result<Unit>) -> Unit) -> Unit,
    modifier: Modifier,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var avatar by rememberSaveable { mutableStateOf(initialAvatar) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by rememberSaveable { mutableStateOf(false) }
    var showDeleteInfo by rememberSaveable { mutableStateOf(false) }

    // Saving refetches, which re-enters this composable with the stored values; keep edits meanwhile.
    LaunchedEffect(initialName, initialAvatar) {
        if (!saving) {
            name = initialName
            avatar = initialAvatar
        }
    }

    val nameError = ProfileValidation.fullName(name).takeIf { submitted }
    val avatarError = ProfileValidation.avatarUrl(avatar).takeIf { submitted }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Profile", style = MaterialTheme.typography.titleLarge)
        AppTextField(
            value = name,
            onValueChange = { name = it; message = null },
            label = "Full name",
            enabled = !saving,
            error = nameError,
        )
        AppTextField(
            value = avatar,
            onValueChange = { avatar = it; message = null },
            label = "Avatar URL",
            enabled = !saving,
            error = avatarError,
            keyboardType = KeyboardType.Uri,
        )
        message?.let { if (failed) ErrorText(it) else Text(it) }
        PrimaryButton(
            text = "Save",
            enabled = !saving,
            onClick = {
                submitted = true
                if (ProfileValidation.fullName(name) != null || ProfileValidation.avatarUrl(avatar) != null) return@PrimaryButton
                saving = true
                message = null
                onSave(name, avatar) { result ->
                    saving = false
                    failed = result.isFailure
                    message = result.fold(
                        onSuccess = { "Profile saved" },
                        onFailure = { it.message ?: "Failed to save profile" },
                    )
                }
            },
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Text("Delete account", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { showDeleteInfo = true }) {
            Text("Delete my account", color = MaterialTheme.colorScheme.error)
        }
    }

    if (showDeleteInfo) {
        AlertDialog(
            onDismissRequest = { showDeleteInfo = false },
            title = { Text("Delete account") },
            text = { Text(DELETE_BLOCKED_MESSAGE) },
            confirmButton = { TextButton(onClick = { showDeleteInfo = false }) { Text("Ok") } },
        )
    }
}
