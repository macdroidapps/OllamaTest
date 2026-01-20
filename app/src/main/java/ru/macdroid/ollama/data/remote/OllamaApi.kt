package ru.macdroid.ollama.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import ru.macdroid.ollama.data.remote.dto.GenerateRequest
import ru.macdroid.ollama.data.remote.dto.GenerateResponse

class OllamaApi(
    private val client: HttpClient,
    private val json: Json
) {
    companion object {
        // 10.0.2.2 - адрес хоста с эмулятора Android
        private const val BASE_URL = "http://10.0.2.2:11434"
        private const val MODEL = "qwen2.5-coder:7b"
    }

    fun generate(prompt: String): Flow<String> = flow {
        val request = GenerateRequest(
            model = MODEL,
            prompt = prompt,
            stream = true
        )

        client.preparePost("$BASE_URL/api/generate") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.execute { response ->
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.isNotBlank()) {
                    try {
                        val generateResponse = json.decodeFromString<GenerateResponse>(line)
                        if (generateResponse.response.isNotEmpty()) {
                            emit(generateResponse.response)
                        }
                        if (generateResponse.done) break
                    } catch (e: Exception) {
                        // Skip malformed JSON lines
                    }
                }
            }
        }
    }
}
