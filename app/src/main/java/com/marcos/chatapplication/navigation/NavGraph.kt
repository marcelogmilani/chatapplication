package com.marcos.chatapplication.navigation

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.marcos.chatapplication.ui.screens.ChatScreen
import com.marcos.chatapplication.ui.screens.CreateGroupScreen
import com.marcos.chatapplication.ui.screens.FinalizeGroupScreen
import com.marcos.chatapplication.ui.screens.HomeScreen
import com.marcos.chatapplication.ui.screens.LoginScreen
import com.marcos.chatapplication.ui.screens.OtherUserProfileScreen
import com.marcos.chatapplication.ui.screens.ProfileScreen
import com.marcos.chatapplication.ui.screens.RegistrationScreen
import com.marcos.chatapplication.ui.screens.UserSearchScreen
import com.marcos.chatapplication.ui.viewmodel.LoginViewModel
import com.marcos.chatapplication.ui.viewmodel.RegistrationViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login_screen")
    object Home : Screen("home_screen")
    object Profile : Screen("profile_screen") // Seu perfil (logado)
    object Chat : Screen("chat_screen/{conversationId}") {
        fun createRoute(conversationId: String) = "chat_screen/$conversationId"
    }
    object UserSearch : Screen("user_search_screen")
    object Registration : Screen("registration_screen")

    object OtherUserProfile : Screen("other_user_profile_screen/{userId}") {
        fun createRoute(userId: String) = "other_user_profile_screen/$userId"
    }
    object CreateGroup : Screen("create_group_screen")
    object FinalizeGroup : Screen("finalize_group_screen/{memberIds}") {
        fun createRoute(memberIds: List<String>): String {
            // Juntamos a lista de IDs numa única string separada por vírgulas
            val ids = memberIds.joinToString(",")
            return "finalize_group_screen/$ids"
        }
    }
}

@Composable
fun NavGraph(navController: NavHostController, startDestination: String) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(route = Screen.Login.route) {
            val loginViewModel: LoginViewModel = hiltViewModel()
            val uiState by loginViewModel.uiState.collectAsStateWithLifecycle()

            LoginScreen(
                uiState = uiState,
                onSendCodeClick = loginViewModel::startPhoneNumberVerification,
                onSignInClick = loginViewModel::signInWithCode,
                onSignUpClick = {
                    navController.navigate(Screen.Registration.route)
                },
                onErrorMessageShown = loginViewModel::onErrorMessageShown,
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                    loginViewModel.onLoginHandled()
                }
            )
        }

        composable(route = Screen.Registration.route) {
            val registrationViewModel: RegistrationViewModel = hiltViewModel()
            val uiState by registrationViewModel.uiState.collectAsStateWithLifecycle()

            RegistrationScreen(
                uiState = uiState,
                onSendCodeClick = registrationViewModel::startPhoneNumberVerification,
                onRegisterClick = registrationViewModel::signInWithCode,
                onRegistrationSuccess = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Registration.route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                    registrationViewModel.onRegistrationHandled()
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onErrorMessageShown = registrationViewModel::onErrorMessageShown
            )

        }

        composable(route = Screen.Profile.route) {
            // Este é o perfil do usuário LOGADO
            ProfileScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSignOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) {
            ChatScreen(navController = navController)
        }

        composable(route = Screen.Home.route) {
            HomeScreen(
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) }, // Perfil do usuário logado
                onConversationClick = { conversationId ->
                    navController.navigate(Screen.Chat.createRoute(conversationId))
                },
                onNewChatClick = {
                    navController.navigate(Screen.UserSearch.route)
                }
            )
        }

        composable(route = Screen.UserSearch.route) {
            UserSearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { conversationId ->
                    navController.navigate(Screen.Chat.createRoute(conversationId)) {
                        popUpTo(Screen.UserSearch.route) {
                            inclusive = true
                        }
                    }
                },
                onNewGroupClick = {
                    navController.navigate(Screen.CreateGroup.route)
                }
            )
        }

        composable(route = Screen.CreateGroup.route) {
            CreateGroupScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNextClick = { selectedContactIds ->
                    navController.navigate(Screen.FinalizeGroup.createRoute(selectedContactIds))
                }
            )
        }

        composable(
            route = Screen.FinalizeGroup.route,
            arguments = listOf(navArgument("memberIds") { type = NavType.StringType })
        ) {
            FinalizeGroupScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onGroupCreated = { groupId ->
                    navController.navigate(Screen.Chat.createRoute(groupId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(
            route = Screen.OtherUserProfile.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) {
            OtherUserProfileScreen(navController = navController)
        }
    }
}
