package ru.macdroid.ollama.data.local.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

interface ModelManagerContract {
    suspend fun isModelAvailable(): Boolean
    suspend fun getModelInfo(): ModelInfo?
    fun getModelPath(): String
    fun downloadModel(url: String): Flow<ModelDownloadState>
    suspend fun deleteModel(): Result<Unit>
    suspend fun validateModel(): Boolean
}

class ModelManager(
    private val context: Context
) : ModelManagerContract {

    private val modelsDir: File by lazy {
        File(context.filesDir, MODELS_DIR).also { it.mkdirs() }
    }

    private val defaultModelFile: File
        get() = File(modelsDir, DEFAULT_MODEL_FILENAME)

    override suspend fun isModelAvailable(): Boolean = withContext(Dispatchers.IO) {
        defaultModelFile.exists() && defaultModelFile.length() > MIN_MODEL_SIZE
    }

    override suspend fun getModelInfo(): ModelInfo? = withContext(Dispatchers.IO) {
        if (!isModelAvailable()) return@withContext null

        ModelInfo(
            name = DEFAULT_MODEL_NAME,
            path = defaultModelFile.absolutePath,
            sizeBytes = defaultModelFile.length(),
            quantization = "Q4_K_M",
            parameters = "0.5B"
        )
    }

    override fun getModelPath(): String = defaultModelFile.absolutePath

    override fun downloadModel(url: String): Flow<ModelDownloadState> = flow {
        emit(ModelDownloadState.Idle)

        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            emit(ModelDownloadState.Downloading(0f, 0, totalBytes))

            // Create temp file for atomic download
            val tempFile = File(modelsDir, "${DEFAULT_MODEL_FILENAME}.tmp")

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) {
                            downloadedBytes.toFloat() / totalBytes
                        } else {
                            0f
                        }

                        emit(ModelDownloadState.Downloading(progress, downloadedBytes, totalBytes))
                    }
                }
            }

            // Atomic rename after successful download
            if (tempFile.renameTo(defaultModelFile)) {
                emit(ModelDownloadState.Completed)
            } else {
                tempFile.delete()
                emit(ModelDownloadState.Error("Failed to save model file"))
            }

        } catch (e: Exception) {
            emit(ModelDownloadState.Error(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun deleteModel(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (defaultModelFile.exists()) {
                if (defaultModelFile.delete()) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to delete model file"))
                }
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun validateModel(): Boolean = withContext(Dispatchers.IO) {
        if (!defaultModelFile.exists()) return@withContext false

        try {
            // Basic GGUF magic number validation
            // GGUF files start with "GGUF" magic bytes
            defaultModelFile.inputStream().use { input ->
                val magic = ByteArray(4)
                if (input.read(magic) != 4) return@withContext false
                val magicStr = String(magic, Charsets.US_ASCII)
                magicStr == "GGUF"
            }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val MODELS_DIR = "models"
        private const val DEFAULT_MODEL_FILENAME = "qwen2-0.5b-instruct-q4_k_m.gguf"
        private const val DEFAULT_MODEL_NAME = "Qwen2-0.5B-Instruct"
        private const val MIN_MODEL_SIZE = 100_000_000L // 100 MB minimum

        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val BUFFER_SIZE = 8192
        private const val USER_AGENT = "Ollama-Android/1.0"

        // Default model URL (Qwen2-0.5B-Instruct Q4_K_M from HuggingFace)
        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/Qwen/Qwen2-0.5B-Instruct-GGUF/resolve/main/qwen2-0_5b-instruct-q4_k_m.gguf"
    }
}
