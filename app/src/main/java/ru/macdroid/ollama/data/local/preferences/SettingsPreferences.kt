package ru.macdroid.ollama.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    companion object {
        private val USE_LOCAL_LLM = booleanPreferencesKey("use_local_llm")
        private val SELECTED_MODEL = stringPreferencesKey("selected_model")
        private val OLLAMA_SERVER_URL = stringPreferencesKey("ollama_server_url")

        private const val DEFAULT_MODEL = "qwen2-0.5b-instruct-q4_k_m"
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:11434"
    }
}
