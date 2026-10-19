package com.mejdi14.kmpstartupmvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mejdi14.kmpstartupmvp.nav.AppNavHost
import com.mejdi14.kmpstartupmvp.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
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
        AppNavHost(authViewModel, metadataViewModel)
    }
}
