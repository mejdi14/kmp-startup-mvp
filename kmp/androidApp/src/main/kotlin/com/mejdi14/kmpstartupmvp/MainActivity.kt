package com.mejdi14.kmpstartupmvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mejdi14.kmpstartupmvp.nav.AppNavHost

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
    MaterialTheme {
        val store = (LocalContext.current.applicationContext as KmpApplication).authStore
        val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(store))
        AppNavHost(authViewModel)
    }
}
