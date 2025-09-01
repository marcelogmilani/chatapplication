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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
// import androidx.compose.ui.text.style.TextAlign // Removido se não usado
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.util.concurrent.TimeUnit

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

    val conversation = uiState.conversationDetails?.conversation
    val pinnedMessageId = conversation?.pinnedMessageId
    val conversationId = conversation?.id

    var imageUriForPreview by remember { mutableStateOf<Uri?>(null) }
    var videoUriForPreview by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                Log.d("ChatScreen", "Selected Image URI for preview: $uri")
                imageUriForPreview = uri
                videoUriForPreview = null
            } else {
                Log.d("ChatScreen", "No image selected for preview.")
            }
        }
    )

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                Log.d("ChatScreen", "Selected Video URI for preview: $uri")
                videoUriForPreview = uri
                imageUriForPreview = null
            } else {
                Log.d("ChatScreen", "No video selected for preview.")
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
                    onSearchClick = { isSearchVisible = true }
                )
            }
        },
        bottomBar = {
            MessageInput(
                text = text,
                onTextChange = { text = it },
                onSendClick = {
                    val currentImagePreviewUri = imageUriForPreview
                    val currentVideoPreviewUri = videoUriForPreview

                    if (currentImagePreviewUri != null) {
                        if (conversationId != null) {
                            viewModel.sendImageMessage(currentImagePreviewUri, conversationId, text.ifBlank { null })
                            imageUriForPreview = null
                            text = ""
                        } else {
                            Log.e("ChatScreen", "Conversation ID is null, cannot send image.")
                        }
                    } else if (currentVideoPreviewUri != null) {
                        if (conversationId != null) {
                            viewModel.sendVideoMessage(currentVideoPreviewUri, conversationId, text.ifBlank { null })
                            videoUriForPreview = null
                            text = ""
                        } else {
                            Log.e("ChatScreen", "Conversation ID is null, cannot send video.")
                        }
                    } else if (text.isNotBlank()) {
                        viewModel.sendMessage(text)
                        text = ""
                    }
                },
                onAttachmentClick = {
                    Log.d("ChatScreen", "Image Attachment button clicked! Launching image picker.")
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                previewImageUri = imageUriForPreview,
                onRemovePreviewImage = { imageUriForPreview = null },
                previewVideoUri = videoUriForPreview,
                onRemovePreviewVideo = { videoUriForPreview = null },
                onVideoAttachmentClick = {
                    Log.d("ChatScreen", "Video Attachment button clicked! Launching video picker.")
                    videoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                }
            )
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
                    viewModel.onPinMessage(null) // Pass null to unpin
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
                        onLongPress = { viewModel.onPinMessage(message) },
                        navController = navController
                    )
                }
            }
        }
    }
}

