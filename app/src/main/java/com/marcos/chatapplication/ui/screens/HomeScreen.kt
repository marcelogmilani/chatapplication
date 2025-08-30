package com.marcos.chatapplication.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // Import para sp (se necessário para Text)
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.marcos.chatapplication.R
import com.marcos.chatapplication.domain.model.ConversationWithDetails
import com.marcos.chatapplication.ui.viewmodel.HomeViewModel
import com.marcos.chatapplication.util.DateFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToProfile: () -> Unit,
    onConversationClick: (conversationId: String) -> Unit,
    onNewChatClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversas") },
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewChatClick) {
                Icon(Icons.Default.Add, contentDescription = "Nova Conversa")
            }
        }

    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.conversations.isEmpty()) {
                Text(
                    text = "Nenhuma conversa encontrada.",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.conversations) { conversationDetails ->
                        ConversationItem(
                            conversationDetails = conversationDetails,
                            onClick = {
                                onConversationClick(conversationDetails.conversation.id)
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationItem(
    conversationDetails: ConversationWithDetails,
    onClick: () -> Unit
) {
    val conversation = conversationDetails.conversation
    val otherUser = conversationDetails.otherParticipant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Adiciona a imagem do perfil aqui
        AsyncImage(
            model = otherUser?.profilePictureUrl?.ifEmpty { R.drawable.ic_person_placeholder },
            contentDescription = "Foto do perfil de ${otherUser?.username}",
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(id = R.drawable.ic_person_placeholder),
            error = painterResource(id = R.drawable.ic_person_placeholder)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = otherUser?.username ?: "Utilizador Desconhecido",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = conversation.lastMessage ?: "Nenhuma mensagem",
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = DateFormatter.formatConversationTimestamp(conversation.lastMessageTimestamp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
