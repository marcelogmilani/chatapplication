package com.marcos.chatapplication.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredConversations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        //If se o usuário tiver conversas, else se o usuário nunca tiver falado com ninguém
                        text = if (uiState.searchQuery.isNotEmpty()) "Nenhum resultado encontrado." else "Nenhuma conversa encontrada.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.filteredConversations, key = { it.conversation.id }) { conversationDetails ->
                        ConversationItem(
                            conversationDetails = conversationDetails,
                            onClick = {
                                onConversationClick(conversationDetails.conversation.id)
                            }
                        )
                        HorizontalDivider() // Adiciona uma linha divisória entre os itens
                    }
                }
            }
        }
    }
}

//Barra de pesquisa de contatos e grupos
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Buscar contatos ou grupos...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Ícone de Busca") },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Limpar Busca")
                }
            }
        },
        singleLine = true,
        shape = CircleShape
    )
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
        // NOVO: Adiciona um ícone para distinguir visualmente os grupos
        if (conversation.isGroup) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = "Grupo",
                modifier = Modifier.padding(end = 16.dp),
                tint = Color.Gray
            )
        } else {
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
        }

        Column(modifier = Modifier.weight(1f)) {
            // LÓGICA ATUALIZADA PARA O NOME
            Text(
                text = if (conversation.isGroup) {
                    conversation.groupName ?: "Grupo sem nome"
                } else {
                    otherUser?.username ?: "Utilizador Desconhecido"
                },
                fontWeight = FontWeight.Bold
            )
            Text(
                text = conversation.lastMessage ?: "Nenhuma mensagem",
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = DateFormatter.formatConversationTimestamp(conversation.lastMessageTimestamp),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}
