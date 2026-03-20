package com.tx.terminal

import android.app.Application
import android.content.Context
import android.util.Log
import com.tx.terminal.data.TerminalPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TXApplication : Application() {
    
    companion object {
        private const val TAG = "TXApplication"
        lateinit var instance: TXApplication
            private set
    }
    
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var preferences: TerminalPreferences
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
        
        // Initialize native library
        try {
            System.loadLibrary("tx_jni")
            Log.i(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }
}
