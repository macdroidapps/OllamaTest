# Voice Agent: Speech → LLM → Text

**Дата:** 2026-01-28
**Статус:** Approved
**Автор:** blogsylar

## Цель

Голосовой агент с текстовым выводом. Пользователь говорит голосовую команду → речь распознаётся в текст через whisper.cpp → текст отправляется в LLM → ответ возвращается текстом в чат.

## Ключевые решения

| Решение | Выбор | Обоснование |
|---------|-------|-------------|
| STT движок | whisper.cpp (нативный C++) | Консистентность с llama.cpp, полный офлайн |
| Структура модуля | Отдельный `:whisper-lib` | Разделение ответственности, независимая сборка |
| UX | Автоотправка после распознавания | Настоящий голосовой агент: сказал → получил ответ |
| Управление моделями | Через Settings (аналог LLM) | Консистентный UX, переиспользование паттерна |
| Размеры моделей | tiny (75MB) + base (140MB) | Баланс: быстрый вариант + точный вариант |
| Язык распознавания | Автоопределение Whisper | Меньше настроек, Whisper умеет из коробки |

## Архитектура

```
                   ChatScreen
                   [Mic Button]
                       │
                       ▼
               AudioRecorder          ◄── presentation layer
              (AudioRecord API,
               WAV 16kHz mono)
                       │ audio file path
                       ▼
                WhisperEngine         ◄── domain layer (interface)
                       │
                       ▼
               WhisperEngineImpl      ◄── whisper-lib module
              (JNI → whisper.cpp)
                       │ recognized text
                       ▼
                 ChatViewModel
              SendMessage(text)       ◄── existing flow
                       │
                       ▼
            CompositeChatRepository
                 (LLM response)
```

Новые компоненты:
- **`:whisper-lib`** — Android library модуль с whisper.cpp submodule, CMake, JNI, Kotlin API
- **`AudioRecorder`** — запись с микрофона через `AudioRecord`, WAV 16kHz mono
- **UI** — кнопка микрофона на ChatScreen, настройки модели в SettingsScreen

## Модуль :whisper-lib

### Структура

```
whisper-lib/
├── build.gradle.kts
├── src/main/
│   ├── cpp/
│   │   ├── CMakeLists.txt          # Сборка whisper.cpp
│   │   └── whisper_jni.cpp         # JNI bridge
│   ├── java/ru/macdroid/whisper/
│   │   ├── WhisperEngine.kt        # Публичный интерфейс
│   │   ├── WhisperEngineImpl.kt    # JNI-реализация
│   │   └── WhisperModel.kt         # Enum моделей
│   └── AndroidManifest.xml
```

### Публичный API

```kotlin
interface WhisperEngine {
    val state: StateFlow<WhisperState>

    suspend fun loadModel(modelPath: String)
    suspend fun transcribe(audioPath: String): String
    fun unloadModel()
}

enum class WhisperState {
    Idle,           // модель не загружена
    Loading,        // загрузка модели
    Ready,          // готов к распознаванию
    Transcribing,   // идёт распознавание
    Error           // ошибка
}

enum class WhisperModel(
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long
) {
    Tiny("ggml-tiny.bin", "https://huggingface.co/.../ggml-tiny.bin", 75_000_000),
    Base("ggml-base.bin", "https://huggingface.co/.../ggml-base.bin", 142_000_000)
}
```

## Запись аудио

### AudioRecorder

```kotlin
class AudioRecorder(private val context: Context) {
    val isRecording: StateFlow<Boolean>

    suspend fun startRecording(): String   // возвращает путь к WAV файлу
    fun stopRecording()
}
```

- Используем `AudioRecord` (низкоуровневый API) — полный контроль над форматом
- Whisper требует: 16kHz, 16-bit, mono PCM
- Файлы сохраняются в `context.cacheDir/audio/`, удаляются после транскрибации

### Permission

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

Runtime permission через `rememberLauncherForActivityResult`. При отказе — Snackbar с объяснением.

## Изменения в MVI контракте

### ChatContract

