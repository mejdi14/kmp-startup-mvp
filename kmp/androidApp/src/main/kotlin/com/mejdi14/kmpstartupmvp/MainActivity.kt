package com.mejdi14.kmpstartupmvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mejdi14.kmpstartupmvp.shadow.ShadowApp
import com.mejdi14.kmpstartupmvp.shadow.ShadowTheme
import com.mejdi14.kmpstartupmvp.shadow.ShadowViewModel

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
    ShadowTheme {
        val app = LocalContext.current.applicationContext as KmpApplication
        val shadowViewModel: ShadowViewModel = viewModel(factory = ShadowViewModel.factory(app, app.shadowStore))
        ShadowApp(shadowViewModel)
    }
}
