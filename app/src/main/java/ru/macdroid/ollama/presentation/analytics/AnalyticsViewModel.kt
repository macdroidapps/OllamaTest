package ru.macdroid.ollama.presentation.analytics

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.macdroid.ollama.domain.model.Message
import ru.macdroid.ollama.domain.model.analytics.FileType
import ru.macdroid.ollama.domain.repository.AnalyticsRepository
import ru.macdroid.ollama.domain.repository.ImportResult
import ru.macdroid.ollama.domain.repository.ImportStage

class AnalyticsViewModel(
    private val repository: AnalyticsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AnalyticsState())
    val state: StateFlow<AnalyticsState> = _state.asStateFlow()

    private val _effect = Channel<AnalyticsEffect>()
    val effect = _effect.receiveAsFlow()

    fun onIntent(intent: AnalyticsIntent) {
        when (intent) {
            is AnalyticsIntent.ImportFile -> importFile(intent.uriString, intent.fileName, intent.mimeType)
            is AnalyticsIntent.UpdateInput -> updateInput(intent.text)
            is AnalyticsIntent.SendQuestion -> sendQuestion()
            is AnalyticsIntent.ClearData -> clearData()
            is AnalyticsIntent.DismissError -> dismissError()
            is AnalyticsIntent.ComputeStatistics -> computeStatistics()
        }
    }

    private fun importFile(uriString: String, fileName: String, mimeType: String?) {
        val uri = Uri.parse(uriString)

        // Determine file type
        val fileType = FileType.fromFileName(fileName)
            ?: mimeType?.let { FileType.fromMimeType(it) }

        if (fileType == null) {
            val extension = fileName.substringAfterLast('.', "unknown")
            _state.update { it.copy(error = AnalyticsError.UnsupportedFormat(extension)) }
            return
        }

        viewModelScope.launch {
            repository.importFile(uri, fileName, fileType)
                .onStart {
                    _state.update {
                        it.copy(
                            isImporting = true,
                            importProgress = 0f,
                            importMessage = "Начинаем импорт...",
                            error = null
                        )
                    }
                }
                .onCompletion {
                    _state.update { it.copy(isImporting = false) }
                }
                .catch { e ->
                    _state.update {
                        it.copy(
                            isImporting = false,
                            error = AnalyticsError.FileReadError(e.message ?: "Ошибка чтения файла")
                        )
                    }
                }
                .collect { result ->
                    when (result) {
                        is ImportResult.Progress -> {
                            _state.update {
                                it.copy(
                                    importProgress = result.progress,
                                    importMessage = result.message ?: getStageMessage(result.stage)
                                )
                            }
                        }
                        is ImportResult.Success -> {
                            // Build context immediately after successful import
                            val context = repository.buildContext(result.parsedData, null)

                            _state.update {
                                it.copy(
                                    loadedFile = result.dataFile,
                                    parsedData = result.parsedData,
                                    analyticsContext = context,
                                    statistics = null,
                                    messages = emptyList(),
                                    isImporting = false,
                                    importProgress = 1f,
                                    importMessage = null
                                )
                            }

                            // Auto-compute statistics
                            computeStatistics()

                            viewModelScope.launch {
                                _effect.send(AnalyticsEffect.ShowToast("Файл загружен: ${result.dataFile.name}"))
                            }
                        }
                        is ImportResult.Error -> {
                            _state.update {
                                it.copy(
                                    isImporting = false,
                                    error = AnalyticsError.ParseError(result.message)
                                )
                            }
                        }
                    }
                }
        }
    }

    private fun computeStatistics() {
        val data = _state.value.parsedData ?: return

        viewModelScope.launch {
            _state.update { it.copy(isComputingStats = true) }

            try {
                val stats = repository.computeStatistics(data)
                val context = repository.buildContext(data, stats)

                _state.update {
                    it.copy(
                        statistics = stats,
                        analyticsContext = context,
                        isComputingStats = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isComputingStats = false,
                        error = AnalyticsError.AnalysisError("Ошибка вычисления статистики: ${e.message}")
                    )
                }
            }
        }
    }

    private fun updateInput(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    private fun sendQuestion() {
        val question = _state.value.inputText.trim()
        val context = _state.value.analyticsContext

        if (question.isEmpty() || context == null || _state.value.isAnalyzing) return

        val userMessage = Message(content = question, isFromUser = true)

        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                inputText = "",
                isAnalyzing = true,
                error = null
            )
        }

        viewModelScope.launch {
            _effect.send(AnalyticsEffect.ScrollToBottom)
        }

        viewModelScope.launch {
            val responseBuilder = StringBuilder()

            repository.analyzeWithLlm(question, context)
                .onStart {
                    val assistantMessage = Message(content = "", isFromUser = false)
                    _state.update { it.copy(messages = it.messages + assistantMessage) }
                }
                .onCompletion {
                    _state.update { it.copy(isAnalyzing = false) }
                }
                .catch { e ->
                    _state.update {
                        it.copy(
                            isAnalyzing = false,
                            error = AnalyticsError.AnalysisError(e.message ?: "Ошибка анализа"),
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

            _effect.send(AnalyticsEffect.ScrollToBottom)
        }
    }

    private fun clearData() {
        _state.update {
            AnalyticsState()
        }
    }

    private fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun getStageMessage(stage: ImportStage): String {
        return when (stage) {
            ImportStage.READING -> "Читаем файл..."
            ImportStage.PARSING -> "Парсим данные..."
            ImportStage.VALIDATING -> "Проверяем данные..."
            ImportStage.COMPLETE -> "Готово!"
        }
    }
}
