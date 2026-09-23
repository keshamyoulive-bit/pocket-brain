package com.kesham.pocketbrain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kesham.pocketbrain.ui.theme.PocketBrainTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketBrainTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var showModels by rememberSaveable { mutableStateOf(false) }
                    if (showModels) {
                        ModelManagerRoute(onBack = { showModels = false })
                    } else {
                        ChatRoute(
                            onClose = { finish() },
                            onOpenModels = { showModels = true }
                        )
                    }
                }
            }
        }
    }
}
