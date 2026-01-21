package ru.macdroid.ollama.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import ru.macdroid.ollama.data.local.llm.LlmConfig
import ru.macdroid.ollama.data.local.llm.LlmEngine
import ru.macdroid.ollama.data.local.llm.ModelManagerContract
import ru.macdroid.ollama.domain.repository.ChatRepository

class LocalChatRepositoryImpl(
    private val llmEngine: LlmEngine,
    private val modelManager: ModelManagerContract
) : ChatRepository {

    override fun sendMessage(message: String): Flow<String> = flow {
        if (!llmEngine.isModelLoaded()) {
            val modelPath = modelManager.getModelPath()
            val config = LlmConfig.DEFAULT

            llmEngine.loadModel(modelPath, config).getOrThrow()
        }

        emitAll(llmEngine.generate(message))
    }.catch { e ->
        throw Exception("Local LLM error: ${e.message}", e)
    }
}
