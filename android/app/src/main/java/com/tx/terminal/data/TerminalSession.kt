package com.tx.terminal.data

import android.content.Context
import android.util.Log
import android.view.Surface
import com.tx.terminal.TXApplication
import com.tx.terminal.jni.NativeTerminal
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents a single terminal session with its own PTY and shell
 * 
 * Features:
 * - Native PTY-backed terminal session with proper environment setup
 * - Automatic screen update notifications
 * - Session lifecycle management
 * - Error handling and recovery
 * - App-private working directory and environment variables
 */
class TerminalSession(
    val id: String,
    val name: String,
    private val shellPath: String = "/system/bin/sh",
    private val initialCommand: String? = null,
    private val columns: Int = 80,
    private val rows: Int = 24,
    private val environmentConfig: EnvironmentConfig? = null
) {
    companion object {
        private const val TAG = "TerminalSession"
        private val sessionCounter = AtomicLong(0)
        
        fun generateId(): String = "session_${sessionCounter.incrementAndGet()}"
        
        /**
         * Create a new terminal session with proper environment setup
         */
        fun createWithEnvironment(
            context: Context,
            name: String = "Terminal",
            shellPath: String = "/system/bin/sh",
            initialCommand: String? = null,
            columns: Int = 80,
            rows: Int = 24
        ): TerminalSession {
            // Initialize terminal environment (creates directories and builds env vars)
            val envConfig = TerminalEnvironment.initialize(context)
            
            return TerminalSession(
                id = generateId(),
                name = name,
                shellPath = shellPath,
                initialCommand = initialCommand,
                columns = columns,
                rows = rows,
                environmentConfig = envConfig
            )
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var nativeHandle: Long = 0
    
    // State flows
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _title = MutableStateFlow(name)
    val title: StateFlow<String> = _title.asStateFlow()
    
    private val _exitCode = MutableStateFlow<Int?>(null)
    val exitCode: StateFlow<Int?> = _exitCode.asStateFlow()
    
    // Error state
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Callback for screen updates
    var onScreenUpdate: (() -> Unit)? = null
    var onTitleChange: ((String) -> Unit)? = null
    var onSessionFinished: ((Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    init {
        Log.d(TAG, "Creating session: $id")
        Log.d("TX_DEBUG", "TerminalSession init id=$id name=$name shell=$shellPath")
    }
    
    /**
     * Initialize the native terminal
     * @return true if initialization succeeded
     */
    fun initialize(): Boolean {
        if (nativeHandle != 0L) {
            Log.w(TAG, "Session already initialized")
            return true
        }
        
        try {
            // Get environment configuration
            val envConfig = environmentConfig
            
            if (envConfig != null) {
                // Use proper environment setup
                nativeHandle = NativeTerminal.createWithEnvironment(
                    columns,
                    rows,
                    shellPath,
                    initialCommand,
                    envConfig.workingDirectory,
                    envConfig.environmentVariables
                )
            } else {
                // Fallback to basic setup (should not happen in normal flow)
                Log.w(TAG, "No environment config provided, using fallback")
                val context = TXApplication.instance
                val fallbackEnv = TerminalEnvironment.initialize(context)
                nativeHandle = NativeTerminal.createWithEnvironment(
                    columns,
                    rows,
                    shellPath,
                    initialCommand,
                    fallbackEnv.workingDirectory,
                    fallbackEnv.environmentVariables
                )
            }
            
            if (nativeHandle == 0L) {
                Log.e(TAG, "Failed to create native terminal")
                _errorMessage.value = "Failed to create terminal session"
                Log.e("TX_DEBUG", "TerminalSession initialize failed: native handle 0")
                onError?.invoke("Failed to create terminal session")
                return false
            }
            
            _isRunning.value = true
            _errorMessage.value = null
            
            // Start the reader coroutine
            startReader()
            
            Log.d(TAG, "Session initialized: $id (handle=$nativeHandle)")
            Log.d("TX_DEBUG", "TerminalSession initialize success handle=$nativeHandle")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception during session initialization", e)
            _errorMessage.value = e.message
            onError?.invoke(e.message ?: "Unknown error")
            return false
        }
    }
    
    /**
     * Start reading output from the PTY
     */
    private fun startReader() {
        scope.launch {
            while (isActive && _isRunning.value) {
                try {
                    // Check if process is still running
                    if (!NativeTerminal.isRunning(nativeHandle)) {
                        val exitCode = NativeTerminal.getExitCode(nativeHandle)
                        _exitCode.value = exitCode
                        _isRunning.value = false
                        onSessionFinished?.invoke(exitCode)
                        break
                    }
                    
                    // Notify UI to redraw
                    onScreenUpdate?.invoke()
                    
                    delay(16) // ~60 FPS
                } catch (e: Exception) {
                    Log.e(TAG, "Error in reader loop", e)
                    delay(100) // Back off on error
                }
            }
        }
    }
    
    fun hasNativeHandle(): Boolean = nativeHandle != 0L
    fun getNativeHandle(): Long = nativeHandle


    /**
     * Attach rendering surface
     */
    fun attachSurface(surface: Surface) {
        if (nativeHandle != 0L) {
            try {
                NativeTerminal.setSurface(nativeHandle, surface)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach surface", e)
            }
        }
    }

    /**
     * Detach rendering surface
     */
    fun detachSurface() {
        if (nativeHandle != 0L) {
            try {
                NativeTerminal.destroySurface(nativeHandle)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to detach surface", e)
            }
        }
    }

    /**
     * Render one frame
     */
    fun render() {
        if (nativeHandle != 0L && _isRunning.value) {
            try {
                NativeTerminal.render(nativeHandle)
            } catch (e: Exception) {
                Log.e(TAG, "Render error", e)
            }
        }
    }

    /**
     * Send text input to the terminal
     */
    fun sendText(text: String) {
        if (nativeHandle != 0L && _isRunning.value) {
            try {
                NativeTerminal.sendText(nativeHandle, text)
                onScreenUpdate?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send text", e)
            }
        }
    }
    
    /**
     * Send a key event
     */
    fun sendKey(keyCode: Int, modifiers: Int, pressed: Boolean) {
        if (nativeHandle != 0L && _isRunning.value) {
            try {
                NativeTerminal.sendKey(nativeHandle, keyCode, modifiers, pressed)
                onScreenUpdate?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send key", e)
            }
        }
    }
    
    /**
     * Send a character
     */
    fun sendChar(codepoint: Int) {
        if (nativeHandle != 0L && _isRunning.value) {
            try {
                NativeTerminal.sendChar(nativeHandle, codepoint)
                onScreenUpdate?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send char", e)
            }
        }
    }
    
    /**
     * Resize the terminal
     */
    fun resize(columns: Int, rows: Int) {
        if (nativeHandle != 0L) {
            try {
                NativeTerminal.resize(nativeHandle, columns, rows)
                onScreenUpdate?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resize", e)
            }
        }
    }
    
    /**
     * Get screen content for debugging
     */
    fun clearSelection() {
        if (nativeHandle != 0L) {
            try {
                NativeTerminal.clearSelection(nativeHandle)
                onScreenUpdate?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear selection", e)
            }
        }
    }

    fun getScreenContent(): String {
        return if (nativeHandle != 0L) {
            try {
                NativeTerminal.getScreenContent(nativeHandle)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get screen content", e)
                ""
            }
        } else {
            ""
        }
    }

    fun getRowText(row: Int): String {
        return if (nativeHandle != 0L) {
            try {
                NativeTerminal.getRowText(nativeHandle, row)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get row text", e)
                ""
            }
        } else {
            ""
        }
    }
    
    /**
     * Get screen dimensions
     */
    fun getScreenDimensions(): Pair<Int, Int> {
        return if (nativeHandle != 0L) {
            try {
                Pair(
                    NativeTerminal.getColumns(nativeHandle),
                    NativeTerminal.getRows(nativeHandle)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get dimensions", e)
                Pair(columns, rows)
            }
        } else {
            Pair(columns, rows)
        }
    }
    
    /**
     * Copy selection to clipboard
     */
    fun copySelection(): String {
        return if (nativeHandle != 0L) {
            try {
                NativeTerminal.copySelection(nativeHandle)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy selection", e)
                ""
            }
        } else {
            ""
        }
    }
    
    /**
     * Paste text
     */
    fun paste(text: String) {
        sendText(text)
    }
    
    /**
     * Update session title
     */
    fun setTitle(newTitle: String) {
        _title.value = newTitle
        onTitleChange?.invoke(newTitle)
    }
    
    /**
     * Finish the session and clean up resources
     */
    fun finish() {
        Log.d(TAG, "Finishing session: $id")
        _isRunning.value = false
        scope.cancel()
        
        if (nativeHandle != 0L) {
            try {
                NativeTerminal.destroy(nativeHandle)
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying session", e)
            }
            nativeHandle = 0
        }
    }
    
    /**
     * Send SIGINT (Ctrl+C)
     */
    fun sendInterrupt() {
        sendText("\u0003") // ETX (Ctrl+C)
    }
    
    /**
     * Send EOF (Ctrl+D)
     */
    fun sendEOF() {
        sendText("\u0004") // EOT (Ctrl+D)
    }
    
    /**
     * Clear the screen
     */
    fun clearScreen() {
        sendText("\u001bc") // ESC c (reset)
    }
}

/**
 * Manager for multiple terminal sessions (tabs)
 */
class SessionManager {
    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()
    
    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()
    
    val activeSession: TerminalSession?
        get() = _sessions.value.find { it.id == _activeSessionId.value }
    
    /**
     * Create a new session with proper environment setup
     */
    fun createSession(
        name: String = "Terminal",
        shellPath: String = "/system/bin/sh",
        initialCommand: String? = null,
        columns: Int = 80,
        rows: Int = 24
    ): TerminalSession? {
        val session = TerminalSession.createWithEnvironment(
            context = TXApplication.instance,
            name = name,
            shellPath = shellPath,
            initialCommand = initialCommand,
            columns = columns,
            rows = rows
        )
        
        // Initialize the session first
        if (!session.initialize()) {
            // Initialization failed - clean up and return null
            Log.e("SessionManager", "Failed to initialize session, removing from list")
            session.finish()
            return null
        }
        
        // Only add to list if initialization succeeded
        _sessions.value += session
        
        // Always switch to the newly created session
        _activeSessionId.value = session.id
        
        return session
    }
    
    /**
     * Switch to a session
     */
    fun switchToSession(sessionId: String) {
        if (_sessions.value.any { it.id == sessionId }) {
            _activeSessionId.value = sessionId
        }
    }
    
    /**
     * Close a session
     */
    fun closeSession(sessionId: String) {
        val session = _sessions.value.find { it.id == sessionId }
        session?.finish()
        
        _sessions.value = _sessions.value.filter { it.id != sessionId }
        
        // Switch to another session if this was active
        if (_activeSessionId.value == sessionId) {
            _activeSessionId.value = _sessions.value.firstOrNull()?.id
        }
    }
    
    /**
     * Close all sessions
     */
    fun closeAllSessions() {
        _sessions.value.forEach { it.finish() }
        _sessions.value = emptyList()
        _activeSessionId.value = null
    }
    
    /**
     * Rename a session
     */
    fun renameSession(sessionId: String, newName: String) {
        _sessions.value.find { it.id == sessionId }?.setTitle(newName)
    }
    
    /**
     * Get session by ID
     */
    fun getSession(sessionId: String): TerminalSession? {
        return _sessions.value.find { it.id == sessionId }
    }
}
