package com.tx.terminal

import android.app.Application
import android.content.Context
import android.util.Log
import com.tx.terminal.data.CommandEnvironmentManager
import com.tx.terminal.data.SessionStateManager
import com.tx.terminal.data.TerminalEnvironment
import com.tx.terminal.data.TerminalPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TXApplication : Application() {
    
    companion object {
        private const val TAG = "TXApplication"
        lateinit var instance: TXApplication
            private set
    }
    
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var preferences: TerminalPreferences
        private set
    
    // Phase 2: Session state manager for persistence
    lateinit var sessionStateManager: SessionStateManager
        private set
    
    // Phase 2: Command environment manager
    lateinit var commandEnvironmentManager: CommandEnvironmentManager
        private set
    
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        instance = this
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "TX Terminal starting...")
        
        // Initialize preferences
        preferences = TerminalPreferences(this)
        
        // Phase 2: Initialize session state manager
        sessionStateManager = SessionStateManager(this)
        
        // Phase 2: Initialize command environment manager
        commandEnvironmentManager = CommandEnvironmentManager(this)
        
        // Initialize terminal environment (create directories)
        applicationScope.launch {
            try {
                val success = TerminalEnvironment.verifyDirectories(this@TXApplication)
                val assetManager = assets
val targetDir = filesDir

fun copyAsset(path: String) {
    val files = assetManager.list(path) ?: return
    for (file in files) {
        val fullPath = if (path.isEmpty()) file else "$path/$file"
        val subFiles = assetManager.list(fullPath)

        if (subFiles != null && subFiles.isNotEmpty()) {
            copyAsset(fullPath)
        } else {
            val outFile = File(targetDir, fullPath.removePrefix("bootstrap/"))
            outFile.parentFile?.mkdirs()

            assetManager.open(fullPath).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            outFile.setExecutable(true)
        }
    }
}
copyAsset("bootstrap")

// call it
copyAsset("bootstrap")
                
                  if (success) {
                    Log.i(TAG, "Terminal environment initialized successfully")
                } else {
                    Log.w(TAG, "Some terminal directories could not be created")
                }
                
                // Phase 2: Initialize command environment
                commandEnvironmentManager.initialize()
                Log.i(TAG, "Command environment initialized")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize terminal environment", e)
            }
        }
        
        // Initialize native library
        try {
            System.loadLibrary("tx_jni")
            Log.i(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }
}
