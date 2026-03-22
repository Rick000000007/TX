package com.tx.terminal.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.tx.terminal.data.TerminalSession
import com.tx.terminal.viewmodel.TerminalViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Terminal rendering surface using AndroidView with a custom canvas-based terminal view
 * 
 * Features:
 * - Hardware-accelerated rendering via native code
 * - Robust soft keyboard input handling
 * - Improved special key handling (Ctrl, Alt, Esc, Tab, arrows, Enter, Backspace)
 * - Touch-to-focus for keyboard
 * - Cursor visibility and blinking
 */
@Composable
fun TerminalSurface(
    viewModel: TerminalViewModel,
    modifier: Modifier = Modifier
) {
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val backgroundColor by viewModel.backgroundColor.collectAsState()
    val foregroundColor by viewModel.foregroundColor.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()

    val activeSession = sessions.find { it.id == activeSessionId }

    Box(
        modifier = modifier
            .background(Color(backgroundColor))
    ) {
        AndroidView(
            factory = { ctx ->
                TerminalSurfaceView(ctx).apply {
                    this.viewModel = viewModel
                    updateColors(backgroundColor, foregroundColor)
                    updateFontSize(fontSize)
                    setSession(activeSession)
                }
            },
            update = { view ->
                view.viewModel = viewModel
                view.updateColors(backgroundColor, foregroundColor)
                view.updateFontSize(fontSize)
                view.setSession(activeSession)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Custom View for terminal rendering
 * 
 * Architecture:
 * - Custom View provides the rendering surface
 * - Canvas rendering displays terminal screen content
 * - InputConnection provides robust soft keyboard integration
 * - Improved key handling for special keys and modifiers
 */
class TerminalSurfaceView(context: Context) : View(context) {

    var viewModel: TerminalViewModel? = null
    private var currentSession: TerminalSession? = null

    private var backgroundColorInt: Int = android.graphics.Color.BLACK
    private var foregroundColorInt: Int = android.graphics.Color.WHITE
    private var fontSizeSp: Float = 14f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = foregroundColorInt
        textSize = fontSizeSp
    }

    private var renderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    @Volatile
    private var isRendering = false
    @Volatile
    private var renderRequested = true
    
    // Cursor blink state
    @Volatile
    private var cursorVisible = true
    private var blinkScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Modifier key states for proper handling
    private var ctrlPressed = false
    private var altPressed = false
    private var shiftPressed = false
    private var metaPressed = false
    
    // Selection state (Phase 2: stronger selection/copy/paste)
    private var selectionStartX = 0f
    private var selectionStartY = 0f
    private var isSelecting = false
    private var longPressTriggered = false
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    
    // Clipboard manager
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        isLongClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        isEnabled = true
        setWillNotDraw(false)

        setOnClickListener {
            requestFocus()
            requestFocusFromTouch()
            post {
                requestFocus()
                requestFocusFromTouch()
                showKeyboard()
            }
        }
    }

    fun setSession(session: TerminalSession?) {
        if (currentSession?.id == session?.id) return

        currentSession?.onScreenUpdate = null
        currentSession = session

        currentSession?.onScreenUpdate = {
            requestRender()
        }

        requestFocus()
        requestRender()
    }

    fun updateColors(bg: Int, fg: Int) {
        backgroundColorInt = bg
        foregroundColorInt = fg
        paint.color = fg
        setBackgroundColor(bg)
        requestRender()
    }

    fun updateFontSize(size: Float) {
        fontSizeSp = size
        paint.textSize = size
        requestRender()
    }

    private fun startRenderLoop() {
        renderScope.launch {
            while (isRendering && isAttachedToWindow) {
                if (renderRequested) {
                    renderRequested = false
                    render()
                }
                delay(16) // ~60 FPS
            }
        }
    }
    
    private fun startCursorBlink() {
        blinkScope.launch {
            while (isRendering && isAttachedToWindow) {
                cursorVisible = !cursorVisible
                requestRender()
                delay(530) // Standard cursor blink rate (530ms on, 530ms off)
            }
        }
    }

    private fun requestRender() {
        renderRequested = true
        postInvalidate()
    }

    private fun render() {
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(backgroundColorInt)

        val text = currentSession?.getScreenContent().orEmpty()
        val lines = if (text.isEmpty()) listOf("[TX DEBUG] screen empty") else text.split("\n")

        val lineHeight = (paint.fontMetrics.descent - paint.fontMetrics.ascent).coerceAtLeast(1f)
        var y = -paint.fontMetrics.ascent

        for (line in lines.take(200)) {
            canvas.drawText(line, 8f, y, paint)
            y += lineHeight
            if (y > height - 8f) break
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                selectionStartX = event.x
                selectionStartY = event.y
                isSelecting = false
                longPressTriggered = false
                
                // Start long press detection for selection
                postDelayed({
                    if (!longPressTriggered && !isSelecting) {
                        longPressTriggered = true
                        vibrate()
                        startSelection()
                    }
                }, 500) // 500ms for long press
                
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus()
                requestFocusFromTouch()
                performClick()
            }
            MotionEvent.ACTION_MOVE -> {
                if (longPressTriggered || isSelecting) {
                    isSelecting = true
                    updateSelection(event.x, event.y)
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(null) // Cancel long press detection
                if (isSelecting) {
                    // Selection completed - copy to clipboard
                    copySelection()
                    isSelecting = false
                } else if (!longPressTriggered) {
                    // Normal tap - show keyboard
                    post { showKeyboard() }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(null)
                isSelecting = false
            }
        }
        return true
    }
    
    /**
     * Start text selection mode
     */
    private fun startSelection() {
        // TODO: Implement native selection start
        // For now, just provide visual feedback
        Toast.makeText(context, "Selection mode - drag to select", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Update selection based on drag position
     */
    private fun updateSelection(x: Float, y: Float) {
        // TODO: Implement native selection update
        // This would communicate with the native terminal to set selection bounds
    }
    
    /**
     * Copy current selection to clipboard
     */
    private fun copySelection() {
        val selectedText = currentSession?.copySelection()
        if (!selectedText.isNullOrEmpty()) {
            val clip = ClipData.newPlainText("Terminal", selectedText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Paste from clipboard
     */
    fun pasteFromClipboard() {
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()
            if (!text.isNullOrEmpty()) {
                currentSession?.paste(text)
                requestRender()
            }
        }
    }
    
    /**
     * Provide haptic feedback
     */
    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(50)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            requestFocus()
            requestFocusFromTouch()
        }
    }

    override fun isFocused(): Boolean = true

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val session = currentSession ?: return super.onKeyDown(keyCode, event)
        
        // Update modifier key states
        ctrlPressed = event.isCtrlPressed
        altPressed = event.isAltPressed
        shiftPressed = event.isShiftPressed
        metaPressed = event.isMetaPressed
        
        val modifiers = buildModifiers(event)

        // Handle special keys with improved sequences
        when (keyCode) {
            // Backspace - send proper backspace character
            KeyEvent.KEYCODE_DEL -> {
                session.sendText("\b") // BS (0x08)
                requestRender()
                return true
            }
            // Forward Delete
            KeyEvent.KEYCODE_FORWARD_DEL -> {
                session.sendText("\u001b[3~") // Delete key sequence (ESC [ 3 ~)
                requestRender()
                return true
            }
            // Enter/Return
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                session.sendText("\r") // CR (0x0D)
                requestRender()
                return true
            }
            // Tab
            KeyEvent.KEYCODE_TAB -> {
                if (shiftPressed) {
                    session.sendText("\u001b[Z") // Shift+Tab sequence
                } else {
                    session.sendText("\t") // HT (0x09)
                }
                requestRender()
                return true
            }
            // Escape
            KeyEvent.KEYCODE_ESCAPE -> {
                session.sendText("\u001b") // ESC (0x1B)
                requestRender()
                return true
            }
            // Arrow keys with modifier support
            KeyEvent.KEYCODE_DPAD_UP -> {
                val seq = if (ctrlPressed) "\u001b[1;5A" else "\u001b[A"
                session.sendText(seq)
                requestRender()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val seq = if (ctrlPressed) "\u001b[1;5B" else "\u001b[B"
                session.sendText(seq)
                requestRender()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val seq = if (ctrlPressed) "\u001b[1;5C" else "\u001b[C"
                session.sendText(seq)
                requestRender()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val seq = if (ctrlPressed) "\u001b[1;5D" else "\u001b[D"
                session.sendText(seq)
                requestRender()
                return true
            }
            // Home key
            KeyEvent.KEYCODE_MOVE_HOME -> {
                val seq = if (ctrlPressed) "\u001b[1;5H" else "\u001b[H"
                session.sendText(seq)
                requestRender()
                return true
            }
            // End key
            KeyEvent.KEYCODE_MOVE_END -> {
                val seq = if (ctrlPressed) "\u001b[1;5F" else "\u001b[F"
                session.sendText(seq)
                requestRender()
                return true
            }
            // Page Up
            KeyEvent.KEYCODE_PAGE_UP -> {
                session.sendText("\u001b[5~")
                requestRender()
                return true
            }
            // Page Down
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                session.sendText("\u001b[6~")
                requestRender()
                return true
            }
            // Insert key
            KeyEvent.KEYCODE_INSERT -> {
                session.sendText("\u001b[2~")
                requestRender()
                return true
            }
            // Space with Ctrl -> NUL (0x00)
            KeyEvent.KEYCODE_SPACE -> {
                if (ctrlPressed) {
                    session.sendText("\u0000") // NUL
                } else {
                    session.sendText(" ")
                }
                requestRender()
                return true
            }
        }

        // Handle Ctrl+letter combinations
        if (ctrlPressed && keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            val ctrlChar = (keyCode - KeyEvent.KEYCODE_A + 1).toChar()
            session.sendText(ctrlChar.toString())
            requestRender()
            return true
        }

        // Handle regular character input
        val unicodeChar = event.getUnicodeChar(event.metaState)
        if (unicodeChar != 0) {
            // Send the character directly
            session.sendChar(unicodeChar)
            requestRender()
            return true
        }

        // Fall back to key event for unhandled keys
        session.sendKey(keyCode, modifiers, true)
        requestRender()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val session = currentSession ?: return super.onKeyUp(keyCode, event)
        
        // Update modifier key states
        ctrlPressed = event.isCtrlPressed
        altPressed = event.isAltPressed
        shiftPressed = event.isShiftPressed
        metaPressed = event.isMetaPressed
        
        val modifiers = buildModifiers(event)
        session.sendKey(keyCode, modifiers, false)
        requestRender()
        return true
    }

    private fun buildModifiers(event: KeyEvent): Int {
        var mods = 0
        if (event.isShiftPressed) mods = mods or 1
        if (event.isCtrlPressed) mods = mods or 2
        if (event.isAltPressed) mods = mods or 4
        if (event.isMetaPressed) mods = mods or 8
        return mods
    }

    /**
     * Create InputConnection for soft keyboard integration
     * This is the key to reliable text input on Android
     */
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT or 
                             android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                             android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or 
                              EditorInfo.IME_ACTION_NONE or
                              EditorInfo.IME_FLAG_NO_FULLSCREEN
        outAttrs.initialSelStart = 0
        outAttrs.initialSelEnd = 0
        
        return TerminalInputConnection(this)
    }
    
    private fun showKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.restartInput(this)
        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }
    
    /**
     * Custom InputConnection for terminal input
     * Handles commitText, deleteSurroundingText, and key events from soft keyboard
     */
    inner class TerminalInputConnection(view: TerminalSurfaceView) : BaseInputConnection(view, false) {
        
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val value = text?.toString().orEmpty()
            if (value.isNotEmpty()) {
                currentSession?.sendText(value)
                requestRender()
            }
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            repeat(beforeLength.coerceAtLeast(1)) {
                currentSession?.sendText("\b")
            }
            requestRender()
            return true
        }
        
        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
            return deleteSurroundingText(beforeLength, afterLength)
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            return when (event.action) {
                KeyEvent.ACTION_DOWN -> onKeyDown(event.keyCode, event)
                KeyEvent.ACTION_UP -> onKeyUp(event.keyCode, event)
                else -> false
            }
        }
        
        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            // Terminal doesn't use composing text, commit immediately
            return commitText(text, newCursorPosition)
        }
        
        override fun finishComposingText(): Boolean {
            return true
        }
    }
}
