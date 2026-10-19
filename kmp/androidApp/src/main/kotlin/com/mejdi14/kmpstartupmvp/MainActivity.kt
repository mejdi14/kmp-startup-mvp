package com.mejdi14.kmpstartupmvp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mejdi14.kmpstartupmvp.auth.DeepLink
import com.mejdi14.kmpstartupmvp.auth.DeepLinks
import com.mejdi14.kmpstartupmvp.nav.AppNavHost
import com.mejdi14.kmpstartupmvp.ui.theme.AppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
        // A cold start from a deep link; a recreated activity must not replay (and re-exchange) the same code.
        if (savedInstanceState == null) handleDeepLink(intent)
    }

    // singleTask: a deep link while the app is already running arrives here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val url = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        val app = applicationContext as KmpApplication
        when (val link = DeepLinks.parse(url)) {
            DeepLink.StripeReturn -> { app.paymentsStore.onReturned(); return }
            is DeepLink.OpenPayments -> { app.paymentsStore.requestPayments(link.price); return }
            else -> Unit
        }
        lifecycleScope.launch {
            runCatching { app.authStore.handleDeepLink(url) }
                .onFailure { Log.w("DeepLink", "Deep link rejected: ${it.message}") }
        }
    }
}

@Composable
fun App() {
    AppTheme {
        val app = LocalContext.current.applicationContext as KmpApplication
        val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(app.authStore))
        val metadataViewModel: MetadataViewModel =
            viewModel(factory = MetadataViewModel.factory(app.metadataStore))
        val paymentsViewModel: PaymentsViewModel =
            viewModel(factory = PaymentsViewModel.factory(app.paymentsStore))
        AppNavHost(authViewModel, metadataViewModel, paymentsViewModel)
    }
}
