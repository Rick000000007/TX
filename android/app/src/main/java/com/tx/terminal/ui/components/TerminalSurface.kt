package com.tx.terminal.ui.components

import android.content.Context
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
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
 * Terminal rendering surface using Android SurfaceView with OpenGL ES rendering
 * 
 * Features:
 * - Hardware-accelerated rendering via native code
 * - Robust soft keyboard input handling
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
 * Custom SurfaceView for terminal rendering
 * 
 * Architecture:
 * - SurfaceView provides the rendering surface
 * - Native code handles OpenGL ES rendering
 * - InputConnection provides robust soft keyboard integration
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val session = currentSession ?: return super.onKeyDown(keyCode, event)

        // Handle special keys
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                session.sendText("\b")
                requestRender()
                return true
            }
            KeyEvent.KEYCODE_FORWARD_DEL -> {
                session.sendText("\u001b[3~") // Delete key sequence
                requestRender()
                return true
            }
            KeyEvent.KEYCODE_ENTER -> {
                session.sendText("\r")
                requestRender()
                return true
            }
            KeyEvent.KEYCODE_TAB -> {
                session.sendText("\t")
                requestRender()
                return true
            }
        }

        val modifiers = buildModifiers(event)
        session.sendKey(keyCode, modifiers, true)

        val unicodeChar = event.getUnicodeChar(event.metaState)
        if (unicodeChar != 0 && !event.isCtrlPressed && !event.isAltPressed) {
            session.sendChar(unicodeChar)
        }

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
