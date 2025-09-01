package com.marcos.chatapplication.ui.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.marcos.chatapplication.R
import com.marcos.chatapplication.domain.model.Conversation
import com.marcos.chatapplication.domain.model.Message
import com.marcos.chatapplication.domain.model.MessageStatus
import com.marcos.chatapplication.domain.model.MessageType
import com.marcos.chatapplication.domain.model.User
import com.marcos.chatapplication.navigation.Screen
import com.marcos.chatapplication.ui.viewmodel.ChatViewModel
import com.marcos.chatapplication.util.DateFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    navController: NavController,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var isSearchVisible by remember { mutableStateOf(false) }

    val conversation = uiState.conversationDetails?.conversation
    val pinnedMessageId = conversation?.pinnedMessageId
    val conversationId = conversation?.id

    var imageUriForPreview by remember { mutableStateOf<Uri?>(null) }

    val currentUserId = Firebase.auth.currentUser?.uid
    val isParticipant = remember(uiState.conversationDetails) {
        val participants = uiState.conversationDetails?.conversation?.participants ?: emptyList()
        currentUserId in participants
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null && isParticipant) {
                Log.d("ChatScreen", "Selected Image URI for preview: $uri")
                imageUriForPreview = uri
            } else if (!isParticipant) {
                Log.d("ChatScreen", "Usuário não é participante, não pode enviar imagens")
            } else {
                Log.d("ChatScreen", "No image selected for preview.")
            }
        }
    )

    LaunchedEffect(uiState.messages.size, uiState.searchQuery) {
        if (uiState.messages.isNotEmpty() && uiState.searchQuery.isBlank()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
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
                    conversation = conversation,
                    otherParticipant = uiState.conversationDetails?.otherParticipant,
                    onSearchClick = { isSearchVisible = true },
                    onEditGroupClick = {
                        if (conversation?.isGroup == true && conversationId != null && isParticipant) {
                            navController.navigate(Screen.EditGroup.createRoute(conversationId))
                        }
                    }
                )
            }
        },
        bottomBar = {
            if(isParticipant) {
                MessageInput(
                    text = text,
                    onTextChange = { text = it },
                    onSendClick = {
                        val currentPreviewUri = imageUriForPreview
                        if (currentPreviewUri != null) {
                            if (conversationId != null) {
                                viewModel.sendImageMessage(
                                    currentPreviewUri,
                                    conversationId,
                                    text.ifBlank { null })
                                imageUriForPreview = null
                                text = ""
                            } else {
                                Log.e(
                                    "ChatScreen",
                                    "Conversation ID is null, cannot send image with caption."
                                )
                            }
                        } else if (text.isNotBlank()) {
                            viewModel.sendMessage(text)
                            text = ""
                        }
                    },
                    onAttachmentClick = {
                        Log.d("ChatScreen", "Attachment button clicked! Launching image picker.")
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    previewImageUri = imageUriForPreview,
                    onRemovePreviewImage = { imageUriForPreview = null }
                )
            }else{
                RemovedUserMessageBar()
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PinnedMessageBar(
                conversation = conversation,
                onUnpin = {
                    if (isParticipant) {
                        viewModel.onPinMessage(null)
                    }
                },
                onClick = {
                    val index = uiState.messages.indexOfFirst { it.id == pinnedMessageId }
                    if (index != -1) {
                        coroutineScope.launch { listState.animateScrollToItem(index) }
                    }
                }
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                val messagesToShow = if (uiState.searchQuery.isNotBlank()) uiState.filteredMessages else uiState.messages
                items(messagesToShow, key = { it.id }) { message ->
                    val sender = uiState.participantsDetails[message.senderId]
                    MessageBubble(
                        message = message,
                        sender = sender,
                        isGroupChat = conversation?.isGroup == true,
                        isPinned = message.id == pinnedMessageId,
                        onLongPress = {
                            if (isParticipant) {
                                viewModel.onPinMessage(message)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RemovedUserMessageBar() {
    Surface(
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Você não é mais participante deste grupo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopAppBar(
    navController: NavController,
    conversation: Conversation?,
    otherParticipant: User?,
    onSearchClick: () -> Unit,
    onEditGroupClick: () -> Unit
) {
    val currentUserId = Firebase.auth.currentUser?.uid
    val isParticipant = remember(conversation) {
        val participants = conversation?.participants ?: emptyList()
        currentUserId in participants
    }

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    enabled = conversation?.isGroup == false && otherParticipant != null,
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
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = conversation.groupName ?: "Grupo")
                } else {
                    AsyncImage(
                        model = otherParticipant?.profilePictureUrl?.ifEmpty { R.drawable.ic_person_placeholder },
                        contentDescription = "Foto de perfil de ${otherParticipant?.username}",
                        modifier = Modifier.size(32.dp).clip(CircleShape),
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
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Buscar Mensagens")
            }
            if (conversation?.isGroup == true && isParticipant) {
                IconButton(onClick = onEditGroupClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Editar Grupo")
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    sender: User?,
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
                contentDescription = "Foto de perfil de ${sender?.username}",
                modifier = Modifier.size(32.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.ic_person_placeholder),
                error = painterResource(id = R.drawable.ic_person_placeholder)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (isSentByCurrentUser) Alignment.End else Alignment.Start) {
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
                            topStart = if (isSentByCurrentUser || (isGroupChat && !isSentByCurrentUser)) 16.dp else 0.dp,
                            topEnd = if (!isSentByCurrentUser || (isGroupChat && isSentByCurrentUser)) 16.dp else 0.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .background(bubbleColor)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (message.type == MessageType.IMAGE && message.mediaUrl != null) {
                    Column {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(message.mediaUrl)
                                .crossfade(true)
                                .placeholder(R.drawable.ic_image_placeholder)
                                .error(R.drawable.ic_broken_image_placeholder)
                                .build(),
                            contentDescription = message.fileName ?: "Imagem",
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )

                        val caption = if (!message.text.isNullOrBlank() && message.text != MessageType.IMAGE_LABEL) {
                            message.text
                        } else {
                            null
                        }

                        if (caption != null) {
                            Text(
                                text = caption,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            if (isPinned) {
                                Icon(Icons.Default.PushPin, contentDescription = "Mensagem fixada", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(text = DateFormatter.formatFullTimestamp(message.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            if (isSentByCurrentUser) {
                                Spacer(modifier = Modifier.width(4.dp))
                                MessageStatusIcon(status = message.status)
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = message.text ?: "",
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isPinned) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = "Mensagem fixada",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = DateFormatter.formatFullTimestamp(message.timestamp),
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
fun MessageStatusIcon(status: String) {
    val icon = when (status) {
        MessageStatus.SENT -> Icons.Default.Done
        MessageStatus.DELIVERED -> Icons.Default.DoneAll
        MessageStatus.READ -> Icons.Filled.DoneAll
        else -> null
    }
    val contentDesc = when (status) {
        MessageStatus.SENT -> "Mensagem enviada"
        MessageStatus.DELIVERED -> "Mensagem entregue"
        MessageStatus.READ -> "Mensagem lida"
        else -> "Status da mensagem"
    }
    val iconColor = if (status == MessageStatus.READ) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    icon?.let {
        Icon(
            imageVector = it,
            contentDescription = contentDesc,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fechar Busca")
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Buscar mensagens...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                maxLines = 1,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Limpar Busca")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PinnedMessageBar(
    conversation: Conversation?,
    onUnpin: () -> Unit,
    onClick: () -> Unit
) {
    val pinnedMessageText = conversation?.pinnedMessageText

    AnimatedVisibility(visible = pinnedMessageText != null) {
        if (pinnedMessageText != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Mensagem fixada",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = pinnedMessageText,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onUnpin, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Desafixar mensagem",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachmentClick: () -> Unit,
    previewImageUri: Uri?,
    onRemovePreviewImage: () -> Unit
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            if (previewImageUri != null) {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
                    AsyncImage(
                        model = previewImageUri,
                        contentDescription = "Pré-visualização da imagem",
                        modifier = Modifier
                            .height(100.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = onRemovePreviewImage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Remover pré-visualização",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = if (previewImageUri != null) 0.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAttachmentClick) {
                    Icon(
                        imageVector = Icons.Filled.AttachFile,
                        contentDescription = "Anexar arquivo"
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { if (previewImageUri != null) Text("Adicionar legenda...") else Text("Digite uma mensagem...") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 5
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onSendClick,
                    enabled = text.isNotBlank() || previewImageUri != null,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar mensagem"
                    )
                }
            }
        }
    }
}