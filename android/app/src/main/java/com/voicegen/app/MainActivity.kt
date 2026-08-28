package com.voicegen.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.voicegen.app.ui.navigation.VoiceGenNavGraph
import com.voicegen.app.ui.theme.VoiceGeneratorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoiceGeneratorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VoiceGenNavGraph()
                }
            }
        }
    }
}
