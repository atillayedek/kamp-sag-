package com.kampusagi.android.feature.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kampusagi.android.R
import com.kampusagi.android.core.designsystem.appColors
import com.kampusagi.android.core.designsystem.component.AppBottomBar
import com.kampusagi.android.core.designsystem.component.AppTopBar
import com.kampusagi.android.core.designsystem.component.BottomBarItem
import com.kampusagi.android.core.designsystem.component.EmptyStateView
import com.kampusagi.android.core.designsystem.component.TextDangerButton
import com.kampusagi.android.domain.community.CommunityScope
import com.kampusagi.android.feature.communities.CommunitiesScreen
import com.kampusagi.android.feature.communities.CreatePostScreen
import com.kampusagi.android.feature.communities.PostDetailScreen
import com.kampusagi.android.navigation.MainRoute

private enum class MainTab(val route: MainRoute, val labelRes: Int, val icon: ImageVector) {
    Communities(MainRoute.Communities, R.string.tab_communities, Icons.Filled.Groups),
    Matches(MainRoute.Matches, R.string.tab_matches, Icons.Filled.Favorite),
    CreateRequirement(MainRoute.CreateRequirement, R.string.tab_requirement, Icons.Filled.AddCircle),
    Conversations(MainRoute.Conversations, R.string.tab_chat, Icons.AutoMirrored.Filled.Chat),
    Profile(MainRoute.Profile, R.string.tab_profile, Icons.Filled.Person),
}

/**
 * Onaylı kullanıcının ana uygulaması: Topluluklar · Eşleşmeler · İhtiyaç · Sohbet · Profil.
 * Alt çubuk yalnızca sekme ekranlarında görünür; ayrıntı ekranları (gönderi oluştur, yorumlar…) tam ekrandır.
 */
@Composable
fun MainScreen(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTab = MainTab.entries.firstOrNull { destination?.hasRoute(it.route::class) == true }

    Scaffold(
        containerColor = MaterialTheme.appColors.appBg,
        // Üst sistem çubuğu payını her ekranın kendi AppTopBar'ı uygular (çift boşluk olmasın).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentTab != null) {
                AppBottomBar(
                    items = MainTab.entries.map { BottomBarItem(stringResource(it.labelRes), it.icon) },
                    selectedIndex = currentTab.ordinal,
                    onSelect = { index ->
                        val target = MainTab.entries[index].route
                        navController.navigate(target) {
                            popUpTo<MainRoute.Communities> { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MainRoute.Communities,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            composable<MainRoute.Communities> {
                CommunitiesScreen(
                    onCreatePost = { scope: CommunityScope -> navController.navigate(MainRoute.CreatePost(scope.name)) },
                    onOpenPost = { postId -> navController.navigate(MainRoute.PostDetail(postId)) },
                )
            }
            composable<MainRoute.CreatePost> {
                CreatePostScreen(onBack = { navController.popBackStack() })
            }
            composable<MainRoute.PostDetail> {
                PostDetailScreen(onBack = { navController.popBackStack() })
            }
            composable<MainRoute.Matches> { TabInProgress(R.string.tab_matches) }
            composable<MainRoute.CreateRequirement> { TabInProgress(R.string.requirement_title) }
            composable<MainRoute.Conversations> { TabInProgress(R.string.tab_chat) }
            composable<MainRoute.Profile> { ProfileTemporary(onSignOut) }
        }
    }
}

/** GEÇİCİ (Görev 9-11 ile gerçek ekranlarla değiştirilecek). */
@Composable
private fun TabInProgress(titleRes: Int) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(titleRes))
        EmptyStateView(title = stringResource(titleRes), message = stringResource(R.string.common_loading))
    }
}

/** GEÇİCİ (Görev 11 Profil/Ayarlar ile değiştirilecek): oturumu kapatabilmek için. */
@Composable
private fun ProfileTemporary(onSignOut: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.tab_profile))
        TextDangerButton(
            text = stringResource(R.string.logout_button),
            onClick = onSignOut,
            modifier = Modifier.padding(20.dp),
        )
    }
}
