package com.marcos.chatapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.marcos.chatapplication.navigation.NavGraph
import com.marcos.chatapplication.navigation.Screen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.marcos.chatapplication.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("Permissions", "Permissão de notificações concedida.")
        } else {
            Log.w("Permissions", "Permissão de notificações negada.")
        }
    }

    private fun askNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        askNotificationPermission()

        installSplashScreen().apply {
            setKeepOnScreenCondition {
                viewModel.authState.value.isInitialLoading
            }
        }

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                val navController = rememberNavController()
                val authState by viewModel.authState.collectAsStateWithLifecycle()

                LaunchedEffect(authState.user) {
                    if (authState.user == null && !authState.isInitialLoading) {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(navController.graph.id) {
                                inclusive = true
                            }
                        }
                    }
                }

                val startDestination = if (authState.user != null) {
                    Screen.Home.route
                } else {
                    Screen.Login.route
                }

                if (!authState.isInitialLoading) {
                    NavGraph(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }
}