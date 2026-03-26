package com.tx.terminal.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Manages persistent session state for restore/reopen functionality
 * 
 * Features:
 * - Save and restore session metadata
 * - Track session history for reopening
 * - Persist session configuration across app restarts
 */
class SessionStateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SessionStateManager"
        private const val SESSIONS_FILE = "sessions.json"
        private const val PREFS_FILE = "terminal_prefs.json"
    }
    
    private val sessionsFile: File
        get() = File(context.filesDir, SESSIONS_FILE)
    
    private val prefsFile: File
        get() = File(context.filesDir, PREFS_FILE)
    
    // Session restore state
    private val _hasRestorableSessions = MutableStateFlow(false)
    val hasRestorableSessions: StateFlow<Boolean> = _hasRestorableSessions.asStateFlow()
    
    /**
     * Save current session information for potential restoration
     * This stores session metadata, not the actual PTY state (which cannot be persisted)
     */
    fun saveSessionState(sessions: List<TerminalSession>, activeSessionId: String?) {
        try {
            val jsonArray = JSONArray()
            
            sessions.forEach { session ->
                val sessionObj = JSONObject().apply {
                    put("id", session.id)
                    put("name", session.name)
                    put("title", session.title.value)
                    put("shellPath", "/system/bin/sh") // Default shell
                    put("wasActive", session.id == activeSessionId)
                }
                jsonArray.put(sessionObj)
            }
            
            val rootObj = JSONObject().apply {
                put("version", 1)
                put("timestamp", System.currentTimeMillis())
                put("sessions", jsonArray)
                put("activeSessionId", activeSessionId)
            }
            
            sessionsFile.writeText(rootObj.toString(2))
            _hasRestorableSessions.value = sessions.isNotEmpty()
            
            Log.d(TAG, "Saved ${sessions.size} session(s) state")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session state", e)
        }
    }
    
    /**
     * Load saved session information for restoration
     * @return List of session info that can be used to recreate sessions
     */
    fun loadSessionState(): List<RestorableSession> {
        if (!sessionsFile.exists()) {
            return emptyList()
        }
        
        return try {
            val json = JSONObject(sessionsFile.readText())
            val sessionsArray = json.getJSONArray("sessions")
            val sessions = mutableListOf<RestorableSession>()
            
            for (i in 0 until sessionsArray.length()) {
                val obj = sessionsArray.getJSONObject(i)
                sessions.add(
                    RestorableSession(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Terminal"),
                        title = obj.optString("title", "Terminal"),
                        shellPath = obj.optString("shellPath", "/system/bin/sh"),
                        wasActive = obj.optBoolean("wasActive", false)
                    )
                )
            }
            
            _hasRestorableSessions.value = sessions.isNotEmpty()
            Log.d(TAG, "Loaded ${sessions.size} restorable session(s)")
            sessions
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load session state", e)
            emptyList()
        }
    }
    
    /**
     * Clear saved session state
     */
    fun clearSessionState() {
        try {
            if (sessionsFile.exists()) {
                sessionsFile.delete()
            }
            _hasRestorableSessions.value = false
            Log.d(TAG, "Cleared session state")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear session state", e)
        }
    }
    
    /**
     * Save terminal preferences
     */
    fun savePreferences(preferences: TerminalPreferenceData) {
        try {
            val json = JSONObject().apply {
                put("fontSize", preferences.fontSize)
                put("backgroundColor", preferences.backgroundColor)
                put("foregroundColor", preferences.foregroundColor)
                put("cursorColor", preferences.cursorColor)
                put("showExtraKeys", preferences.showExtraKeys)
                put("scrollbackLines", preferences.scrollbackLines)
            }
            prefsFile.writeText(json.toString(2))
            Log.d(TAG, "Saved preferences")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save preferences", e)
        }
    }
    
    /**
     * Load terminal preferences
     */
    fun loadPreferences(): TerminalPreferenceData? {
        if (!prefsFile.exists()) {
            return null
        }
        
        return try {
            val json = JSONObject(prefsFile.readText())
            TerminalPreferenceData(
                fontSize = json.optDouble("fontSize", 14.0).toFloat(),
                backgroundColor = json.optInt("backgroundColor", 0xFF000000.toInt()),
                foregroundColor = json.optInt("foregroundColor", 0xFFFFFFFF.toInt()),
                cursorColor = json.optInt("cursorColor", 0xFFFFFFFF.toInt()),
                showExtraKeys = json.optBoolean("showExtraKeys", true),
                scrollbackLines = json.optInt("scrollbackLines", 10000)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load preferences", e)
            null
        }
    }
    
    /**
     * Check if there are any saved sessions to restore
     */
    fun checkForRestorableSessions(): Boolean {
        val hasSessions = sessionsFile.exists() && sessionsFile.length() > 0
        _hasRestorableSessions.value = hasSessions
        return hasSessions
    }
}

/**
 * Data class representing a restorable session
 */
data class RestorableSession(
    val id: String,
    val name: String,
    val title: String,
    val shellPath: String,
    val wasActive: Boolean
)

/**
 * Terminal preference data for persistence
 */
data class TerminalPreferenceData(
    val fontSize: Float = 14f,
    val backgroundColor: Int = 0xFF000000.toInt(),
    val foregroundColor: Int = 0xFFFFFFFF.toInt(),
    val cursorColor: Int = 0xFFFFFFFF.toInt(),
    val showExtraKeys: Boolean = true,
    val scrollbackLines: Int = 10000
)
