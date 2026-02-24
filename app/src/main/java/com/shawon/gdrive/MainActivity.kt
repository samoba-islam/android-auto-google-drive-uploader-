package com.shawon.gdrive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shawon.gdrive.ui.screens.MainScreen
import com.shawon.gdrive.ui.theme.GdriveTheme
import com.shawon.gdrive.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GdriveTheme {
                val viewModel: MainViewModel = viewModel()
                MainScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}