```kotlin
// State — новые поля
data class ChatState(
    // ... существующие поля
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false
)

// Intent — новый
sealed interface ChatIntent {
    // ... существующие
    data object ToggleRecording : ChatIntent
}

// Effect — новый
sealed interface ChatEffect {
    // ... существующие
    data class ShowError(val message: String) : ChatEffect
}
```

### ChatViewModel flow

```
ToggleRecording intent
    │
    ├─ isRecording == false → startRecording()
    │   state.isRecording = true
    │
    └─ isRecording == true → stopRecording()
        state.isRecording = false
        state.isTranscribing = true
        │
        audioPath = audioRecorder.stopRecording()
        text = whisperEngine.transcribe(audioPath)
        deleteAudioFile(audioPath)
        │
        ├─ text не пустой → sendMessage(text)  // существующий метод
        │   state.isTranscribing = false
        │
        └─ text пустой → ShowError("Не удалось распознать речь")
            state.isTranscribing = false
```

## UI: кнопка микрофона

### Расположение

```
┌──────────────────────────────────────────┐
│  [TextField............................]  │
│                              [🎤] [➤]    │
└──────────────────────────────────────────┘
```

### Визуальные состояния

| State | Вид кнопки | Поле ввода |
|-------|-----------|------------|
| Idle | Иконка `mic` | Обычное |
| Recording | Иконка `stop`, красный tint, пульсация | "Говорите..." disabled |
| Transcribing | CircularProgressIndicator | "Распознавание..." disabled |

- Кнопка отправки скрывается во время записи/транскрибации
- Во время `isLoading` (LLM генерирует) кнопка микрофона disabled
- Пульсация: бесконечная анимация `animateFloat` на alpha

## Управление моделями в Settings

### WhisperModelManager

```kotlin
class WhisperModelManager(private val context: Context) {
    val downloadState: StateFlow<DownloadState>

    suspend fun downloadModel(model: WhisperModel)
    fun cancelDownload()
    fun getDownloadedModels(): List<WhisperModel>
    fun getModelPath(model: WhisperModel): String?
    fun deleteModel(model: WhisperModel)
}
```

- Модели хранятся в `context.filesDir/whisper-models/`
- Скачивание через Ktor HttpClient
- Активная модель в `SettingsPreferences` (ключ `whisperModel`)

### UI секция в SettingsScreen

```
┌─ Speech Recognition ─────────────────────┐
│                                           │
│  Model: Tiny (75 MB)          [Download]  │
│  ────────────────── 45% ──────────────    │
│                                           │
│  Model: Base (140 MB)         [Download]  │
│  Status: Not downloaded                   │
│                                           │
└───────────────────────────────────────────┘
```

### SettingsContract — новые элементы

- State: `whisperModels: List<WhisperModelState>`, `activeWhisperModel: WhisperModel?`
- Intent: `DownloadWhisperModel(model)`, `DeleteWhisperModel(model)`, `SelectWhisperModel(model)`

## DI (Koin)

```kotlin
// Whisper — добавляем в AppModule
single { WhisperModelManager(androidContext()) }
single<WhisperEngine> { WhisperEngineImpl() }
single { AudioRecorder(androidContext()) }

// Обновляем ChatViewModel
viewModel { ChatViewModel(get(), get(), get(), get()) }
//                        repo  prefs  whisper  recorder
```

## Порядок реализации

1. whisper.cpp git submodule в корень проекта
2. `:whisper-lib` модуль — CMake, JNI, WhisperEngine
3. AudioRecorder — запись WAV 16kHz mono
4. WhisperModelManager — скачивание/хранение моделей
5. SettingsScreen — секция управления Whisper моделями
6. ChatContract — новые состояния и intents
7. ChatViewModel — toggle recording + transcribe flow
8. ChatScreen — кнопка микрофона с тремя состояниями
9. AndroidManifest — RECORD_AUDIO permission
10. Тестирование — голосовые запросы: «посчитай», «дай определение», «скажи анекдот»

## Вне скоупа

- TTS (Text-to-Speech) — только текстовый вывод
- Continuous listening — только push-to-talk
- История голосовых сообщений — аудио удаляется после транскрибации
- Streaming transcription — whisper работает по целому файлу
