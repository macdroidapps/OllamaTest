package ru.macdroid.ollama.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.macdroid.ollama.data.local.llm.LlmConfig
import ru.macdroid.ollama.data.local.llm.PromptTemplate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsPreferences(
    private val context: Context
) {
    private val dataStore = context.dataStore

    val useLocalLlm: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[USE_LOCAL_LLM] ?: false
    }

    val selectedModel: Flow<String> = dataStore.data.map { preferences ->
        preferences[SELECTED_MODEL] ?: DEFAULT_MODEL
    }

    val ollamaServerUrl: Flow<String> = dataStore.data.map { preferences ->
        preferences[OLLAMA_SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    // LLM Generation Parameters
    val temperature: Flow<Float> = dataStore.data.map { preferences ->
        preferences[TEMPERATURE] ?: DEFAULT_TEMPERATURE
    }

    val topP: Flow<Float> = dataStore.data.map { preferences ->
        preferences[TOP_P] ?: DEFAULT_TOP_P
    }

    val topK: Flow<Int> = dataStore.data.map { preferences ->
        preferences[TOP_K] ?: DEFAULT_TOP_K
    }

    val repeatPenalty: Flow<Float> = dataStore.data.map { preferences ->
        preferences[REPEAT_PENALTY] ?: DEFAULT_REPEAT_PENALTY
    }

    val maxTokens: Flow<Int> = dataStore.data.map { preferences ->
        preferences[MAX_TOKENS] ?: DEFAULT_MAX_TOKENS
    }

    val promptTemplate: Flow<String> = dataStore.data.map { preferences ->
        preferences[PROMPT_TEMPLATE] ?: DEFAULT_PROMPT_TEMPLATE
    }

    /**
     * Get the complete LlmConfig from all stored preferences
     */
    val llmConfig: Flow<LlmConfig> = dataStore.data.map { preferences ->
        LlmConfig(
            temperature = preferences[TEMPERATURE] ?: DEFAULT_TEMPERATURE,
            topP = preferences[TOP_P] ?: DEFAULT_TOP_P,
            topK = preferences[TOP_K] ?: DEFAULT_TOP_K,
            repeatPenalty = preferences[REPEAT_PENALTY] ?: DEFAULT_REPEAT_PENALTY,
            maxTokens = preferences[MAX_TOKENS] ?: DEFAULT_MAX_TOKENS,
            promptTemplate = PromptTemplate.fromName(
                preferences[PROMPT_TEMPLATE] ?: DEFAULT_PROMPT_TEMPLATE
            )
        )
    }

    suspend fun setUseLocalLlm(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[USE_LOCAL_LLM] = enabled
        }
    }

    suspend fun setSelectedModel(model: String) {
        dataStore.edit { preferences ->
            preferences[SELECTED_MODEL] = model
        }
    }

    suspend fun setOllamaServerUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[OLLAMA_SERVER_URL] = url
        }
    }

    suspend fun setTemperature(value: Float) {
        dataStore.edit { preferences ->
            preferences[TEMPERATURE] = value.coerceIn(0.1f, 1.5f)
        }
    }

    suspend fun setTopP(value: Float) {
        dataStore.edit { preferences ->
            preferences[TOP_P] = value.coerceIn(0.1f, 1.0f)
        }
    }

    suspend fun setTopK(value: Int) {
        dataStore.edit { preferences ->
            preferences[TOP_K] = value.coerceIn(1, 100)
        }
    }

    suspend fun setRepeatPenalty(value: Float) {
        dataStore.edit { preferences ->
            preferences[REPEAT_PENALTY] = value.coerceIn(1.0f, 2.0f)
        }
    }

    suspend fun setMaxTokens(value: Int) {
        dataStore.edit { preferences ->
            preferences[MAX_TOKENS] = value.coerceIn(64, 2048)
        }
    }

    suspend fun setPromptTemplate(template: PromptTemplate) {
        dataStore.edit { preferences ->
            preferences[PROMPT_TEMPLATE] = template.name
        }
    }

    companion object {
        private val USE_LOCAL_LLM = booleanPreferencesKey("use_local_llm")
        private val SELECTED_MODEL = stringPreferencesKey("selected_model")
        private val OLLAMA_SERVER_URL = stringPreferencesKey("ollama_server_url")

        // LLM parameters keys
        private val TEMPERATURE = floatPreferencesKey("llm_temperature")
        private val TOP_P = floatPreferencesKey("llm_top_p")
        private val TOP_K = intPreferencesKey("llm_top_k")
        private val REPEAT_PENALTY = floatPreferencesKey("llm_repeat_penalty")
        private val MAX_TOKENS = intPreferencesKey("llm_max_tokens")
        private val PROMPT_TEMPLATE = stringPreferencesKey("llm_prompt_template")

        private const val DEFAULT_MODEL = "qwen2-0.5b-instruct-q4_k_m"
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:11434"

        // LLM parameter defaults
        private const val DEFAULT_TEMPERATURE = 0.7f
        private const val DEFAULT_TOP_P = 0.9f
        private const val DEFAULT_TOP_K = 40
        private const val DEFAULT_REPEAT_PENALTY = 1.1f
        private const val DEFAULT_MAX_TOKENS = 512
        private const val DEFAULT_PROMPT_TEMPLATE = "ASSISTANT"
    }
}
