package ru.macdroid.ollama.data.local.llm

/**
 * Predefined system prompt templates for different use cases.
 */
enum class PromptTemplate(
    val displayName: String,
    val systemPrompt: String
) {
    ASSISTANT(
        displayName = "Assistant",
        systemPrompt = """You are a helpful AI assistant running locally on an Android device.
            |You provide concise, accurate, and helpful responses.
            |Keep your responses brief but informative.""".trimMargin()
    ),

    CREATIVE(
        displayName = "Creative",
        systemPrompt = """You are a creative writing assistant.
            |Be imaginative, engaging, and expressive in your responses.
            |Feel free to use metaphors and vivid descriptions.""".trimMargin()
    ),

    PRECISE(
        displayName = "Precise",
        systemPrompt = """You are a precise assistant focused on accuracy.
            |Give short, factual answers only.
            |Avoid speculation and unnecessary elaboration.""".trimMargin()
    ),

    CODING(
        displayName = "Coding",
        systemPrompt = """You are a coding assistant.
            |Provide clean, efficient code examples.
            |Explain your code briefly when helpful.""".trimMargin()
    ),

    RUSSIAN(
        displayName = "Russian",
        systemPrompt = """Ты - полезный AI-ассистент.
            |Отвечай кратко и по делу на русском языке.
            |Будь точным и информативным.""".trimMargin()
    ),

    ANALYTICS(
        displayName = "Data Analyst",
        systemPrompt = """You are a data analyst assistant.
            |Analyze data and answer questions based ONLY on the provided context.
            |Be concise and use specific numbers from the data.
            |If you cannot answer a question from the provided data, say so clearly.
            |Format numbers appropriately and highlight key insights.""".trimMargin()
    );

    companion object {
        val DEFAULT = ASSISTANT

        fun fromName(name: String): PromptTemplate {
            return entries.find { it.name == name } ?: DEFAULT
        }
    }
}
