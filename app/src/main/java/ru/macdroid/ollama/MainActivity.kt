package ru.macdroid.ollama

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.macdroid.ollama.presentation.chat.ChatScreen
import ru.macdroid.ollama.ui.theme.OllamaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OllamaTheme {
                ChatScreen()
            }
        }
    }
}
