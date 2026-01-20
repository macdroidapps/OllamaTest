package ru.macdroid.ollama.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class GenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = true
)

@Serializable
data class GenerateResponse(
    val model: String = "",
    val response: String = "",
    val done: Boolean = false
)
