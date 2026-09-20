package com.kesham.pocketbrain

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kesham.pocketbrain.ui.theme.ClayAlert
import com.kesham.pocketbrain.ui.theme.ClayPrimary
import com.kesham.pocketbrain.ui.theme.ClaySurface
import com.kesham.pocketbrain.ui.theme.ClayTextPrimary
import com.kesham.pocketbrain.ui.theme.ClayTextSecondary
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun ChatRoute(
    onClose: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val chatViewModel: ChatViewModel = viewModel(factory = ChatViewModel.getFactory(context))

    // Reset InferenceModel when entering ChatScreen
    LaunchedEffect(Unit) {
        val inferenceModel = InferenceModel.getInstance(context)
        chatViewModel.resetInferenceModel(inferenceModel)
    }

    val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val textInputEnabled by chatViewModel.isTextInputEnabled.collectAsStateWithLifecycle()
    ChatScreen(
        context,
        uiState,
        textInputEnabled,
        remainingTokens = chatViewModel.tokensRemaining,
        resetTokenCount = {
            chatViewModel.recomputeSizeInTokens("")
        },
        onSendMessage = { message ->
            chatViewModel.sendMessage(message)
        },
        onChangedMessage = { message ->
            chatViewModel.recomputeSizeInTokens(message)
        },
        onClose = onClose
    )
}

@Composable
fun ChatScreen(
    context: Context,
    uiState: UiState,
    textInputEnabled: Boolean,
    remainingTokens: StateFlow<Int>,
    resetTokenCount: () -> Unit,
    onSendMessage: (String) -> Unit,
    onChangedMessage: (String) -> Unit,
    onClose: () -> Unit
) {
    var userMessage by rememberSaveable { mutableStateOf("") }
    val tokens by remainingTokens.collectAsState(initial = -1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClaySurface),
        verticalArrangement = Arrangement.Bottom
    ) {
        ChatTopBar(
            textInputEnabled = textInputEnabled,
            onClearChat = {
                InferenceModel.getInstance(context).resetSession()
                uiState.clearMessages()
                resetTokenCount()
            },
            onCloseChat = {
                InferenceModel.getInstance(context).close()
                uiState.clearMessages()
                resetTokenCount()
                onClose()
            }
        )

        if (tokens >= 0) {
            Text(
                text = "$tokens ${stringResource(R.string.tokens_remaining)}",
                style = MaterialTheme.typography.labelSmall,
                color = ClayTextSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
        }

        if (tokens == 0) {
            ClayBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = ClayPillShape,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.context_full_message),
                    style = MaterialTheme.typography.labelMedium,
                    color = ClayAlert,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            reverseLayout = true
        ) {
            items(uiState.messages) { chat ->
                ChatItem(chat)
            }
        }

        ChatInputBar(
            userMessage = userMessage,
            textInputEnabled = textInputEnabled,
            sendEnabled = textInputEnabled && tokens > 0,
            onUserMessageChanged = { message ->
                userMessage = message
                // Only recompute on first word or when we get a new word
                if (!userMessage.contains(" ") || userMessage.trim() != userMessage) {
                    onChangedMessage(userMessage)
                }
            },
            onFocused = { onChangedMessage(userMessage) },
            onSend = {
                if (userMessage.isNotBlank()) {
                    onSendMessage(userMessage)
                    userMessage = ""
                }
            }
        )

        Text(
            text = stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = ClayTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ChatTopBar(
    textInputEnabled: Boolean,
    onClearChat: () -> Unit,
    onCloseChat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ClayTextPrimary,
            maxLines = 1,
            modifier = Modifier.padding(end = 8.dp)
        )

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayBox(
                modifier = Modifier.weight(1f, fill = false),
                shape = ClayPillShape,
                color = ClayPrimary,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${InferenceModel.model} · ${InferenceModel.activeBackend?.name ?: "N/A"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ClayTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onClearChat,
                enabled = textInputEnabled,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = ClayTextSecondary,
                    disabledContentColor = ClayTextSecondary.copy(alpha = 0.4f)
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Clear Chat")
            }

            IconButton(
                onClick = onCloseChat,
                enabled = textInputEnabled,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = ClayTextSecondary,
                    disabledContentColor = ClayTextSecondary.copy(alpha = 0.4f)
                )
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close Chat")
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    userMessage: String,
    textInputEnabled: Boolean,
    sendEnabled: Boolean,
    onUserMessageChanged: (String) -> Unit,
    onFocused: () -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ClayBox(
            modifier = Modifier.weight(1f),
            shape = ClayPillShape,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
        ) {
            BasicTextField(
                value = userMessage,
                onValueChange = onUserMessageChanged,
                enabled = textInputEnabled,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = ClayTextPrimary),
                cursorBrush = SolidColor(ClayTextPrimary),
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            onFocused()
                        }
                    },
                decorationBox = { innerTextField ->
                    Box {
                        if (userMessage.isEmpty()) {
                            Text(
                                text = stringResource(R.string.chat_label),
                                style = MaterialTheme.typography.bodyLarge,
                                color = ClayTextSecondary
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        ClayBox(
            shape = CircleShape,
            color = ClayPrimary,
            contentPadding = PaddingValues(0.dp)
        ) {
            IconButton(
                onClick = onSend,
                enabled = sendEnabled,
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = ClayTextPrimary,
                    disabledContentColor = ClayTextPrimary.copy(alpha = 0.4f)
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Default.Send,
                    contentDescription = stringResource(R.string.action_send)
                )
            }
        }
    }
}

@Composable
fun ChatItem(
    chatMessage: ChatMessage
) {
    val bubbleColor = if (chatMessage.isFromUser) ClayPrimary else ClaySurface

    val horizontalAlignment = if (chatMessage.isFromUser) {
        Alignment.End
    } else {
        Alignment.Start
    }

    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        val author = if (chatMessage.isFromUser) {
            stringResource(R.string.user_label)
        } else if (chatMessage.isThinking) {
            stringResource(R.string.thinking_label)
        } else {
            stringResource(R.string.model_label)
        }
        Text(
            text = author,
            style = MaterialTheme.typography.labelSmall,
            color = ClayTextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        BoxWithConstraints {
            ClayBox(
                modifier = Modifier.widthIn(0.dp, maxWidth * 0.85f),
                color = bubbleColor,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
            ) {
                if (chatMessage.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = ClayTextSecondary
                    )
                } else {
                    Text(
                        text = chatMessage.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = ClayTextPrimary
                    )
                }
            }
        }
    }
}
