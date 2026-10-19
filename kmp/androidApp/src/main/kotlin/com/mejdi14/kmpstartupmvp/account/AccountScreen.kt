package com.mejdi14.kmpstartupmvp.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.mejdi14.kmpstartupmvp.AuthViewModel
import com.mejdi14.kmpstartupmvp.MetadataViewModel
import com.mejdi14.kmpstartupmvp.auth.AccountConversion
import com.mejdi14.kmpstartupmvp.auth.AsyncValue
import com.mejdi14.kmpstartupmvp.auth.SaveAccountDialog
import com.mejdi14.kmpstartupmvp.metadata.ProfileValidation
import com.mejdi14.kmpstartupmvp.ui.components.AppTextField
import com.mejdi14.kmpstartupmvp.ui.components.ErrorState
import com.mejdi14.kmpstartupmvp.ui.components.ErrorText
import com.mejdi14.kmpstartupmvp.ui.components.LoadingIndicator
import com.mejdi14.kmpstartupmvp.ui.components.PrimaryButton
import com.mejdi14.kmpstartupmvp.ui.userMessage

/** Why a guest should convert (D-011); the sign-out warning in `HomeScreen` says the other half. */
internal const val GUEST_ACCOUNT_MESSAGE =
    "You're signed in as a guest, so this account only exists on this device and can't be " +
        "recovered. Add an email and password to keep it — nothing you've done is lost."

/** Shown for delete-account: the fixed backend has no self-service deletion path (PORTING.md §2.1/§2.3/§2.4). */
internal const val DELETE_BLOCKED_MESSAGE =
    "Account deletion isn't available in the app yet. Please contact support to delete your account."

/**
 * Profile editing (`full_name`/`avatar_url`) plus the delete-account entry point (task 9), and for a
 * guest session the "Save your account" conversion (D-011). The guest banner sits above the profile
 * state, not inside it: converting is what a guest came here for and must not depend on the
 * `users` row having loaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    authViewModel: AuthViewModel,
    metadataViewModel: MetadataViewModel,
    onBack: () -> Unit,
) {
    val state by metadataViewModel.state.collectAsStateWithLifecycle()
    val isGuest by authViewModel.isGuest.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (isGuest) SaveAccountSection(authViewModel)
            val modifier = Modifier.weight(1f).fillMaxWidth()
            when (val s = state) {
                AsyncValue.Loading -> LoadingIndicator(modifier)
                is AsyncValue.Error ->
                    ErrorState("Failed to load profile", modifier, onRetry = metadataViewModel::refresh)
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
}

/**
 * Guest-only conversion entry point (D-011). It stays on screen while a confirmation is pending,
 * because until the link is followed the address is not yet the user's and the account is still
 * unrecoverable.
 */
@Composable
private fun SaveAccountSection(authViewModel: AuthViewModel) {
    var showForm by rememberSaveable { mutableStateOf(false) }
    var pendingEmail by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Save your account", style = MaterialTheme.typography.titleMedium)
        Text(GUEST_ACCOUNT_MESSAGE, style = MaterialTheme.typography.bodyMedium)
        PrimaryButton(text = "Save your account", onClick = { showForm = true })
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }

    if (showForm) {
        SaveAccountDialog(
            onSave = authViewModel::saveGuestAccount,
            onSaved = { conversion ->
                showForm = false
                // Complete: `isGuest` has already gone false and this whole section disappears.
                pendingEmail = (conversion as? AccountConversion.ConfirmationRequired)?.email
            },
            onDismiss = { showForm = false },
        )
    }

    pendingEmail?.let { email ->
        AlertDialog(
            onDismissRequest = { pendingEmail = null },
            title = { Text("Confirm your email") },
            text = {
                Text(
                    "Your password is set. Open the link we sent to $email to finish — until then this " +
                        "is still a guest session, so don't sign out.",
                )
            },
            confirmButton = { TextButton(onClick = { pendingEmail = null }) { Text("Ok") } },
        )
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
                        onFailure = { it.userMessage() },
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
