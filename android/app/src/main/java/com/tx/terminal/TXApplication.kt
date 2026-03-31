package com.tx.terminal

import android.app.Application
import android.content.Context
import android.util.Log
import com.tx.terminal.data.CommandEnvironmentManager
import com.tx.terminal.data.SessionStateManager
import com.tx.terminal.data.TerminalEnvironment
import com.tx.terminal.data.TerminalPreferences
import com.tx.terminal.data.UserspaceInstaller
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

    lateinit var sessionStateManager: SessionStateManager
        private set

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

        // Initialize session state manager
        sessionStateManager = SessionStateManager(this)

        // Initialize command environment manager
        commandEnvironmentManager = CommandEnvironmentManager(this)

        // Initialize terminal environment
        applicationScope.launch {
                try {
        // 🔥 Step 1: Userspace (BLOCKING)
        UserspaceInstaller(this).installIfNeeded()
        Log.i(TAG, "Userspace installed")

        // 🔥 Step 2: Verify directories
        val success = TerminalEnvironment.verifyDirectories(this@TXApplication)
        if (success) {
        Log.i(TAG, "Terminal environment initialized successfully")
        } else {
        Log.w(TAG, "Some terminal directories could not be created")
        }

        // 🔥 Step 3: Initialize command environment
        commandEnvironmentManager.initialize()
        Log.i(TAG, "Command environment initialized")

        } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize terminal environment", e)
        }
    }

        // Load native library
        try {
            System.loadLibrary("tx_jni")
            Log.i(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }
}
