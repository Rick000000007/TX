package com.tx.terminal.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.tx.terminal.ui.screens.MainScreen
import com.tx.terminal.ui.theme.TXTerminalTheme
import com.tx.terminal.viewmodel.TerminalViewModel

class MainActivity : ComponentActivity() {
    
    private val viewModel: TerminalViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("TX_DEBUG", "MainActivity onCreate")
        
        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            TXTerminalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        viewModel.closeAllSessions()
    }
}
