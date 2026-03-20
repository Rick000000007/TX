package com.tx.terminal.data

import android.content.Context
import android.os.Environment
import android.util.Log
import android.view.Surface
import com.tx.terminal.TXApplication
import com.tx.terminal.jni.NativeTerminal
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents a single terminal session with its own PTY and shell
 * 
 * Features:
 * - Native PTY-backed terminal session
 * - Automatic screen update notifications
 * - Session lifecycle management with proper cleanup
 * - Error handling and recovery
 * - App-private environment setup (scaffolded)
 * 
 * RUNTIME NOTE:
 * Currently uses /system/bin/sh as the shell. App-private runtime
 * environment is scaffolded but not fully implemented. See
 * RuntimeEnvironment class for future extension points.
 */
class TerminalSession(
    val id: String,
    val name: String,
    private val shellPath: String = DEFAULT_SHELL,
    private val initialCommand: String? = null,
    private val columns: Int = DEFAULT_COLUMNS,
    private val rows: Int = DEFAULT_ROWS
) {
    companion object {
        private const val TAG = "TerminalSession"
        private val sessionCounter = AtomicLong(0)
        
        // Default configuration
        const val DEFAULT_SHELL = "/system/bin/sh"
        const val DEFAULT_COLUMNS = 80
        const val DEFAULT_ROWS = 24
        
        fun generateId(): String = "session_${sessionCounter.incrementAndGet()}"
    }
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var nativeHandle: Long = 0
    private val lifecycleLock = Object()
    
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
    
    // Callbacks
    var onScreenUpdate: (() -> Unit)? = null
    var onTitleChange: ((String) -> Unit)? = null
    var onSessionFinished: ((Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    // Runtime environment (scaffolded for future use)
    private val runtimeEnv = RuntimeEnvironment()
    
    init {
        Log.d(TAG, "Creating session: $id")
    }
    
    /**
     * Initialize the native terminal
     * @return true if initialization succeeded
     */
    fun initialize(): Boolean {
        synchronized(lifecycleLock) {
            if (nativeHandle != 0L) {
                Log.w(TAG, "Session already initialized")
                return true
            }
            
            try {
                // Setup runtime environment (scaffolded)
                runtimeEnv.setup()
                
                nativeHandle = NativeTerminal.create(
                    columns,
                    rows,
                    shellPath,
                    initialCommand
                )
                
                if (nativeHandle == 0L) {
                    Log.e(TAG, "Failed to create native terminal")
                    _errorMessage.value = "Failed to create terminal session"
                    onError?.invoke("Failed to create terminal session")
                    return false
                }
                
                _isRunning.value = true
                _errorMessage.value = null
                
                // Start the reader coroutine
                startReader()
                
                Log.d(TAG, "Session initialized: $id (handle=$nativeHandle)")
                return true
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception during session initialization", e)
                _errorMessage.value = e.message
                onError?.invoke(e.message ?: "Unknown error")
                return false
            }
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
                } catch (e: CancellationException) {
                    // Normal cancellation, exit cleanly
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error in reader loop", e)
                    delay(100) // Back off on error
                }
            }
        }
    }
    
    fun hasNativeHandle(): Boolean = synchronized(lifecycleLock) { nativeHandle != 0L }

    /**
     * Attach rendering surface
     */
    fun attachSurface(surface: Surface) {
        synchronized(lifecycleLock) {
            if (nativeHandle != 0L) {
                try {
                    NativeTerminal.setSurface(nativeHandle, surface)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to attach surface", e)
                }
            }
        }
    }

    /**
     * Detach rendering surface
     */
    fun detachSurface() {
        synchronized(lifecycleLock) {
            if (nativeHandle != 0L) {
                try {
                    NativeTerminal.destroySurface(nativeHandle)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to detach surface", e)
                }
            }
        }
    }

    /**
     * Render one frame
     */
    fun render() {
        synchronized(lifecycleLock) {
            if (nativeHandle != 0L && _isRunning.value) {
                try {
                    NativeTerminal.render(nativeHandle)
                } catch (e: Exception) {
                    Log.e(TAG, "Render error", e)
                }
            }
        }
    }

    /**
     * Send text input to the terminal
     */
    fun sendText(text: String) {
        synchronized(lifecycleLock) {
            if (nativeHandle != 0L && _isRunning.value) {
                try {
                    NativeTerminal.sendText(nativeHandle, text)
                    onScreenUpdate?.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send text", e)
                }
            }
        }
    }
    
    /**
     * Send a key event
     */
    fun sendKey(keyCode: Int, modifiers: Int, pressed: Boolean) {
        synchronized(lifecycleLock) {
            if (nativeHandle != 0L && _isRunning.value) {
                try {
                    NativeTerminal.sendKey(nativeHandle, keyCode, modifiers, pressed)
                    onScreenUpdate?.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send key", e)
                }
            }
        }
    }
    
    /**
     * Send a character
     */
    fun sendChar(codepoint: Int) {
        synchronized(lifecycleLock) {
            if (nativeHandle != 0L && _isRunning.value) {
                try {
                    NativeTerminal.sendChar(nativeHandle, codepoint)
                    onScreenUpdate?.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send char", e)
                }
            }
        }
    }
    
    /**
     * Resize the terminal
     */
    fun resize(columns: Int, rows: Int) {
        synchronized(lifecycleLock) {
            if (nativeHandle != 0L) {
                try {
                    NativeTerminal.resize(nativeHandle, columns, rows)
                    onScreenUpdate?.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to resize", e)
                }
            }
        }
    }
    
    /**
     * Get screen content for debugging/rendering
     */
    fun getScreenContent(): String {
        return synchronized(lifecycleLock) {
            if (nativeHandle != 0L) {
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
    }
    
    /**
     * Get screen dimensions
     */
    fun getScreenDimensions(): Pair<Int, Int> {
        return synchronized(lifecycleLock) {
            if (nativeHandle != 0L) {
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
    }
    
    /**
     * Copy selection to clipboard
     */
    fun copySelection(): String {
        return synchronized(lifecycleLock) {
            if (nativeHandle != 0L) {
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
     * This is the proper lifecycle termination method.
     */
    fun finish() {
        Log.d(TAG, "Finishing session: $id")
        
        synchronized(lifecycleLock) {
            _isRunning.value = false
        }
        
        // Cancel all coroutines
        scope.cancel()
        
        synchronized(lifecycleLock) {
            if (nativeHandle != 0L) {
                try {
                    NativeTerminal.destroy(nativeHandle)
                } catch (e: Exception) {
                    Log.e(TAG, "Error destroying session", e)
                }
                nativeHandle = 0
            }
        }
        
        // Cleanup runtime environment
        runtimeEnv.cleanup()
        
        Log.d(TAG, "Session finished: $id")
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
 * Runtime environment scaffolding
 * 
 * This class provides the foundation for an app-private Linux environment.
 * Currently it sets up basic directory structure but does not bundle
 * additional tools or packages.
 * 
 * FUTURE WORK:
 * - Bundle BusyBox or similar toolset
 * - Setup app-private package management
 * - Extended PATH with bundled binaries
 * - Custom shell configurations
 */
class RuntimeEnvironment {
    companion object {
        private const val TAG = "RuntimeEnvironment"
    }
    
    private var isSetup = false
    
    /**
     * Setup the runtime environment
     * Currently creates basic directory structure.
     */
    fun setup() {
        if (isSetup) return
        
        try {
            val context = TXApplication.instance
            
            // Create app-private directories
            val homeDir = getHomeDirectory(context)
            val tmpDir = getTmpDirectory(context)
            
            homeDir.mkdirs()
            tmpDir.mkdirs()
            
            Log.d(TAG, "Runtime environment setup:")
            Log.d(TAG, "  HOME: ${homeDir.absolutePath}")
            Log.d(TAG, "  TMP: ${tmpDir.absolutePath}")
            
            // NOTE: Additional setup (PATH, bundled tools, etc.)
            // is scaffolded for future implementation.
            
            isSetup = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup runtime environment", e)
        }
    }
    
    /**
     * Cleanup the runtime environment
     */
    fun cleanup() {
        // Cleanup tasks if needed
        isSetup = false
    }
    
    /**
     * Get the app-private HOME directory
     */
    fun getHomeDirectory(context: Context): File {
        return File(context.filesDir, "home")
    }
    
    /**
     * Get the app-private TMP directory
     */
    fun getTmpDirectory(context: Context): File {
        return File(context.cacheDir, "tmp")
    }
    
    /**
     * Get the extended PATH (currently just system PATH)
     * FUTURE: Add bundled binary directories
     */
    fun getExtendedPath(): String {
        return System.getenv("PATH") ?: "/system/bin:/system/xbin"
    }
    
    /**
     * Check if a bundled tool exists
     * FUTURE: Check for bundled BusyBox, etc.
     */
    fun hasBundledTool(name: String): Boolean {
        // Currently no bundled tools
        return false
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
     * Create a new session
     */
    fun createSession(
        name: String = "Terminal",
        shellPath: String = TerminalSession.DEFAULT_SHELL,
        initialCommand: String? = null
    ): TerminalSession {
        val session = TerminalSession(
            id = TerminalSession.generateId(),
            name = name,
            shellPath = shellPath,
            initialCommand = initialCommand
        )
        
        _sessions.value += session
        
        // Initialize the session
        if (session.initialize()) {
            // Make it active if it's the first session
            if (_sessions.value.size == 1) {
                _activeSessionId.value = session.id
            }
        }
        
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
