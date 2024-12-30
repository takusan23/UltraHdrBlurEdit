package io.github.takusan23.ultrahdrbluredit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.takusan23.ultrahdrbluredit.ui.theme.UltraHdrBlurEditTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UltraHdrBlurEditTheme {
                EditScreen()
            }
        }
    }
}