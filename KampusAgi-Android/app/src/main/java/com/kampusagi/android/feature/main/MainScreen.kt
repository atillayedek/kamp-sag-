package com.kampusagi.android.feature.main

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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.kampusagi.android.core.designsystem.component.BottomBarItem
import com.kampusagi.android.domain.community.CommunityScope
import com.kampusagi.android.feature.chat.ChatScreen
import com.kampusagi.android.feature.chat.ConversationsScreen
import com.kampusagi.android.feature.chat.ConversationsViewModel
import com.kampusagi.android.feature.communities.CommunitiesScreen
import com.kampusagi.android.feature.communities.CreatePostScreen
import com.kampusagi.android.feature.communities.PostDetailScreen
import com.kampusagi.android.feature.matches.MatchesScreen
import com.kampusagi.android.feature.matches.UserProfileScreen
import com.kampusagi.android.feature.requirement.CreateRequirementScreen
import com.kampusagi.android.feature.subscription.PremiumScreen
import com.kampusagi.android.feature.settings.AccountInfoScreen
import com.kampusagi.android.feature.settings.NotificationSettingsScreen
import com.kampusagi.android.feature.settings.PrivacyScreen
import com.kampusagi.android.feature.settings.ProfileScreen
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
fun MainScreen(onSignOut: () -> Unit, inboxViewModel: ConversationsViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val inbox by inboxViewModel.uiState.collectAsStateWithLifecycle()
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
                    items = MainTab.entries.map { tab ->
                        val label = stringResource(tab.labelRes)
                        val unread = if (tab == MainTab.Conversations) inbox.unreadTotal else 0
                        BottomBarItem(
                            label = label,
                            icon = tab.icon,
                            badgeCount = unread,
                            badgeDescription = if (unread > 0) stringResource(R.string.tab_chat_unread_cd, label, unread) else null,
                        )
                    },
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
            composable<MainRoute.Matches> {
                MatchesScreen(
                    onOpenChat = { conversationId -> navController.navigate(MainRoute.Chat(conversationId)) },
                    onOpenProfile = { userId -> navController.navigate(MainRoute.UserProfile(userId)) },
                    onCreateRequirement = { navController.navigate(MainRoute.CreateRequirement) },
                )
            }
            composable<MainRoute.UserProfile> {
                UserProfileScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChat = { conversationId -> navController.navigate(MainRoute.Chat(conversationId)) },
                )
            }
            composable<MainRoute.CreateRequirement> { CreateRequirementScreen() }
            composable<MainRoute.Conversations> {
                ConversationsScreen(
                    viewModel = inboxViewModel,
                    onOpenConversation = { conversationId -> navController.navigate(MainRoute.Chat(conversationId)) },
                )
            }
            composable<MainRoute.Chat> { ChatScreen(onBack = { navController.popBackStack() }) }
            composable<MainRoute.Profile> {
                ProfileScreen(
                    onOpenAccountInfo = { navController.navigate(MainRoute.AccountInfo) },
                    onOpenNotifications = { navController.navigate(MainRoute.NotificationSettings) },
                    onOpenPrivacy = { navController.navigate(MainRoute.Privacy) },
                    onOpenPremium = { navController.navigate(MainRoute.Premium) },
                    onSignOut = onSignOut,
                )
            }
            composable<MainRoute.AccountInfo> { AccountInfoScreen(onBack = { navController.popBackStack() }) }
            composable<MainRoute.NotificationSettings> { NotificationSettingsScreen(onBack = { navController.popBackStack() }) }
            composable<MainRoute.Privacy> { PrivacyScreen(onBack = { navController.popBackStack() }) }
            composable<MainRoute.Premium> { PremiumScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
