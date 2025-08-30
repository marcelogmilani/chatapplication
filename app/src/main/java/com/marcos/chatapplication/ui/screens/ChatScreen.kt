package com.marcos.chatapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import com.marcos.chatapplication.navigation.Screen // Para navegação
import com.marcos.chatapplication.ui.viewmodel.ChatViewModel
import com.marcos.chatapplication.util.DateFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController, // Modificado: onNavigateBack -> navController
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val otherParticipant = uiState.conversationDetails?.otherParticipant

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }


    LaunchedEffect(Unit) {
        viewModel.onChatScreenVisible()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            enabled = otherParticipant?.uid != null && otherParticipant.uid.isNotBlank(),
                            onClick = {
                                otherParticipant?.uid?.let { userId ->
                                    if (userId.isNotBlank()) {
                                        navController.navigate(Screen.OtherUserProfile.createRoute(userId))
                                    }
                                }
                            }
                        )
                    ) {
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
fun MessageBubble(message: Message) {
    val currentUserId = Firebase.auth.currentUser?.uid
    val isSentByCurrentUser = message.senderId == currentUserId

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (isSentByCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isSentByCurrentUser) 16.dp else 0.dp,
                        bottomEnd = if (isSentByCurrentUser) 0.dp else 16.dp
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


@Composable
private fun MessageStatusIcon(status: String) {
    val icon = when (status) {
        MessageStatus.READ -> Icons.Default.DoneAll
        MessageStatus.DELIVERED -> Icons.Default.DoneAll
        else -> Icons.Default.Done
    }

    val iconColor = when (status) {
        MessageStatus.READ -> MaterialTheme.colorScheme.primary
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
