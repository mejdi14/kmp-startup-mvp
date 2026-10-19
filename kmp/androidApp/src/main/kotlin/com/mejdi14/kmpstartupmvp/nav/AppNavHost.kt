package com.mejdi14.kmpstartupmvp.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mejdi14.kmpstartupmvp.AuthViewModel
import com.mejdi14.kmpstartupmvp.MetadataViewModel
import com.mejdi14.kmpstartupmvp.home.HomeScreen
import com.mejdi14.kmpstartupmvp.auth.AuthScreen
import com.mejdi14.kmpstartupmvp.ui.components.LoadingIndicator

/**
 * Nav graph + D-002 auth gate. The graph is built once; navigation happens only
 * when [AuthViewModel.authGate] changes value. Token refreshes update the session
 * but never change the gate, so nothing here reacts to them (§1.6 bug not ported).
 */
@Composable
fun AppNavHost(authViewModel: AuthViewModel, metadataViewModel: MetadataViewModel, modifier: Modifier = Modifier) {
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
        composable(Routes.LOADING) { LoadingIndicator() }
        composable(Routes.LOGIN) { AuthScreen(authViewModel) }
        composable(Routes.HOME) {
            HomeScreen(
                authViewModel = authViewModel,
                metadataViewModel = metadataViewModel,
                onOpenPayments = { navController.navigate(Routes.PAYMENTS) },
            )
        }
        // Task 9 (password-reset dialog) and task 11 (payments, ?price=) fill these in; deep links come with task 9.
        composable(Routes.RESET) { PlaceholderScreen("Reset password") }
        composable(Routes.PAYMENTS) { PlaceholderScreen("Payments") }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(title) }
}
