package com.mejdi14.kmpstartupmvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mejdi14.kmpstartupmvp.auth.AuthScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}

// Interim shell: the login screen is the only destination until task 5 builds the real graph + auth gate.
@Composable
fun App() {
    MaterialTheme {
        val store = (LocalContext.current.applicationContext as KmpApplication).authStore
        val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(store))
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "login") {
            composable("login") { AuthScreen(authViewModel) }
        }
    }
}
