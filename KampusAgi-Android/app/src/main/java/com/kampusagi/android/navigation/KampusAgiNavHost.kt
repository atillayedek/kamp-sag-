package com.kampusagi.android.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.Dimens
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.appText
import com.kampusagi.android.core.designsystem.component.ErrorStateView
import com.kampusagi.android.core.designsystem.component.TextDangerButton
import com.kampusagi.android.core.ui.toUiText
import com.kampusagi.android.feature.auth.login.LoginScreen
import com.kampusagi.android.feature.auth.register.RegisterScreen
import com.kampusagi.android.feature.auth.welcome.WelcomeScreen
import com.kampusagi.android.feature.main.MainScreen
import com.kampusagi.android.feature.verification.PendingReviewScreen
import com.kampusagi.android.feature.verification.RejectedScreen

internal fun routeFor(state: UserSessionState): KampusAgiRoute? = when (state) {
    UserSessionState.Loading, is UserSessionState.LoadFailed -> null
    UserSessionState.Unauthenticated -> KampusAgiRoute.Welcome
    is UserSessionState.Onboarding -> KampusAgiRoute.Register(startStep = state.startStep)
    UserSessionState.PendingReview -> KampusAgiRoute.PendingReview
    is UserSessionState.Rejected -> KampusAgiRoute.Rejected(state.reason)
    UserSessionState.Approved -> KampusAgiRoute.Approved
}

@Composable
fun KampusAgiNavHost(rootViewModel: RootViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val sessionState by rootViewModel.sessionState.collectAsStateWithLifecycle()

    // İlk durum çözülene kadar NavHost hiç kurulmaz -> açık oturumlu kullanıcı Welcome'ı görmez.
    if (sessionState is UserSessionState.Loading) {
        SplashContent()
        return
    }

    val startRoute = remember { routeFor(sessionState) ?: KampusAgiRoute.Welcome }
    var currentRoute by remember { mutableStateOf(startRoute) }

    // Durum değişince doğru ekrana geç ve geri yığınını tamamen temizle.
    LaunchedEffect(sessionState) {
        val target = routeFor(sessionState) ?: return@LaunchedEffect
        if (target != currentRoute) {
            currentRoute = target
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = startRoute) {
            composable<KampusAgiRoute.Welcome> {
                WelcomeScreen(
                    onLoginClick = { navController.navigate(KampusAgiRoute.Login) },
                    onRegisterClick = { navController.navigate(KampusAgiRoute.Register()) },
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
                MainScreen(onSignOut = rootViewModel::signOut)
            }
        }

        (sessionState as? UserSessionState.LoadFailed)?.let { failed ->
            LoadFailedOverlay(
                message = failed.error.toUiText().asString(),
                onRetry = rootViewModel::retry,
                onSignOut = rootViewModel::signOut,
            )
        }
    }
}

@Composable
private fun SplashContent() {
    val loading = stringResource(R.string.common_loading)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColors.appBg)
            .semantics { contentDescription = loading },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.appColors.primary)
    }
}

@Composable
private fun LoadFailedOverlay(message: String, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColors.appBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ErrorStateView(message = message, onRetry = onRetry)
            TextDangerButton(
                text = stringResource(R.string.logout_button),
                onClick = onSignOut,
                modifier = Modifier.padding(horizontal = Dimens.screenPaddingH),
            )
        }
    }
}
