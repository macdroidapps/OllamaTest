package ru.macdroid.ollama.di

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ru.macdroid.ollama.data.local.llm.LlmEngine
import ru.macdroid.ollama.data.local.llm.LocalLlmEngine
import ru.macdroid.ollama.data.local.llm.ModelManager
import ru.macdroid.ollama.data.local.llm.ModelManagerContract
import ru.macdroid.ollama.data.local.preferences.SettingsPreferences
import ru.macdroid.ollama.data.remote.OllamaApi
import ru.macdroid.ollama.data.repository.ChatRepositoryImpl
import ru.macdroid.ollama.data.repository.CompositeChatRepository
import ru.macdroid.ollama.data.repository.LocalChatRepositoryImpl
import ru.macdroid.ollama.domain.repository.ChatRepository
import ru.macdroid.ollama.presentation.chat.ChatViewModel
import ru.macdroid.ollama.presentation.settings.SettingsViewModel
import java.util.concurrent.TimeUnit

val appModule = module {
    // JSON Serializer
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    // HTTP Client
    single {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(60, TimeUnit.SECONDS)
                    readTimeout(120, TimeUnit.SECONDS)
                    writeTimeout(60, TimeUnit.SECONDS)
                }
            }
            install(ContentNegotiation) {
                json(get())
            }
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("KtorHttp", message)
                    }
                }
                level = LogLevel.ALL
            }
        }
    }

    // Preferences
    single { SettingsPreferences(androidContext()) }

    // Remote API
    single { OllamaApi(get(), get()) }

    // Local LLM
    single<LlmEngine> { LocalLlmEngine(androidContext()) }
    single<ModelManagerContract> { ModelManager(androidContext()) }

    // Repositories
    single<ChatRepository>(named("remote")) { ChatRepositoryImpl(get()) }
    single<ChatRepository>(named("local")) { LocalChatRepositoryImpl(get(), get()) }
    single<ChatRepository> {
        CompositeChatRepository(
            localRepository = get(named("local")),
            remoteRepository = get(named("remote")),
            settingsPreferences = get()
        )
    }

    // ViewModels
    viewModel { ChatViewModel(get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
}
