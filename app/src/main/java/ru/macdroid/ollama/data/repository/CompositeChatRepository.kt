package ru.macdroid.ollama.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import ru.macdroid.ollama.data.local.preferences.SettingsPreferences
import ru.macdroid.ollama.domain.repository.ChatRepository

enum class LlmMode {
    Local,
    Remote
}

class CompositeChatRepository(
    private val localRepository: ChatRepository,
    private val remoteRepository: ChatRepository,
    private val settingsPreferences: SettingsPreferences
) : ChatRepository {

    override fun sendMessage(message: String): Flow<String> = flow {
        val useLocal = settingsPreferences.useLocalLlm.first()
        val repository = if (useLocal) localRepository else remoteRepository
        emitAll(repository.sendMessage(message))
    }

    suspend fun getCurrentMode(): LlmMode {
        return if (settingsPreferences.useLocalLlm.first()) LlmMode.Local else LlmMode.Remote
    }
}