fun formatDuration(millis: Long?): String {
    if (millis == null || millis <= 0) return ""
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1)

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    sender: User?,
    isGroupChat: Boolean,
    isPinned: Boolean,
    onLongPress: () -> Unit,
    navController: NavController
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
                        onClick = {
                            if (message.mediaUrl != null) {
                                when (message.type) {
                                    MessageType.IMAGE -> {
                                        Log.d("MessageBubble", "Image clicked: ${message.mediaUrl}")
                                        navController.navigate(Screen.MediaView.createRoute("image", message.mediaUrl!!))
                                    }
                                    MessageType.VIDEO -> {
                                        Log.d("MessageBubble", "Video clicked: ${message.mediaUrl}")
                                        navController.navigate(Screen.MediaView.createRoute("video", message.mediaUrl!!))
                                    }
                                    else -> {
                                        Log.d("MessageBubble", "Text message clicked (no action defined for simple click).")
                                    }
                                }
                            }
                        },
                        onLongClick = onLongPress,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    )
            ) {
                when (message.type) {
                    MessageType.IMAGE -> {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
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
                            val caption = if (!message.text.isNullOrBlank() && message.text != MessageType.IMAGE_LABEL) message.text else null
                            if (caption != null) {
                                Text(text = caption, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                            }
                            MessageMetadataRow(message, isSentByCurrentUser, isPinned)
                        }
                    }
                    MessageType.VIDEO -> {
                        Column(
                            modifier = if (!message.text.isNullOrBlank() && message.text != MessageType.VIDEO_LABEL)
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            else
                                Modifier
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(message.thumbnailUrl ?: R.drawable.ic_video_placeholder)
                                        .crossfade(true)
                                        .placeholder(R.drawable.ic_video_placeholder)
                                        .error(R.drawable.ic_video_placeholder)
                                        .build(),
                                    contentDescription = "Miniatura do vídeo: ${message.fileName ?: ""}",
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Reproduzir vídeo",
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(48.dp)
                                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                        .padding(8.dp),
                                    tint = Color.White
                                )
                                if (message.duration != null && message.duration > 0) {
                                    Text(
                                        text = formatDuration(message.duration),
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 10.sp),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            val caption = if (!message.text.isNullOrBlank() && message.text != MessageType.VIDEO_LABEL) message.text else null
                            if (caption != null) {
                                Text(
                                    text = caption,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            MessageMetadataRow(
                                message,
                                isSentByCurrentUser,
                                isPinned,
                                modifier = if (caption == null && (message.text == MessageType.VIDEO_LABEL || message.text.isNullOrBlank()))
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                else
                                    Modifier
                            )
                        }
                    }
                    else -> { // Mensagem de Texto
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = message.text ?: "",
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            MessageMetadataRow(message, isSentByCurrentUser, isPinned, isText = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageMetadataRow(
    message: Message,
    isSentByCurrentUser: Boolean,
    isPinned: Boolean,
    modifier: Modifier = Modifier,
    isText: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isText) Arrangement.End else Arrangement.Start, // Para texto, alinhar metadados à direita do texto
        modifier = modifier.then(
            if (!isText) Modifier.fillMaxWidth().padding(top = 4.dp) // Para imagem/vídeo, ocupa largura e tem padding superior
            else Modifier.padding(start = 8.dp) // Para texto, apenas padding inicial para separar do texto
        )
    ) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopAppBar(
    navController: NavController,
    conversation: Conversation?,
    otherParticipant: User?,
    onSearchClick: () -> Unit
) {
    val userStatus = rememberFormattedUserStatus(otherParticipant)

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
                    Text(
                        text = conversation.groupName ?: "Grupo",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                    Column {
                        Text(
                            text = otherParticipant?.username ?: "Carregando...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium // Um pouco maior para o nome
                        )
                        if (userStatus.isNotBlank()) {
                            Text(
                                text = userStatus,
                                style = MaterialTheme.typography.bodySmall, // Estilo menor para o status
                                color = MaterialTheme.colorScheme.onSurfaceVariant, // Cor suave
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
        }
    )
}

@Composable
fun MessageStatusIcon(status: String) {
    val icon = when (status) {
        MessageStatus.SENT -> Icons.Default.Done
        MessageStatus.DELIVERED -> Icons.Default.DoneAll
        MessageStatus.READ -> Icons.Filled.DoneAll // MaterialTheme.colorScheme.primary para este
        else -> null // ou um ícone de pendente/erro se quiser
    }
    val contentDesc = when (status) {
        MessageStatus.SENT -> "Mensagem enviada"
        MessageStatus.DELIVERED -> "Mensagem entregue"
        MessageStatus.READ -> "Mensagem lida"
        else -> "Status da mensagem" // Ou um específico para pendente/erro
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
        color = MaterialTheme.colorScheme.surfaceVariant, // Ou Surface, dependendo do design
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp), // Altura padrão da TopAppBar
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
                    focusedIndicatorColor = Color.Transparent, // Sem linha indicadora
                    unfocusedIndicatorColor = Color.Transparent, // Sem linha indicadora
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                maxLines = 1,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge // Ou o estilo desejado
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { // Limpar query
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
        if (pinnedMessageText != null) { // Verificação para Smart Cast
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp), // Padding em volta da barra
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant, // Cor de destaque
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp) // Padding interno
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) { // Para o texto ocupar o espaço disponível
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
                            fontWeight = FontWeight.SemiBold // Dar destaque
                        )
                    }
                    IconButton(onClick = onUnpin, modifier = Modifier.size(24.dp)) { // Tamanho menor para o ícone de fechar
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
    onAttachmentClick: () -> Unit, // Para imagens
    previewImageUri: Uri?,
    onRemovePreviewImage: () -> Unit,
    previewVideoUri: Uri?,
    onRemovePreviewVideo: () -> Unit,
    onVideoAttachmentClick: () -> Unit // Para vídeos
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface // Cor de fundo da barra de input
    ) {
        Column { // Para empilhar a pré-visualização e a barra de input
            // Pré-visualização da Imagem
            if (previewImageUri != null) {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
                    AsyncImage(
                        model = previewImageUri,
                        contentDescription = "Pré-visualização da imagem",
                        modifier = Modifier
                            .height(100.dp) // Altura da miniatura
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
                            contentDescription = "Remover pré-visualização da imagem",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer // Cor do ícone para bom contraste
                        )
                    }
                }
            } else if (previewVideoUri != null) {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)) // Fundo para a pré-visualização do vídeo
                    .fillMaxWidth()
                    .height(100.dp) // Altura da miniatura
                ) {
                    Column( // Para centralizar o ícone e o texto
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Videocam, // Ícone de vídeo
                            contentDescription = "Ícone de vídeo",
                            modifier = Modifier.size(40.dp), // Tamanho do ícone
                            tint = MaterialTheme.colorScheme.primary // Cor do ícone
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text( // Nome do arquivo ou texto genérico
                            text = "Vídeo selecionado: ${previewVideoUri.lastPathSegment ?: "video.mp4"}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onRemovePreviewVideo,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Remover pré-visualização do vídeo",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = if (previewImageUri != null || previewVideoUri != null) 0.dp else 8.dp), // Ajustar padding superior se houver preview
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAttachmentClick) { // Botão para anexar imagem
                    Icon(imageVector = Icons.Filled.AttachFile, contentDescription = "Anexar imagem")
                }
                IconButton(onClick = onVideoAttachmentClick) { // Botão para anexar vídeo
                    Icon(imageVector = Icons.Filled.Videocam, contentDescription = "Anexar vídeo")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        val placeholderText = when {
                            previewImageUri != null -> "Adicionar legenda para imagem..."
                            previewVideoUri != null -> "Adicionar legenda para vídeo..."
                            else -> "Digite uma mensagem..."
                        }
                        Text(placeholderText)
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, // Cor de fundo do TextField
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, // Cor de fundo do TextField
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant, // Cor de fundo do TextField
                        focusedIndicatorColor = Color.Transparent, // Sem linha sob o texto
                        unfocusedIndicatorColor = Color.Transparent, // Sem linha sob o texto
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(20.dp), // Cantos arredondados
                    maxLines = 5 // Permitir múltiplas linhas para legendas ou mensagens mais longas
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onSendClick,
                    enabled = text.isNotBlank() || previewImageUri != null || previewVideoUri != null, // Habilitar se houver texto OU mídia
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary, // Cor do ícone quando habilitado
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) // Cor do ícone quando desabilitado
                    )
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar mensagem")
                }
            }
        }
    }
}

