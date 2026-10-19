package com.mejdi14.kmpstartupmvp.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mejdi14.kmpstartupmvp.AuthViewModel
import com.mejdi14.kmpstartupmvp.MetadataViewModel
import com.mejdi14.kmpstartupmvp.auth.AsyncValue
import com.mejdi14.kmpstartupmvp.metadata.UserMetadata
import com.mejdi14.kmpstartupmvp.ui.components.LoadingIndicator

// TODO(product-owner): placeholder carried over from Flutter's HomeScreen (a GitHub README, not a
// pricing page). Replace with the real pricing URL.
internal const val PRICING_URL =
    "https://github.com/devtodollars/mvp-boilerplate/blob/main/flutter/README.md"

private const val APP_TITLE = "DevToDollars"

/** The four-state status sentence (§1.1); null for the loading state, which renders a spinner. */
internal fun statusText(state: AsyncValue<UserMetadata?>): String? = when (state) {
    AsyncValue.Loading -> null
    is AsyncValue.Error -> "Failed to load subscription plan"
    is AsyncValue.Data -> {
        val subscription = state.value?.subscription
        if (subscription == null) {
            "You are not currently subscribed to any plan."
        } else {
            // Flutter interpolated a missing name as "null"; fall back to a generic sentence.
            subscription.prices?.products?.name
                ?.let { "You are currently on the $it plan." }
                ?: "You are currently subscribed to a plan."
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    authViewModel: AuthViewModel,
    metadataViewModel: MetadataViewModel,
    onOpenPayments: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val state by metadataViewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(APP_TITLE) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.inversePrimary,
                ),
                actions = {
                    TextButton(onClick = onOpenPayments) { Text("Payments") }
                    TextButton(onClick = onOpenAccount) { Text("Account") }
                    TextButton(onClick = { authViewModel.signOut() }) { Text("Logout") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val text = statusText(state)
            if (text == null) LoadingIndicator(Modifier) else Text(text)
            TextButton(onClick = { uriHandler.openUri(PRICING_URL) }) { Text("See Pricing") }
        }
    }
}
