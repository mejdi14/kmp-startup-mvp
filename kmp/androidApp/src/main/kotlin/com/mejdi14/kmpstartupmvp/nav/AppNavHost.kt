package com.mejdi14.kmpstartupmvp.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mejdi14.kmpstartupmvp.AuthViewModel
import com.mejdi14.kmpstartupmvp.auth.AsyncValue
import com.mejdi14.kmpstartupmvp.auth.AuthScreen

/**
 * Nav graph + D-002 auth gate. The graph is built once; navigation happens only
 * when [AuthViewModel.authGate] changes value. Token refreshes update the session
 * but never change the gate, so nothing here reacts to them (§1.6 bug not ported).
 */
@Composable
fun AppNavHost(authViewModel: AuthViewModel, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val gate by authViewModel.authGate.collectAsState()

    // Fires on gate transitions only (and once on first composition, a no-op
    // when the gate already matches the start destination, e.g. after rotation).
    LaunchedEffect(gate) {
        val target = Routes.forGate(gate)
        if (navController.currentDestination?.route != target) {
            navController.navigate(target) {
                // Clear everything: login/home must not be reachable by Back across the gate.
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.forGate(gate), modifier = modifier) {
        composable(Routes.LOADING) { LoadingScreen() }
        composable(Routes.LOGIN) { AuthScreen(authViewModel) }
        composable(Routes.HOME) { SignedInPlaceholder(authViewModel) }
        // Task 9 (password-reset dialog) and task 11 (payments, ?price=) fill these in; deep links come with task 9.
        composable(Routes.RESET) { PlaceholderScreen("Reset password") }
        composable(Routes.PAYMENTS) { PlaceholderScreen("Payments") }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Stand-in for task 8's HomeScreen: proves the gate, nothing more. */
@Composable
private fun SignedInPlaceholder(authViewModel: AuthViewModel) {
    val state by authViewModel.state.collectAsState()
    val email = (state as? AsyncValue.Data)?.value?.email.orEmpty()
    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Signed in as $email")
            Button(onClick = { authViewModel.signOut() }) { Text("Sign out") }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(title) }
}
