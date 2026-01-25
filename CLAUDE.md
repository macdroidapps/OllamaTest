# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build and install to connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run single test class
./gradlew :app:testDebugUnitTest --tests "ru.macdroid.ollama.ExampleUnitTest"

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

**Note:** First build takes significantly longer due to llama.cpp native compilation via CMake. Subsequent builds are faster due to caching.

## Architecture

This is an Android chat application that supports both remote Ollama server and local on-device LLM inference via llama.cpp.

### Module Structure

- **:app** - Main Android application (Jetpack Compose UI)
- **:llama-lib** - Android library wrapping llama.cpp for local LLM inference
- **llama.cpp/** - Git submodule containing the official llama.cpp repository

### Layer Architecture

```
Presentation (MVI)          Domain                  Data
├── ChatScreen              ├── ChatRepository      ├── Remote (OllamaApi)
├── ChatViewModel           │   (interface)         ├── Local (LocalLlmEngine)
├── ChatContract            └── Message             └── CompositeChatRepository
├── SettingsScreen                                      (routes local/remote)
└── SettingsViewModel
```

### Key Patterns

**MVI Pattern**: ViewModels use Contract classes defining State, Intent, and Effect sealed interfaces. See `ChatContract.kt` and `SettingsContract.kt`.

**Composite Repository**: `CompositeChatRepository` routes requests to either `LocalChatRepositoryImpl` or `ChatRepositoryImpl` based on user preference stored in DataStore.

**DI with Koin**: All dependencies configured in `di/AppModule.kt`. Named qualifiers (`named("local")`, `named("remote")`) differentiate repository implementations.

### llama-lib Integration

The native library uses JNI to bridge Kotlin to llama.cpp C++ code:
- `llama-lib/.../AiChat.kt` - Main singleton entry point (`AiChat.getInferenceEngine(context)`)
- `llama-lib/.../InferenceEngine.kt` - Interface with state machine for model loading/inference
- `llama-lib/src/main/cpp/ai_chat.cpp` - JNI bridge implementation
- Builds against llama.cpp submodule via CMake

**Build flags** (configured in `llama-lib/build.gradle.kts`):
- `GGML_NATIVE=OFF` - Disable host-specific optimizations
- `GGML_BACKEND_DL=ON` - Dynamic backend loading
- `GGML_CPU_ALL_VARIANTS=ON` - Support all CPU variants
- KleidiAI enabled for ARM64 optimizations (set in CMakeLists.txt)

### Inference Configuration

`LlmConfig` provides configurable sampling parameters:
- `temperature`, `topP`, `topK`, `repeatPenalty` - Standard LLM sampling
- `contextSize`, `threads`, `gpuLayers` - Resource allocation
- `LlmConfig.forDevice(availableMemoryMb)` - Auto-tuning based on device memory

`PromptTemplate` enum provides preset system prompts: ASSISTANT, CREATIVE, PRECISE, CODING, RUSSIAN.

### Data Flow (Local Mode)

1. User sends message via `ChatScreen`
2. `ChatViewModel` receives `SendMessage` intent
3. `CompositeChatRepository` checks mode preference
4. Routes to `LocalChatRepositoryImpl`
5. `LocalLlmEngine.generate()` streams tokens via Kotlin Flow
6. UI updates reactively with streamed response

## Key Files

- `app/src/main/java/ru/macdroid/ollama/di/AppModule.kt` - DI configuration
- `llama-lib/src/main/cpp/CMakeLists.txt` - Native build configuration
- `docs/adr/001-local-llm-integration.md` - Architecture decision record

## Development Notes

- Target ABI: `arm64-v8a` only (64-bit ARM devices)
- Min SDK: 26, Target/Compile SDK: 36
- NDK Version: 29.0.13113456
- CMake Version: 3.31.5
- Default model: Qwen2-0.5B-Instruct-GGUF (Q4_K_M, ~400MB)
- Models stored in: `context.filesDir/models/`
