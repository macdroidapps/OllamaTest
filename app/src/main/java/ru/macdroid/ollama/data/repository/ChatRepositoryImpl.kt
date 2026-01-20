package ru.macdroid.ollama.data.repository

import kotlinx.coroutines.flow.Flow
import ru.macdroid.ollama.data.remote.OllamaApi
import ru.macdroid.ollama.domain.repository.ChatRepository

class ChatRepositoryImpl(
    private val api: OllamaApi
) : ChatRepository {

    override fun sendMessage(message: String): Flow<String> {
        return api.generate(message)
    }
}
