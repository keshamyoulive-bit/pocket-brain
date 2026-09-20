package com.kesham.pocketbrain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kesham.pocketbrain.ui.theme.PocketBrainTheme

const val START_SCREEN = "start_screen"
const val LOAD_SCREEN = "load_screen"
const val CHAT_SCREEN = "chat_screen"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketBrainTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    val startDestination = intent.getStringExtra("NAVIGATE_TO") ?: START_SCREEN

                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable(START_SCREEN) {
                            SelectionRoute(
                                onModelSelected = {
                                    navController.navigate(LOAD_SCREEN) {
                                        popUpTo(START_SCREEN) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable(LOAD_SCREEN) {
                            LoadingRoute(
                                onModelLoaded = {
                                    navController.navigate(CHAT_SCREEN) {
                                        popUpTo(LOAD_SCREEN) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onGoBack = {
                                    navController.navigate(START_SCREEN) {
                                        popUpTo(LOAD_SCREEN) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable(CHAT_SCREEN) {
                            ChatRoute(
                                onClose = {
                                    navController.navigate(START_SCREEN) {
                                        popUpTo(LOAD_SCREEN) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                })
                        }
                    }
                }
            }
        }
    }
}
