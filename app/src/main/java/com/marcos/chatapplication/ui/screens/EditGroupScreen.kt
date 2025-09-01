package com.marcos.chatapplication.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.marcos.chatapplication.domain.model.User
import com.marcos.chatapplication.ui.viewmodel.ChatViewModel
import com.marcos.chatapplication.ui.viewmodel.GroupActionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGroupScreen(
    conversationId: String,
    navController: NavController,
    viewModel: ChatViewModel = hiltViewModel()
) {
    var groupName by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val groupActionState by viewModel.groupActionState.collectAsStateWithLifecycle()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(conversationId) {
        viewModel.loadConversationDetails(conversationId)
    }

    LaunchedEffect(uiState.conversationDetails) {
        uiState.conversationDetails?.conversation?.groupName?.let {
            groupName = it
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar Grupo") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Nome do Grupo") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Gerenciar participantes
            Text("Participantes:", style = MaterialTheme.typography.titleMedium)

            LazyColumn(modifier = Modifier.height(200.dp)) {
                items(uiState.conversationDetails?.conversation?.participants ?: emptyList()) { participantId ->
                    val user = uiState.participantsDetails[participantId]
                    ParticipantItem(
                        user = user,
                        onRemove = {
                            viewModel.removeParticipantFromGroup(conversationId, participantId)
                        },
                        canRemove = participantId != currentUserId && currentUserId != null
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    navController.navigate("add_participants/$conversationId")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Adicionar Participantes")
            }

            // BOTÃO SALVAR ALTERAÇÕES MOVIDO PARA AQUI (APÓS ADICIONAR PARTICIPANTES)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (groupName.isNotBlank()) {
                        viewModel.updateGroupName(conversationId, groupName)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salvar Alterações")
            }
        }
    }

    LaunchedEffect(groupActionState) {
        when (val state = groupActionState) {
            is GroupActionState.Success -> {
                // Recarregar detalhes após ação bem-sucedida
                viewModel.loadConversationDetails(conversationId)
                viewModel.resetGroupActionState()
                navController.popBackStack()
            }
            is GroupActionState.Error -> {
                // Mostrar mensagem de erro se necessário
                viewModel.resetGroupActionState()
            }
            else -> {}
        }
    }
}

@Composable
fun ParticipantItem(
    user: User?,
    onRemove: () -> Unit,
    canRemove: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar do participante
            AsyncImage(
                model = user?.profilePictureUrl ?: "",
                contentDescription = "Foto de ${user?.username}",
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Nome do participante
            Text(
                text = user?.username ?: "Usuário",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )

            // Botão de remover (apenas se pode remover)
            if (canRemove) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remover participante"
                    )
                }
            }
        }
    }
}