package ru.macdroid.ollama.data.local.llm

/**
 * Predefined LLM configuration presets for different use cases.
 * These allow users to quickly switch between optimized settings.
 */
object ConfigPresets {

    /**
     * Baseline configuration - balanced between creativity and coherence.
     * Good for general-purpose conversations.
     */
    val BASELINE = LlmConfig(
        temperature = 0.7f,
        topP = 0.9f,
        topK = 40,
        repeatPenalty = 1.1f,
        maxTokens = 512,
        promptTemplate = PromptTemplate.ASSISTANT
    )

    /**
     * Creative configuration - higher randomness for more diverse outputs.
     * Good for brainstorming, creative writing, and exploration.
     */
    val CREATIVE = LlmConfig(
        temperature = 1.0f,
        topP = 0.95f,
        topK = 60,
        repeatPenalty = 1.05f,
        maxTokens = 1024,
        promptTemplate = PromptTemplate.CREATIVE
    )

    /**
     * Precise configuration - lower randomness for focused, deterministic responses.
     * Good for factual questions, code generation, and technical queries.
     */
    val PRECISE = LlmConfig(
        temperature = 0.3f,
        topP = 0.7f,
        topK = 20,
        repeatPenalty = 1.15f,
        maxTokens = 256,
        promptTemplate = PromptTemplate.PRECISE
    )

    /**
     * Fast configuration - optimized for quick responses with minimal tokens.
     * Good for simple queries and quick interactions.
     */
    val FAST = LlmConfig(
        temperature = 0.5f,
        topP = 0.8f,
        topK = 30,
        repeatPenalty = 1.1f,
        maxTokens = 128,
        promptTemplate = PromptTemplate.ASSISTANT
    )

    /**
     * Coding configuration - optimized for code generation.
     * Lower temperature for more predictable code output.
     */
    val CODING = LlmConfig(
        temperature = 0.2f,
        topP = 0.8f,
        topK = 25,
        repeatPenalty = 1.1f,
        maxTokens = 1024,
        promptTemplate = PromptTemplate.CODING
    )

    /**
     * All available presets with their display names.
     */
    val ALL = listOf(
        "Baseline" to BASELINE,
        "Creative" to CREATIVE,
        "Precise" to PRECISE,
        "Fast" to FAST,
        "Coding" to CODING
    )

    /**
     * Get a preset by name.
     */
    fun fromName(name: String): LlmConfig {
        return ALL.find { it.first == name }?.second ?: BASELINE
    }
}
