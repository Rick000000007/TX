package com.tx.terminal.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tx.terminal.TXApplication
import com.tx.terminal.data.SessionManager
import com.tx.terminal.data.TerminalSession
import com.tx.terminal.data.TerminalPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private const val TAG = "TerminalViewModel"

/**
 * ViewModel for terminal management
 * 
 * Features:
 * - Session lifecycle management
 * - UI state management
 * - Preference integration
 * - Clipboard operations
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {
    
    private val preferences = TXApplication.instance.preferences
    private val sessionManager = SessionManager()
    
    // Expose session manager flows
    val sessions = sessionManager.sessions
    val activeSessionId = sessionManager.activeSessionId
    val activeSession: TerminalSession?
        get() = sessionManager.activeSession
    
    // UI state
    private val _showExtraKeys = MutableStateFlow(true)
    val showExtraKeys: StateFlow<Boolean> = _showExtraKeys.asStateFlow()
    
    private val _isKeyboardVisible = MutableStateFlow(false)
    val isKeyboardVisible: StateFlow<Boolean> = _isKeyboardVisible.asStateFlow()
    
    private val _fontSize = MutableStateFlow(14f)
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()
    
    private val _backgroundColor = MutableStateFlow(0xFF000000.toInt())
    val backgroundColor: StateFlow<Int> = _backgroundColor.asStateFlow()
    
    private val _foregroundColor = MutableStateFlow(0xFFFFFFFF.toInt())
    val foregroundColor: StateFlow<Int> = _foregroundColor.asStateFlow()
    
    private val _cursorColor = MutableStateFlow(0xFFFFFFFF.toInt())
    val cursorColor: StateFlow<Int> = _cursorColor.asStateFlow()
    
    // Error state
    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()
    
    init {
        Log.d(TAG, "TerminalViewModel initialized")
        
        // Collect preferences
        viewModelScope.launch {
            preferences.showExtraKeys.collect { _showExtraKeys.value = it }
        }
        
        viewModelScope.launch {
            preferences.fontSize.collect { _fontSize.value = it }
        }
        
        viewModelScope.launch {
            preferences.backgroundColor.collect { _backgroundColor.value = it }
        }
        
        viewModelScope.launch {
            preferences.foregroundColor.collect { _foregroundColor.value = it }
        }
        
        viewModelScope.launch {
            preferences.cursorColor.collect { _cursorColor.value = it }
        }
        
        // Create initial session
        createSession("Terminal 1")
    }
    
    /**
     * Create a new terminal session
     */
    fun createSession(name: String = "Terminal"): TerminalSession {
        val shellPath = preferences.getShellPathSync()
        return sessionManager.createSession(name, shellPath)
    }
    
    /**
     * Switch to a session
     */
    fun switchToSession(sessionId: String) {
        sessionManager.switchToSession(sessionId)
    }
    
    /**
     * Close a session
     */
    fun closeSession(sessionId: String) {
        sessionManager.closeSession(sessionId)
    }
    
    /**
     * Close all sessions
     */
    fun closeAllSessions() {
        sessionManager.closeAllSessions()
    }
    
    /**
     * Rename a session
     */
    fun renameSession(sessionId: String, newName: String) {
        sessionManager.renameSession(sessionId, newName)
    }
    
    /**
     * Send text to active session
     */
    fun sendText(text: String) {
        activeSession?.sendText(text)
    }
    
    /**
     * Send key event to active session
     */
    fun sendKey(keyCode: Int, modifiers: Int = 0, pressed: Boolean = true) {
        activeSession?.sendKey(keyCode, modifiers, pressed)
    }
    
    /**
     * Send character to active session
     */
    fun sendChar(codepoint: Int) {
        activeSession?.sendChar(codepoint)
    }
    
    /**
     * Send Ctrl+C (interrupt)
     */
    fun sendInterrupt() {
        activeSession?.sendInterrupt()
    }
    
    /**
     * Send Ctrl+D (EOF)
     */
    fun sendEOF() {
        activeSession?.sendEOF()
    }
    
    /**
     * Clear screen
     */
    fun clearScreen() {
        activeSession?.clearScreen()
    }
    
    /**
     * Copy selection to clipboard
     */
    fun copyToClipboard(): String {
        val text = activeSession?.copySelection() ?: ""
        if (text.isNotEmpty()) {
            val clipboard = getApplication<Application>().getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", text))
        }
        return text
    }
    
    /**
     * Paste from clipboard
     */
    fun pasteFromClipboard() {
        val clipboard = getApplication<Application>().getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager
        
        clipboard.primaryClip?.getItemAt(0)?.text?.let { text ->
            activeSession?.paste(text.toString())
        }
    }
    
    /**
     * Toggle extra keys visibility
     */
    fun toggleExtraKeys() {
        _showExtraKeys.value = !_showExtraKeys.value
        viewModelScope.launch {
            preferences.setShowExtraKeys(_showExtraKeys.value)
        }
    }
    
    /**
     * Set keyboard visibility
     */
    fun setKeyboardVisible(visible: Boolean) {
        _isKeyboardVisible.value = visible
    }
    
    /**
     * Increase font size
     */
    fun increaseFontSize() {
        val newSize = (_fontSize.value + 1f).coerceAtMost(32f)
        _fontSize.value = newSize
        viewModelScope.launch {
            preferences.setFontSize(newSize)
        }
    }
    
    /**
     * Decrease font size
     */
    fun decreaseFontSize() {
        val newSize = (_fontSize.value - 1f).coerceAtLeast(8f)
        _fontSize.value = newSize
        viewModelScope.launch {
            preferences.setFontSize(newSize)
        }
    }
    
    /**
     * Reset font size
     */
    fun resetFontSize() {
        _fontSize.value = TerminalPreferences.Defaults.FONT_SIZE
        viewModelScope.launch {
            preferences.setFontSize(TerminalPreferences.Defaults.FONT_SIZE)
        }
    }
    
    /**
     * Resize terminal
     */
    fun resize(columns: Int, rows: Int) {
        activeSession?.resize(columns, rows)
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "TerminalViewModel cleared")
        sessionManager.closeAllSessions()
    }
}
