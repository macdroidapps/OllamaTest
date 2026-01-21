package ru.macdroid.ollama.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.macdroid.ollama.data.local.preferences.SettingsPreferences
import ru.macdroid.ollama.data.repository.LlmMode
import ru.macdroid.ollama.domain.model.Message
import ru.macdroid.ollama.domain.repository.ChatRepository

class ChatViewModel(
    private val repository: ChatRepository,
    private val settingsPreferences: SettingsPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val _effect = Channel<ChatEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        refreshLlmMode()
    }

    fun onIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.UpdateInput -> updateInput(intent.text)
            is ChatIntent.SendMessage -> sendMessage()
            is ChatIntent.DismissError -> dismissError()
            is ChatIntent.RefreshLlmMode -> refreshLlmMode()
        }
    }

    private fun updateInput(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    private fun refreshLlmMode() {
        viewModelScope.launch {
            val useLocal = settingsPreferences.useLocalLlm.first()
            val mode = if (useLocal) LlmMode.Local else LlmMode.Remote
            _state.update { it.copy(llmMode = mode) }
        }
    }

    private fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isLoading) return

        val userMessage = Message(content = text, isFromUser = true)

        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = "",
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            _effect.send(ChatEffect.ScrollToBottom)
        }

        viewModelScope.launch {
            val responseBuilder = StringBuilder()

            repository.sendMessage(text)
                .onStart {
                    val assistantMessage = Message(content = "", isFromUser = false)
                    _state.update { it.copy(messages = it.messages + assistantMessage) }
                }
                .onCompletion {
                    _state.update { it.copy(isLoading = false) }
                }
                .catch { e ->
                    _state.update {
                        it.copy(
                            error = e.message ?: "Unknown error",
                            isLoading = false,
                            messages = it.messages.dropLast(1)
                        )
                    }
                }
                .collect { token ->
                    responseBuilder.append(token)
                    _state.update { currentState ->
                        val messages = currentState.messages.toMutableList()
                        if (messages.isNotEmpty()) {
                            val lastIndex = messages.lastIndex
                            messages[lastIndex] = messages[lastIndex].copy(
                                content = responseBuilder.toString()
                            )
                        }
                        currentState.copy(messages = messages)
                    }
                }

            _effect.send(ChatEffect.ScrollToBottom)
        }
    }

    private fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}
