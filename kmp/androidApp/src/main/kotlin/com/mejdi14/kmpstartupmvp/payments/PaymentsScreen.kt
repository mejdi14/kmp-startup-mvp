package com.mejdi14.kmpstartupmvp.payments

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mejdi14.kmpstartupmvp.PaymentsViewModel
import com.mejdi14.kmpstartupmvp.ui.components.LoadingIndicator
import kotlinx.coroutines.CancellationException

/** Opens [url] in a Custom Tab (D-005); the Stripe `return_url` deep link brings the user back. */
internal fun openCustomTab(context: Context, url: String) {
    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
}

/**
 * Port of Flutter's `PaymentsScreen`: an interstitial that fetches the Stripe URL and opens it. A null
 * [price] opens the billing portal. When the user returns (deep link or tab dismissed) the screen
 * reports it to the store, which polls for the new subscription, and leaves for Home.
 */
@Composable
fun PaymentsScreen(price: String?, viewModel: PaymentsViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    var started by rememberSaveable { mutableStateOf(false) }
    var launched by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(price) {
        if (started) return@LaunchedEffect // rotation / recomposition must not open a second tab
        started = true
        try {
            val url = viewModel.getStripeUrl(price)
            viewModel.markLaunched(price)
            launched = true
            openCustomTab(context, url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Error retrieving stripe redirect url"
        }
    }

    // Resuming after the tab closed, whether via the return deep link or the user backing out.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (launched) {
            viewModel.onReturned()
            onDone()
        }
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("Failed to open Stripe") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDone) { Text("Ok") } },
        )
    }

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LoadingIndicator(Modifier)
            Spacer(Modifier.height(20.dp))
            Text(
                "You are being redirected to Stripe for payment.\nPlease wait a moment...",
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(40.dp))
            TextButton(onClick = onDone) { Text("Return to Home") }
        }
    }
}
