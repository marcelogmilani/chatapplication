package com.marcos.chatapplication.ui.screens

import androidx.compose.foundation.layout.* // Mantenha ou adicione se necessário
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign // Importar para alinhamento de texto se necessário
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.marcos.chatapplication.R
// Remova a importação duplicada se houver: import com.marcos.chatapplication.ui.viewmodel.OtherUserProfileUiState
import com.marcos.chatapplication.ui.viewmodel.OtherUserProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherUserProfileScreen(
    navController: NavController,
    viewModel: OtherUserProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator()
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error ?: "Erro desconhecido",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
                uiState.user != null -> {
                    val user = uiState.user!!
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp) // Reduzido para acomodar mais um campo
                    ) {
                        AsyncImage(
                            model = user.profilePictureUrl?.ifEmpty { R.drawable.ic_person_placeholder } ?: R.drawable.ic_person_placeholder,
                            contentDescription = "Foto do perfil de ${user.username ?: "usuário"}",
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(id = R.drawable.ic_person_placeholder),
                            error = painterResource(id = R.drawable.ic_person_placeholder)
                        )

                        Spacer(modifier = Modifier.height(8.dp)) // Espaço extra após a imagem

                        Text(
                            text = user.username ?: "Nome não disponível",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // ADICIONADO: Exibição do Status do Usuário
                        Text(
                            text = user.userSetStatus?.takeIf { it.isNotBlank() } ?: "Disponível", // Mostra status ou "Disponível"
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary // Pode usar uma cor diferente para status
                        )

                        // Spacer(modifier = Modifier.height(4.dp)) // Pequeno espaço antes do email

                        Text(
                            text = user.email ?: "Email não disponível",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant // Cor um pouco mais suave
                        )

                        if (!user.birthDate.isNullOrBlank()) {
                            Text(
                                text = "Nascimento: ${user.birthDate}",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant // Cor um pouco mais suave
                            )
                        }
                    }
                }
                else -> {
                    Text("Usuário não encontrado.")
                }
            }
        }
    }
}
