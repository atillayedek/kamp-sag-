package com.kampusagi.android.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.KampusAgiSpacing
import com.kampusagi.android.feature.auth.login.LoginScreen
import com.kampusagi.android.feature.auth.register.RegisterScreen
import com.kampusagi.android.feature.auth.welcome.WelcomeScreen
import com.kampusagi.android.feature.verification.PendingReviewScreen
import com.kampusagi.android.feature.verification.RejectedScreen

@Composable
fun KampusAgiNavHost(rootViewModel: RootViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val sessionState by rootViewModel.sessionState.collectAsStateWithLifecycle()

    // Durum değiştiğinde doğru başlangıç ekranına geç, geri yığınını
    // TAMAMEN temizle (popUpTo inclusive) — kullanıcının tasarım mesajındaki
    // "geçişlerde geri yığını temizlensin" talimatı.
    LaunchedEffect(sessionState) {
        val route: KampusAgiRoute = when (val state = sessionState) {
            UserSessionState.Loading -> return@LaunchedEffect
            UserSessionState.Unauthenticated -> KampusAgiRoute.Welcome
            UserSessionState.PendingReview -> KampusAgiRoute.PendingReview
            UserSessionState.Approved -> KampusAgiRoute.Approved
            is UserSessionState.Rejected -> KampusAgiRoute.Rejected(state.reason)
        }
        navController.navigate(route) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = KampusAgiRoute.Welcome) {
        composable<KampusAgiRoute.Welcome> {
            WelcomeScreen(
                onLoginClick = { navController.navigate(KampusAgiRoute.Login) },
                onRegisterClick = { navController.navigate(KampusAgiRoute.Register) },
            )
        }
        composable<KampusAgiRoute.Login> {
            LoginScreen(onBackClick = { navController.popBackStack() })
        }
        composable<KampusAgiRoute.Register> {
            RegisterScreen(onBackClick = { navController.popBackStack() })
        }
        composable<KampusAgiRoute.PendingReview> {
            PendingReviewScreen()
        }
        composable<KampusAgiRoute.Rejected> { backStackEntry ->
            val route: KampusAgiRoute.Rejected = backStackEntry.toRoute()
            RejectedScreen(reason = route.reason)
        }
        composable<KampusAgiRoute.Approved> {
            ApprovedPlaceholderScreen()
        }
    }
}

/**
 * Ana uygulama (Topluluk/İhtiyaç & AI/Sıralama/Mesajlar/Profil) bu tasarım
 * turunda İSTENMEDİ — kullanıcının Android tasarım mesajı yalnızca kimlik
 * doğrulama + öğrenci doğrulama akışını kapsıyordu. Bu, uydurulmuş bir ana
 * ekran DEĞİL, yalnızca Approved durumu için gerekli asgari bir yer
 * tutucudur (bkz. memory-bank/Memory_Bank.md).
 */
@Composable
private fun ApprovedPlaceholderScreen() {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(KampusAgiSpacing.screenMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(stringResource(R.string.approved_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.approved_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
