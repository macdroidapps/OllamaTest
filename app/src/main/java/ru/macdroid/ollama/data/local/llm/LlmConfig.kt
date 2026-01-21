package ru.macdroid.ollama.data.local.llm

data class LlmConfig(
    val contextSize: Int = 2048,
    val threads: Int = 4,
    val gpuLayers: Int = 0,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val maxTokens: Int = 512
) {
    companion object {
        val DEFAULT = LlmConfig()

        fun forDevice(availableMemoryMb: Long): LlmConfig {
            return when {
                availableMemoryMb >= 6000 -> LlmConfig(
                    contextSize = 4096,
                    threads = 6,
                    gpuLayers = 32
                )
                availableMemoryMb >= 4000 -> LlmConfig(
                    contextSize = 2048,
                    threads = 4,
                    gpuLayers = 16
                )
                else -> LlmConfig(
                    contextSize = 1024,
                    threads = 2,
                    gpuLayers = 0
                )
            }
        }
    }
}

data class ModelInfo(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val quantization: String,
    val parameters: String
) {
    val sizeFormatted: String
        get() = when {
            sizeBytes >= 1_000_000_000 -> String.format("%.1f GB", sizeBytes / 1_000_000_000.0)
            sizeBytes >= 1_000_000 -> String.format("%.1f MB", sizeBytes / 1_000_000.0)
            else -> String.format("%.1f KB", sizeBytes / 1_000.0)
        }
}

sealed class ModelDownloadState {
    data object Idle : ModelDownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : ModelDownloadState()
    data object Completed : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}
