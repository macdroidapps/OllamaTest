package ru.macdroid.ollama.data.local.llm

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

interface LlmEngine {
    suspend fun loadModel(modelPath: String, config: LlmConfig): Result<Unit>
    fun generate(prompt: String, config: LlmConfig = LlmConfig.DEFAULT): Flow<String>
    suspend fun unloadModel()
    fun isModelLoaded(): Boolean
}

class LocalLlmEngine(
    private val context: Context
) : LlmEngine {

    private val inferenceEngine: InferenceEngine by lazy {
        AiChat.getInferenceEngine(context)
    }

    private var systemPromptSet = false

    override suspend fun loadModel(modelPath: String, config: LlmConfig): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val modelFile = File(modelPath)
                if (!modelFile.exists()) {
                    return@withContext Result.failure(IllegalStateException("Model file not found: $modelPath"))
                }

                inferenceEngine.loadModel(modelPath)

                // Set system prompt after loading model
                val systemPrompt = buildSystemPrompt()
                inferenceEngine.setSystemPrompt(systemPrompt)
                systemPromptSet = true

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override fun generate(prompt: String, config: LlmConfig): Flow<String> {
        if (!inferenceEngine.state.value.isModelLoaded) {
            throw IllegalStateException("Model not loaded. Call loadModel() first.")
        }

        return inferenceEngine.sendUserPrompt(
            message = prompt,
            predictLength = config.maxTokens
        ).catch { e ->
            throw Exception("LLM generation error: ${e.message}", e)
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun unloadModel() {
        withContext(Dispatchers.IO) {
            inferenceEngine.cleanUp()
            systemPromptSet = false
        }
    }

    override fun isModelLoaded(): Boolean {
        return inferenceEngine.state.value.isModelLoaded
    }

    private fun buildSystemPrompt(): String {
        return """You are a helpful AI assistant running locally on an Android device.
            |You provide concise, accurate, and helpful responses.
            |You are powered by a quantized language model optimized for mobile devices.
            |Keep your responses brief but informative.""".trimMargin()
    }

    companion object {
        private const val TAG = "LocalLlmEngine"
    }
}
