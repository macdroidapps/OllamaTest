package ru.macdroid.ollama.domain.repository

import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun sendMessage(message: String): Flow<String>
}
