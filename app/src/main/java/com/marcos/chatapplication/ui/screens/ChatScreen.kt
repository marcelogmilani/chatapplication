package com.marcos.chatapplication.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.marcos.chatapplication.R // Para o placeholder
import com.marcos.chatapplication.domain.model.Message
import com.marcos.chatapplication.domain.model.MessageStatus
import com.marcos.chatapplication.domain.model.User
import com.marcos.chatapplication.navigation.Screen // Para navegação
import com.marcos.chatapplication.ui.viewmodel.ChatViewModel
import com.marcos.chatapplication.util.DateFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var isSearchVisible by remember { mutableStateOf(false) }

    val conversationDetails = uiState.conversationDetails
    val conversation = uiState.conversationDetails?.conversation
    val otherParticipant = uiState.conversationDetails?.otherParticipant
    val pinnedMessageId = conversation?.pinnedMessageId

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }

    LaunchedEffect(uiState.filteredMessages.size) {
        if (uiState.filteredMessages.isNotEmpty() && uiState.searchQuery.isBlank()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.filteredMessages.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            if (isSearchVisible) {
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onClose = {
                        isSearchVisible = false
                        viewModel.onSearchQueryChange("")
                    }
                )
            } else {
                ChatTopAppBar(
                    navController = navController,
                    otherParticipant = otherParticipant,
                    onSearchClick = { isSearchVisible = true }
                )
            }
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            enabled = conversation?.isGroup == false,
                            onClick = {
                                otherParticipant?.uid?.let { userId ->
                                    if (userId.isNotBlank()) {
                                        navController.navigate(Screen.OtherUserProfile.createRoute(userId))
                                    }
                                }
                            }
                        )
                    ) {
                        if (conversation?.isGroup == true) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = "Ícone do Grupo",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = conversation.groupName ?: "Grupo")
                        } else {
                            AsyncImage(
                                model = otherParticipant?.profilePictureUrl?.ifEmpty { R.drawable.ic_person_placeholder },
                                contentDescription = "Foto do perfil de ${otherParticipant?.username}",
                                modifier = Modifier
                                    .size(32.dp) // Tamanho da imagem na TopAppBar
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                                placeholder = painterResource(id = R.drawable.ic_person_placeholder),
                                error = painterResource(id = R.drawable.ic_person_placeholder)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = otherParticipant?.username ?: "Carregando...")
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { // Modificado para usar navController
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        bottomBar = {
            MessageInput(
                text = text,
                onTextChange = { text = it },
                onSendClick = {
                    viewModel.sendMessage(text)
                    text = ""
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(uiState.messages) { message ->
                MessageBubble(message = message)
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    sender: User?, // Modificado de senderName: String? para User?
    isGroupChat: Boolean,
    isPinned: Boolean,
    onLongPress: () -> Unit
) {

    val currentUserId = Firebase.auth.currentUser?.uid
    val isSentByCurrentUser = message.senderId == currentUserId

    val bubbleColor = if (isPinned) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else if (isSentByCurrentUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isSentByCurrentUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {

        if (isGroupChat && !isSentByCurrentUser) {
            AsyncImage(
                model = sender?.profilePictureUrl?.ifEmpty { R.drawable.ic_person_placeholder },
                contentDescription = "Foto do perfil de ${sender?.username}",
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.ic_person_placeholder),
                error = painterResource(id = R.drawable.ic_person_placeholder)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isSentByCurrentUser) Alignment.End else Alignment.Start
        ) {
            if (isGroupChat && !isSentByCurrentUser && sender != null) {
                Text(
                    text = sender.username ?: "Utilizador",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = if (isSentByCurrentUser) 16.dp else 0.dp,
                            topEnd = if (isSentByCurrentUser) 0.dp else 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .background(
                        if (isSentByCurrentUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isPinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Fixada",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = message.text,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = DateFormatter.formatMessageTimestamp(message.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            if (isSentByCurrentUser) {
                                Spacer(modifier = Modifier.width(4.dp))
                                MessageStatusIcon(status = message.status)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun MessageStatusIcon(status: String) {
    val icon = when (status) {
        MessageStatus.READ -> Icons.Default.DoneAll
        MessageStatus.DELIVERED -> Icons.Default.DoneAll
        else -> Icons.Default.Done
    }

    val iconColor = when (status) {
        MessageStatus.READ -> Color(0xFF00B0FF)
        else -> Color.Gray
    }

    Icon(
        imageVector = icon,
        contentDescription = "Status da mensagem: $status",
        tint = iconColor,
        modifier = Modifier.size(16.dp)
    )
}

@Composable
fun MessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Digite uma mensagem...") },
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            IconButton(
                onClick = onSendClick,
                enabled = text.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
