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
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.macdroid.ollama.data.remote.OllamaApi
import ru.macdroid.ollama.data.repository.ChatRepositoryImpl
import ru.macdroid.ollama.domain.repository.ChatRepository
import ru.macdroid.ollama.presentation.chat.ChatViewModel
import java.util.concurrent.TimeUnit

val appModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

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

    single { OllamaApi(get(), get()) }

    single<ChatRepository> { ChatRepositoryImpl(get()) }

    viewModel { ChatViewModel(get()) }
}
