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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
import com.marcos.chatapplication.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
        // A partir do Android 13 (API 33), a permissão POST_NOTIFICATIONS é necessária
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // Se um usuário estiver logado (verificação interna no ViewModel), define como offline
                viewModel.setUserOffline()
                Log.d("MainActivityLifecycle", "onStop: setUserOffline chamado")
            }

       /*     override fun onStart(owner: LifecycleOwner) {
                // Esta é uma alternativa a whenStarted se whenStarted apresentar problemas
                // ou se quisermos ser mais explícitos sobre o onStart.
                // No entanto, o bloco whenStarted abaixo já lida com o estado "started".
                // Se o usuário estiver logado, ele será definido como online pelo coletor de authState.
                Log.d("MainActivityLifecycle", "onStart: Verificando estado do usuário")
                if (viewModel.authState.value.user != null) {
                    viewModel.setUserOnline()
                    Log.d("MainActivityLifecycle", "onStart: Usuário logado, setUserOnline chamado")
                }
            }*/
        })

        lifecycleScope.launch {
            // whenStarted garante que a coleta e a ação só ocorram quando o lifecycle está ATIVO
            // e o usuário está logado. Pausa quando em ON_STOP.
            lifecycle.whenStarted { // Esta linha deve funcionar com o import correto
                Log.d("MainActivityLifecycle", "whenStarted: Bloco iniciado, coletando authState")
                viewModel.authState.collectLatest { authState ->
                    Log.d("MainActivityLifecycle", "whenStarted: authState coletado: user=${authState.user?.uid}, initialLoading=${authState.isInitialLoading}")
                    if (authState.user != null && !authState.isInitialLoading) { // Adicionada checagem de !isInitialLoading aqui também
                        viewModel.setUserOnline()
                        Log.d("MainActivityLifecycle", "whenStarted: Usuário logado e não carregando, setUserOnline chamado")
                    }
                    // Não precisa de um 'else' para setUserOffline aqui,
                    // pois o onStop do LifecycleObserver cuidará disso.
                }
            }
        }


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

                LaunchedEffect(authState.user, authState.isInitialLoading) {
                    if (authState.user == null && !authState.isInitialLoading) {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(navController.graph.id) {
                                inclusive = true
                            }
                        }
                    }
                }

                val startDestination = if (authState.isInitialLoading) {
                    Screen.Login.route // Ou um screen de "loading" se você tiver um
                } else {
                    if (authState.user != null) {
                        Screen.Home.route
                    } else {
                        Screen.Login.route
                    }
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
