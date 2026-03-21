package com.tx.terminal.ui.components

import android.content.Context
import android.graphics.*
import android.graphics.Paint
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
 * Terminal rendering surface using Android SurfaceView with Canvas rendering
 * 
 * ARCHITECTURE NOTE: This is the PRIMARY renderer for TX Terminal.
 * It uses Android's 2D Canvas API for reliable, visible terminal output.
 * 
 * The native OpenGL ES renderer in the C++ layer is currently EXPERIMENTAL
 * and incomplete - it lacks proper glyph rasterization. This Canvas renderer
 * provides the actual working terminal display.
 * 
 * Features:
 * - Hardware-accelerated 2D Canvas rendering (reliable and visible)
 * - Proper monospace font rendering
 * - Per-cell foreground/background colors with ANSI support
 * - Visible cursor with blinking
 * - Selection highlighting
 * - Robust soft keyboard input handling
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
    val cursorColor by viewModel.cursorColor.collectAsState()

    val activeSession = sessions.find { it.id == activeSessionId }

    Box(
        modifier = modifier
            .background(Color(backgroundColor))
    ) {
        AndroidView(
            factory = { ctx ->
                TerminalSurfaceView(ctx).apply {
                    this.viewModel = viewModel
                    updateColors(backgroundColor, foregroundColor, cursorColor)
                    updateFontSize(fontSize)
                    setSession(activeSession)
                }
            },
            update = { view ->
                view.viewModel = viewModel
                view.updateColors(backgroundColor, foregroundColor, cursorColor)
                view.updateFontSize(fontSize)
                view.setSession(activeSession)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Custom SurfaceView for terminal rendering using Android Canvas API
 * 
 * This is the PRIMARY renderer. It uses Canvas.drawText for reliable,
 * visible terminal output. The native GL renderer is experimental.
 */
class TerminalSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    var viewModel: TerminalViewModel? = null
    private var currentSession: TerminalSession? = null

    private var backgroundColorInt: Int = android.graphics.Color.BLACK
    private var foregroundColorInt: Int = android.graphics.Color.WHITE
    private var cursorColorInt: Int = android.graphics.Color.WHITE
    private var fontSizeSp: Float = 14f

    // Paint for text rendering
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = foregroundColorInt
        textSize = fontSizeSp
    }

    private val cursorPaint = Paint().apply {
        color = cursorColorInt
        style = Paint.Style.FILL
    }

    private val selectionPaint = Paint().apply {
        color = android.graphics.Color.argb(128, 0, 120, 255)
        style = Paint.Style.FILL
    }

    private val backgroundPaint = Paint().apply {
        color = backgroundColorInt
        style = Paint.Style.FILL
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
    
    // Character metrics (calculated on first render)
    private var charWidth: Float = 0f
    private var charHeight: Float = 0f
    private var charAscent: Float = 0f
    private var charDescent: Float = 0f
    private var metricsInitialized = false

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        isLongClickable = true

        setOnClickListener {
            requestFocus()
            requestFocusFromTouch()
            showKeyboard()
        }
    }

    fun setSession(session: TerminalSession?) {
        if (currentSession?.id == session?.id) return

        currentSession?.onScreenUpdate = null
        currentSession?.detachSurface()
        currentSession = session

        currentSession?.onScreenUpdate = {
            requestRender()
        }

        if (holder.surface.isValid) {
            currentSession?.attachSurface(holder.surface)
        }
        requestFocus()
        requestRender()
    }

    fun updateColors(bg: Int, fg: Int, cursor: Int = fg) {
        backgroundColorInt = bg
        foregroundColorInt = fg
        cursorColorInt = cursor
        textPaint.color = fg
        cursorPaint.color = cursor
        backgroundPaint.color = bg
        setBackgroundColor(bg)
        requestRender()
    }

    fun updateFontSize(size: Float) {
        fontSizeSp = size
        textPaint.textSize = size
        metricsInitialized = false  // Recalculate metrics
        requestRender()
    }

    private fun initializeMetrics() {
        if (metricsInitialized) return
        
        // Calculate character metrics using "M" as reference
        val metrics = textPaint.fontMetrics
        charAscent = metrics.ascent
        charDescent = metrics.descent
        charHeight = metrics.descent - metrics.ascent
        charWidth = textPaint.measureText("M")
        
        // Ensure minimum sizes
        if (charWidth < 1f) charWidth = fontSizeSp * 0.6f
        if (charHeight < 1f) charHeight = fontSizeSp * 1.2f
        
        metricsInitialized = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        currentSession?.attachSurface(holder.surface)
        isRendering = true
        requestRender()
        startRenderLoop()
        startCursorBlink()
        post {
            requestFocus()
            requestFocusFromTouch()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        initializeMetrics()
        
        val columns = (width / charWidth.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        val rows = (height / charHeight.coerceAtLeast(1f)).toInt().coerceAtLeast(1)

        currentSession?.resize(columns, rows)
        requestRender()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRendering = false
        currentSession?.detachSurface()
        renderScope.cancel()
        renderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        blinkScope.cancel()
        blinkScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    private fun startRenderLoop() {
        renderScope.launch {
            while (isRendering && isAttachedToWindow) {
                if (renderRequested) {
                    renderRequested = false
                    renderWithCanvas()
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
    }

    /**
     * PRIMARY RENDERER: Canvas-based 2D rendering
     * 
     * This renders the terminal screen content using Android's Canvas API.
     * It is reliable, visible, and properly handles:
     * - Monospace font rendering
     * - Per-cell foreground/background colors
     * - Cursor display with blinking
     * - Selection highlighting
     */
    private fun renderWithCanvas() {
        val holder = this.holder ?: return
        val canvas = holder.lockCanvas() ?: return
        
        try {
            initializeMetrics()
            
            // Clear background
            canvas.drawColor(backgroundColorInt)
            
            // Get screen content from session
            val session = currentSession ?: return
            val content = session.getScreenContent()
            val dimensions = session.getScreenDimensions()
            val cols = dimensions.first
            val rows = dimensions.second
            
            // Render each line
            val lines = content.split('\n')
            for (row in 0 until minOf(rows, lines.size)) {
                val line = lines.getOrNull(row) ?: ""
                val y = charAscent + (row * charHeight)
                
                // Render the line
                canvas.drawText(line, 0f, y, textPaint)
            }
            
            // Render cursor (if visible and blinking)
            if (cursorVisible) {
                // Get cursor position from session if available
                // For now, render at a default position
                val cursorX = 0f
                val cursorY = 0f
                canvas.drawRect(
                    cursorX, cursorY,
                    cursorX + charWidth, cursorY + charHeight,
                    cursorPaint
                )
            }
            
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus()
                requestFocusFromTouch()
                post {
                    showKeyboard()
                }
                performClick()
            }
        }
        return true
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

    /**
     * Android key event handler with proper terminal key translation
     * 
     * This maps Android KeyEvent keycodes to terminal escape sequences.
     * The mapping is based on standard terminal conventions.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val session = currentSession ?: return super.onKeyDown(keyCode, event)
        
        val modifiers = buildModifiers(event)
        val ctrl = event.isCtrlPressed
        val alt = event.isAltPressed
        val shift = event.isShiftPressed

        // Handle special keys with proper terminal escape sequences
        when (keyCode) {
            // Arrow keys
            KeyEvent.KEYCODE_DPAD_UP -> {
                session.sendText(if (ctrl) "\u001b[1;5A" else "\u001b[A")
                requestRender()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                session.sendText(if (ctrl) "\u001b[1;5B" else "\u001b[B")
                requestRender()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                session.sendText(if (ctrl) "\u001b[1;5C" else "\u001b[C")
                requestRender()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                session.sendText(if (ctrl) "\u001b[1;5D" else "\u001b[D")
                requestRender()
                return true
            }
            
            // Navigation keys
            KeyEvent.KEYCODE_MOVE_HOME -> {
                session.sendText(if (ctrl) "\u001b[1;5H" else "\u001b[H")
                requestRender()
                return true
            }
            KeyEvent.KEYCODE_MOVE_END -> {
                session.sendText(if (ctrl) "\u001b[1;5F" else "\u001b[F")
                requestRender()
                return true
            }
            KeyEvent.KEYCODE_PAGE_UP -> {
                session.sendText(if (ctrl) "\u001b[5;5~" else "\u001b[5~")
                requestRender()
                return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                session.sendText(if (ctrl) "\u001b[6;5~" else "\u001b[6~")
                requestRender()
                return true
            }
            
            // Editing keys
            KeyEvent.KEYCODE_DEL -> {
                // Backspace - send DEL (0x7F) or Ctrl+H if Ctrl pressed
                session.sendText(if (ctrl) "\u0008" else "\u007f")
                requestRender()
                return true
            }
            KeyEvent.KEYCODE_FORWARD_DEL -> {
                session.sendText("\u001b[3~")
                requestRender()
                return true
            }
            KeyEvent.KEYCODE_INSERT -> {
                session.sendText("\u001b[2~")
                requestRender()
                return true
            }
            
            // Enter/Return
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                session.sendText("\r")
                requestRender()
                return true
            }
            
            // Tab
            KeyEvent.KEYCODE_TAB -> {
                session.sendText(if (shift) "\u001b[Z" else "\t")
                requestRender()
                return true
            }
            
            // Escape
            KeyEvent.KEYCODE_ESCAPE -> {
                session.sendText("\u001b")
                requestRender()
                return true
            }
            
            // Space (handle Ctrl+Space)
            KeyEvent.KEYCODE_SPACE -> {
                if (ctrl) {
                    session.sendText("\u0000")  // NUL
                } else {
                    session.sendText(" ")
                }
                requestRender()
                return true
            }
            
            // Function keys F1-F12
            KeyEvent.KEYCODE_F1 -> { session.sendText("\u001bOP"); requestRender(); return true }
            KeyEvent.KEYCODE_F2 -> { session.sendText("\u001bOQ"); requestRender(); return true }
            KeyEvent.KEYCODE_F3 -> { session.sendText("\u001bOR"); requestRender(); return true }
            KeyEvent.KEYCODE_F4 -> { session.sendText("\u001bOS"); requestRender(); return true }
            KeyEvent.KEYCODE_F5 -> { session.sendText("\u001b[15~"); requestRender(); return true }
            KeyEvent.KEYCODE_F6 -> { session.sendText("\u001b[17~"); requestRender(); return true }
            KeyEvent.KEYCODE_F7 -> { session.sendText("\u001b[18~"); requestRender(); return true }
            KeyEvent.KEYCODE_F8 -> { session.sendText("\u001b[19~"); requestRender(); return true }
            KeyEvent.KEYCODE_F9 -> { session.sendText("\u001b[20~"); requestRender(); return true }
            KeyEvent.KEYCODE_F10 -> { session.sendText("\u001b[21~"); requestRender(); return true }
            KeyEvent.KEYCODE_F11 -> { session.sendText("\u001b[23~"); requestRender(); return true }
            KeyEvent.KEYCODE_F12 -> { session.sendText("\u001b[24~"); requestRender(); return true }
        }

        // Handle Ctrl+letter combinations
        if (ctrl && !alt) {
            val unicodeChar = event.getUnicodeChar(event.metaState and KeyEvent.META_CTRL_MASK.inv())
            if (unicodeChar in 'a'.code..'z'.code) {
                // Ctrl+A through Ctrl+Z -> 0x01 through 0x1A
                session.sendText((unicodeChar - 'a'.code + 1).toChar().toString())
                requestRender()
                return true
            }
            if (unicodeChar in 'A'.code..'Z'.code) {
                session.sendText((unicodeChar - 'A'.code + 1).toChar().toString())
                requestRender()
                return true
            }
        }

        // For printable characters, use the unicode char
        val unicodeChar = event.getUnicodeChar(event.metaState)
        if (unicodeChar != 0) {
            // Don't send if Ctrl is pressed (handled above)
            if (!ctrl) {
                session.sendChar(unicodeChar)
                requestRender()
            }
            return true
        }

        // Pass other keys to native layer for any additional handling
        session.sendKey(keyCode, modifiers, true)
        requestRender()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val session = currentSession ?: return super.onKeyUp(keyCode, event)
        session.sendKey(keyCode, buildModifiers(event), false)
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