// ================== NOVAS FUNÇÕES ADICIONADAS ABAIXO ==================

@Composable
fun rememberFormattedUserStatus(user: User?): String {
    val context = LocalContext.current
    return remember(user?.presenceStatus, user?.lastSeen) { // Recalcula se o status ou lastSeen mudar
        formatUserStatus(user, context)
    }
}

fun formatUserStatus(user: User?, context: android.content.Context): String {
    if (user == null) return ""

    return when (user.presenceStatus) {
        "Online" -> "Online"
        "Offline" -> {
            val lastSeenTime = user.lastSeen
            if (lastSeenTime == null || lastSeenTime <= 0) {
                "Offline" // Ou "Visto por último: desconhecido"
            } else {
                val now = System.currentTimeMillis()
                val diff = now - lastSeenTime

                val oneMinuteInMillis = 60 * 1000L
                val oneHourInMillis = 60 * oneMinuteInMillis
                // val oneDayInMillis = 24 * oneHourInMillis // Não usado diretamente com Calendar

                when {
                    diff < oneMinuteInMillis -> "Visto por último: agora mesmo"
                    diff < oneHourInMillis -> {
                        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                        "Visto por último: há $minutes min"
                    }
                    else -> {
                        val lastSeenCalendar = java.util.Calendar.getInstance().apply { timeInMillis = lastSeenTime }
                        val todayCalendar = java.util.Calendar.getInstance()

                        val timeFormat = android.text.format.DateFormat.getTimeFormat(context) // Formato de hora localizado
                        val dateFormat = android.text.format.DateFormat.getDateFormat(context) // Formato de data localizado (CORRETO)

                        if (lastSeenCalendar.get(java.util.Calendar.YEAR) == todayCalendar.get(java.util.Calendar.YEAR) &&
                            lastSeenCalendar.get(java.util.Calendar.DAY_OF_YEAR) == todayCalendar.get(java.util.Calendar.DAY_OF_YEAR)) {
                            "Visto por último: hoje às ${timeFormat.format(lastSeenCalendar.time)}"
                        } else {
                            // Cria uma cópia para não modificar o todayCalendar original permanentemente
                            val yesterdayCalendar = java.util.Calendar.getInstance().apply {
                                timeInMillis = todayCalendar.timeInMillis
                                add(java.util.Calendar.DAY_OF_YEAR, -1)
                            }
                            if (lastSeenCalendar.get(java.util.Calendar.YEAR) == yesterdayCalendar.get(java.util.Calendar.YEAR) &&
                                lastSeenCalendar.get(java.util.Calendar.DAY_OF_YEAR) == yesterdayCalendar.get(java.util.Calendar.DAY_OF_YEAR)) {
                                "Visto por último: ontem às ${timeFormat.format(lastSeenCalendar.time)}"
                            } else {
                                // Para datas mais antigas, pode ser melhor mostrar apenas a data ou data e hora.
                                // Exemplo: "Visto em 20/08/23 às 15:30"
                                "Visto em ${dateFormat.format(lastSeenCalendar.time)} às ${timeFormat.format(lastSeenCalendar.time)}"
                            }
                        }
                    }
                }
            }
        }
        else -> user.presenceStatus ?: "" // Fallback para outros status ou se for nulo
    }
}
