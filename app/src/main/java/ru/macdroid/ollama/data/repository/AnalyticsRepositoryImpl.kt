package ru.macdroid.ollama.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import ru.macdroid.ollama.data.local.analytics.ContextBuilder
import ru.macdroid.ollama.data.local.analytics.StatisticsCalculator
import ru.macdroid.ollama.data.local.analytics.parser.CsvParser
import ru.macdroid.ollama.data.local.analytics.parser.FileParser
import ru.macdroid.ollama.data.local.analytics.parser.JsonParser
import ru.macdroid.ollama.data.local.analytics.parser.LogParser
import ru.macdroid.ollama.data.local.analytics.parser.ParseResult
import ru.macdroid.ollama.data.local.llm.LlmConfig
import ru.macdroid.ollama.data.local.llm.LlmEngine
import ru.macdroid.ollama.data.local.llm.ModelManager
import ru.macdroid.ollama.data.local.llm.ModelManagerContract
import ru.macdroid.ollama.data.local.llm.PromptTemplate
import ru.macdroid.ollama.domain.model.analytics.AnalyticsContext
import ru.macdroid.ollama.domain.model.analytics.DataFile
import ru.macdroid.ollama.domain.model.analytics.DataStatistics
import ru.macdroid.ollama.domain.model.analytics.FileType
import ru.macdroid.ollama.domain.model.analytics.ParsedData
import ru.macdroid.ollama.domain.repository.AnalyticsRepository
import ru.macdroid.ollama.domain.repository.ImportResult
import ru.macdroid.ollama.domain.repository.ImportStage

/**
 * Implementation of AnalyticsRepository.
 */
class AnalyticsRepositoryImpl(
    private val context: Context,
    private val llmEngine: LlmEngine,
    private val modelManager: ModelManagerContract,
    private val contextBuilder: ContextBuilder = ContextBuilder(),
    private val statisticsCalculator: StatisticsCalculator = StatisticsCalculator()
) : AnalyticsRepository {

    companion object {
        private const val MAX_FILE_SIZE = 6 * 1024 * 1024L  // 6 MB
        private const val MAX_PREVIEW_ROWS = 1000
    }

    private val csvParser = CsvParser()
    private val jsonParser = JsonParser()
    private val logParser = LogParser()

    override fun importFile(uri: Uri, fileName: String, type: FileType): Flow<ImportResult> = flow {
        emit(ImportResult.Progress(ImportStage.READING, 0.1f, "Читаем файл..."))

        // Read file content
        val content = readFileContent(uri)
            ?: throw IllegalStateException("Не удалось прочитать файл")

        // Check file size
        if (content.length > MAX_FILE_SIZE) {
            throw IllegalStateException("Файл слишком большой (макс. 6 MB)")
        }

        emit(ImportResult.Progress(ImportStage.PARSING, 0.3f, "Парсим данные..."))

        // Get appropriate parser
        val parser = getParser(type)

        // Parse file
        parser.parse(content, MAX_PREVIEW_ROWS).collect { parseResult ->
            when (parseResult) {
                is ParseResult.Progress -> {
                    val progress = 0.3f + (parseResult.parsedRows.toFloat() /
                            (parseResult.totalRows ?: parseResult.parsedRows).toFloat()) * 0.5f
                    emit(ImportResult.Progress(ImportStage.PARSING, progress, parseResult.message))
                }
                is ParseResult.Success -> {
                    emit(ImportResult.Progress(ImportStage.VALIDATING, 0.9f, "Проверяем данные..."))

                    val dataFile = DataFile(
                        name = fileName,
                        uri = uri,
                        type = type,
                        sizeBytes = content.length.toLong()
                    )

                    emit(ImportResult.Progress(ImportStage.COMPLETE, 1.0f, "Готово"))
                    emit(ImportResult.Success(dataFile, parseResult.data))
                }
                is ParseResult.Error -> {
                    throw IllegalStateException(parseResult.message, parseResult.cause)
                }
            }
        }
    }.catch { e ->
        emit(ImportResult.Error(e.message ?: "Неизвестная ошибка", e))
    }.flowOn(Dispatchers.IO)

    override suspend fun computeStatistics(data: ParsedData): DataStatistics {
        return withContext(Dispatchers.Default) {
            statisticsCalculator.calculate(data)
        }
    }

    override fun buildContext(data: ParsedData, statistics: DataStatistics?): AnalyticsContext {
        return contextBuilder.buildContext(data, statistics)
    }

    override fun analyzeWithLlm(question: String, context: AnalyticsContext): Flow<String> = flow {
        // Ensure model is loaded with analytics system prompt
        if (!llmEngine.isModelLoaded()) {
            val modelPath = modelManager.getModelPath()
            val config = LlmConfig.DEFAULT.copy(promptTemplate = PromptTemplate.ANALYTICS)
            llmEngine.loadModel(modelPath, config).getOrThrow()
            // System prompt is set inside loadModel() from config.promptTemplate
        }

        // Build full prompt with context (system prompt already set during model load)
        val fullPrompt = contextBuilder.buildFullPrompt(context, question)

        // Generate response
        val config = LlmConfig.DEFAULT.copy(
            promptTemplate = PromptTemplate.ANALYTICS,
            temperature = 0.3f,  // Lower temperature for factual analysis
            maxTokens = 1024
        )

        llmEngine.generate(fullPrompt, config).collect { token ->
            emit(token)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun readFileContent(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().readText()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun getParser(type: FileType): FileParser {
        return when (type) {
            FileType.CSV -> csvParser
            FileType.JSON -> jsonParser
            FileType.LOG -> logParser
        }
    }
}
