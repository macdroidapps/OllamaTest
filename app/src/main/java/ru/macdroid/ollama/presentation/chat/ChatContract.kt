package ru.macdroid.ollama.presentation.chat

import ru.macdroid.ollama.domain.model.Message

data class ChatState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface ChatIntent {
    data class UpdateInput(val text: String) : ChatIntent
    data object SendMessage : ChatIntent
    data object DismissError : ChatIntent
}

sealed interface ChatEffect {
    data object ScrollToBottom : ChatEffect
}
