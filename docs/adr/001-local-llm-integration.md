# ADR-001: Local LLM Integration with llama.cpp

## Status
Accepted

## Context
The Ollama Android application currently relies on a network connection to communicate with an Ollama server for LLM inference. Users have requested offline functionality to use the app without network connectivity.

### Requirements
- Run LLM inference directly on Android device
- Support GGUF model format (standard for quantized models)
- Minimal RAM usage (target: 2GB+ devices)
- Streaming token generation
- Seamless switching between online/offline modes

### Evaluated Options

| Option | Pros | Cons |
|--------|------|------|
| **llama.cpp (Android)** | Native performance, hardware acceleration (SME2/AMX), mature ecosystem, GGUF support | Requires NDK build setup |
| llama-stack-client-kotlin | Official Meta SDK | Requires ExecuTorch, larger footprint |
| java-llama.cpp | JNI bindings, Maven available | Limited Android support, manual build |
| MLC LLM | Cross-platform | Different model format, complex setup |

## Decision
We will use **llama.cpp with Android bindings** via the official `examples/llama.android` project structure.

### Reasons
1. **Native Performance**: Direct C++ execution with hardware acceleration
2. **Mature Ecosystem**: Well-maintained, active community
3. **GGUF Support**: Industry standard for quantized models
4. **Kotlin Flow Integration**: Native streaming support via `AiChat` facade
5. **Memory Efficiency**: Optimized for mobile devices with quantization (Q4_K_M, Q5_K_M)

## Architecture

### Component Diagram
```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
├─────────────────────────────────────────────────────────────┤
│  ChatScreen          │  SettingsScreen                       │
│  ChatViewModel       │  SettingsViewModel                    │
│  ChatContract        │  SettingsContract (MVI)               │
└──────────┬───────────┴────────────┬─────────────────────────┘
           │                        │
           ▼                        ▼
┌─────────────────────────────────────────────────────────────┐
│                     Domain Layer                             │
├─────────────────────────────────────────────────────────────┤
│  ChatRepository (interface)                                  │
│  LlmMode { Local, Remote }                                   │
└──────────┬────────────────────────┬─────────────────────────┘
           │                        │
           ▼                        ▼
┌──────────────────────┐  ┌────────────────────────────────────┐
│    Remote Data       │  │          Local Data                │
├──────────────────────┤  ├────────────────────────────────────┤
│ ChatRepositoryImpl   │  │ LocalChatRepositoryImpl            │
│ OllamaApi           │  │ LocalLlmEngine                     │
│                      │  │ ModelManager                       │
│                      │  │ LlmConfig                          │
└──────────────────────┘  └────────────────────────────────────┘
```

### Data Flow (Local Mode)
```
User Input
    │
    ▼
ChatViewModel.onIntent(SendMessage)
    │
    ▼
CompositeChatRepository.sendMessage()
    │
    ├─── if (mode == Local) ───► LocalChatRepositoryImpl
    │                                    │
    │                                    ▼
    │                           LocalLlmEngine.generate()
    │                                    │
    │                                    ▼
    │                           llama.cpp native inference
    │                                    │
    └─── if (mode == Remote) ──► ChatRepositoryImpl
                                         │
                                         ▼
                                    OllamaApi
```

### MVI Contract for Settings
```kotlin
// State
data class SettingsState(
    val useLocalLlm: Boolean,
    val isModelAvailable: Boolean,
    val isModelLoading: Boolean,
    val downloadProgress: Float,
    val modelInfo: ModelInfo?
)

// Intent
sealed interface SettingsIntent {
    object ToggleLocalLlm : SettingsIntent
    object DownloadModel : SettingsIntent
    object DeleteModel : SettingsIntent
    object CheckModelStatus : SettingsIntent
}

// Effect
sealed interface SettingsEffect {
    data class ShowError(val message: String) : SettingsEffect
    object ModelReady : SettingsEffect
    object NavigateBack : SettingsEffect
}
```

## Recommended Model
**Qwen2-0.5B-Instruct-GGUF (Q4_K_M)**
- Size: ~400 MB
- RAM: 2GB+ devices
- Performance: ~10-20 tokens/sec on mid-range devices

### Model Selection Criteria
| Model | Size | Min RAM | Use Case |
|-------|------|---------|----------|
| Qwen2-0.5B Q4_K_M | 400MB | 2GB | Default, fast responses |
| Qwen2-1.5B Q4_K_M | 1GB | 4GB | Better quality |
| Phi-3-mini Q4_K_M | 2GB | 6GB | High quality |

## Offline/Online Mode Strategy

### Mode Switching
1. User toggles mode in Settings
2. Preference stored in DataStore
3. CompositeChatRepository reads preference
4. Routes requests to appropriate implementation

### Graceful Degradation
- If model not downloaded → Show download prompt
- If model loading fails → Fall back to remote with error message
- If device low on memory → Warning before loading

## Consequences

### Positive
- Full offline functionality
- User privacy (no data leaves device)
- Reduced latency for simple queries
- No server costs for local inference

### Negative
- Increased APK size (native libraries ~20MB per ABI)
- Model download required (~400MB+)
- Device battery consumption during inference
- Limited model capability vs server models

### Risks
- Memory pressure on low-end devices
- Thermal throttling during extended use
- Model compatibility with future llama.cpp versions

## Implementation Plan
1. Add llama.cpp Android library
2. Create LocalLlmEngine wrapper
3. Implement ModelManager for download/validation
4. Create CompositeChatRepository for mode switching
5. Build Settings UI with MVI pattern
6. Update Chat UI with mode indicator
7. Add unit and integration tests

## References
- [llama.cpp Android Documentation](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md)
- [GGUF Model Format](https://github.com/ggerganov/ggml/blob/master/docs/gguf.md)
- [Qwen2 Models](https://huggingface.co/Qwen)
