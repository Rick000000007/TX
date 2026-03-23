package com.tx.terminal.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
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
import com.tx.terminal.jni.NativeTerminal
import com.tx.terminal.viewmodel.TerminalViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor

/**
 * Terminal rendering surface using AndroidView with a custom canvas-based terminal view
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
        modifier = modifier.background(Color(backgroundColor))
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

class TerminalSurfaceView(context: Context) : View(context) {

    var viewModel: TerminalViewModel? = null
    private var currentSession: TerminalSession? = null

    private var backgroundColorInt: Int = android.graphics.Color.BLACK
    private var foregroundColorInt: Int = android.graphics.Color.WHITE
    private var fontSizeSp: Float = 14f

    private val horizontalPadding = 0f
    private val verticalPadding = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = foregroundColorInt
        textSize = fontSizeSp
    }

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }

    private val selectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = android.graphics.Color.BLACK
        textSize = fontSizeSp
    }

    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(140, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val selectionHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#9E9E9E")
        style = Paint.Style.FILL
    }

    private var renderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var isRendering = false

    @Volatile
    private var renderRequested = true

    @Volatile
    private var cursorVisible = true

    private var blinkScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var ctrlPressed = false
    private var altPressed = false
    private var shiftPressed = false
    private var metaPressed = false

    private var selectionStartX = 0f
    private var selectionStartY = 0f
    private var isSelecting = false
    private var longPressTriggered = false
    private var hasPersistentSelection = false

    private enum class SelectionHandle {
        NONE,
        START,
        END
    }

    private var selectionStartCol = 0
    private var selectionStartRow = 0
    private var selectionEndCol = 0
    private var selectionEndRow = 0
    private var activeHandle = SelectionHandle.NONE
    private var longPressRunnable: Runnable? = null

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val clipboard =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private var cellWidth = 1f
    private var cellHeight = 1f
    private var textAscent = 0f
    private var baselineOffset = 0f
    private var terminalColumns = 80
    private var terminalRows = 24

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        isLongClickable = true
        isEnabled = true
        setWillNotDraw(false)

        recalculateMetrics()

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
        requestFocusFromTouch()
        applyTerminalSize()
        requestRender()
        post {
            requestFocus()
            requestFocusFromTouch()
            showKeyboard()
        }
    }

    fun updateColors(bg: Int, fg: Int) {
        backgroundColorInt = bg
        foregroundColorInt = fg
        paint.color = fg
        setBackgroundColor(bg)
        requestRender()
    }

    fun updateFontSize(size: Float) {
        if (fontSizeSp == size) return
        fontSizeSp = size
        paint.textSize = size
        recalculateMetrics()
        applyTerminalSize()
        requestRender()
    }

    private fun recalculateMetrics() {
    paint.typeface = Typeface.MONOSPACE
    paint.isLinearText = true

    val fm = paint.fontMetrics
    textAscent = fm.ascent
    baselineOffset = -fm.ascent

    cellWidth = paint.measureText("M").coerceAtLeast(1f)
    cellHeight = (fm.descent - fm.ascent + fm.leading).coerceAtLeast(1f)

    selectedTextPaint.typeface = Typeface.MONOSPACE
    selectedTextPaint.textSize = fontSizeSp

    val selectedFm = selectedTextPaint.fontMetrics
    selectedTextPaint.isLinearText = true
}

    private fun applyTerminalSize() {
    if (width <= 0 || height <= 0) return

    val usableWidth = (width - horizontalPadding * 2).coerceAtLeast(1f)
    val usableHeight = (height - verticalPadding * 2).coerceAtLeast(1f)

    val measuredCellWidth = paint.measureText("M").coerceAtLeast(1f)
    val fm = paint.fontMetrics
    val measuredCellHeight = (fm.descent - fm.ascent + fm.leading).coerceAtLeast(1f)

    val newColumns = floor(usableWidth / measuredCellWidth).toInt().coerceAtLeast(2)
    val newRows = floor(usableHeight / measuredCellHeight).toInt().coerceAtLeast(2)

    val sizeChanged = newColumns != terminalColumns || newRows != terminalRows

    terminalColumns = newColumns
    terminalRows = newRows
    cellWidth = measuredCellWidth
    cellHeight = measuredCellHeight
    baselineOffset = -fm.ascent

    if (sizeChanged) {
        viewModel?.resize(terminalColumns, terminalRows)
    }
}

    private fun startRenderLoop() {
        renderScope.launch {
            while (isRendering && isAttachedToWindow) {
                if (renderRequested) {
                    renderRequested = false
                    render()
                }
                delay(16)
            }
        }
    }

    private fun startCursorBlink() {
        blinkScope.launch {
            while (isRendering && isAttachedToWindow) {
                cursorVisible = !cursorVisible
                requestRender()
                delay(530)
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isRendering = true
        renderScope.cancel()
        blinkScope.cancel()
        renderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        blinkScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        startRenderLoop()
        startCursorBlink()
        applyTerminalSize()
    }

    override fun onDetachedFromWindow() {
        isRendering = false
        renderScope.cancel()
        blinkScope.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyTerminalSize()
        requestRender()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(backgroundColorInt)

        val session = currentSession

        for (row in 0 until terminalRows) {
            val top = verticalPadding + (row * cellHeight)
            val baselineY = top + baselineOffset
            val bottom = top + cellHeight

            if (top > height - verticalPadding) break

            val line = session?.getRowText(row).orEmpty()

            for (col in 0 until terminalColumns) {
                val left = horizontalPadding + (col * cellWidth)
                val right = left + cellWidth

                val isSelected = if (session != null) {
                    try {
                        NativeTerminal.isCellSelected(session.getNativeHandle(), col, row)
                    } catch (_: Exception) {
                        false
                    }
                } else {
                    false
                }

                if (isSelected) {
                    canvas.drawRect(left, top, right, bottom, selectionPaint)
                }

                if (col < line.length) {
                    val ch = line[col].toString()
                    val textPaint = if (isSelected) selectedTextPaint else paint
                    val charWidth = textPaint.measureText(ch)
                    val charX = left + ((cellWidth - charWidth) * 0.5f)
                    canvas.drawText(ch, charX, baselineY, textPaint)
                }
            }
        }

        if (session != null) {
            try {
                val rawCursorCol = NativeTerminal.getCursorCol(session.getNativeHandle())
                val cursorRow = NativeTerminal.getCursorRow(session.getNativeHandle())

                if (cursorRow in 0 until terminalRows) {
                    val cursorCol = rawCursorCol.coerceIn(0, terminalColumns)

                    if (cursorCol in 0..terminalColumns) {
                        val left = horizontalPadding + (cursorCol * cellWidth)
                        val top = verticalPadding + (cursorRow * cellHeight)
                        val right = left + cellWidth
                        val bottom = top + cellHeight
                        canvas.drawRect(left, top, right, bottom, cursorPaint)
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (hasPersistentSelection) {
            val handleRadius = (cellHeight * 0.22f).coerceAtLeast(10f)

            val startCx = horizontalPadding + (selectionStartCol * cellWidth)
            val startCy = verticalPadding + (selectionStartRow * cellHeight) + cellHeight
            canvas.drawCircle(startCx, startCy, handleRadius, selectionHandlePaint)

            val endCx = horizontalPadding + ((selectionEndCol + 1) * cellWidth)
            val endCy = verticalPadding + (selectionEndRow * cellHeight) + cellHeight
            canvas.drawCircle(endCx, endCy, handleRadius, selectionHandlePaint)
        }
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType =
            EditorInfo.TYPE_CLASS_TEXT or
            EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions =
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_ACTION_NONE
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!text.isNullOrEmpty()) {
                    currentSession?.sendText(text.toString())
                }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    handleKeyEvent(event)
                }
                return true
            }

            override fun deleteSurroundingText(
                beforeLength: Int,
                afterLength: Int
            ): Boolean {
                currentSession?.sendText("\u007f")
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return handleKeyEvent(event)
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> {
                ctrlPressed = true
                return true
            }

            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                altPressed = true
                return true
            }

            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                shiftPressed = true
                return true
            }

            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> {
                metaPressed = true
                return true
            }
        }

        val modifiers =
            (if (shiftPressed) 1 else 0) or
            (if (ctrlPressed) 2 else 0) or
            (if (altPressed) 4 else 0) or
            (if (metaPressed) 8 else 0)

        when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER -> currentSession?.sendText("\n")
            KeyEvent.KEYCODE_DEL -> currentSession?.sendText("\u007f")
            KeyEvent.KEYCODE_TAB -> currentSession?.sendText("\t")
            KeyEvent.KEYCODE_ESCAPE -> currentSession?.sendText("\u001b")

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> currentSession?.sendKey(event.keyCode, modifiers, true)

            else -> {
                val unicode = event.unicodeChar
                if (unicode != 0) {
                    currentSession?.sendChar(unicode)
                } else {
                    currentSession?.sendKey(event.keyCode, modifiers, true)
                }
            }
        }

        requestRender()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> ctrlPressed = false
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> altPressed = false
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> shiftPressed = false
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> metaPressed = false

            else -> {
                val modifiers =
                    (if (shiftPressed) 1 else 0) or
                    (if (ctrlPressed) 2 else 0) or
                    (if (altPressed) 4 else 0) or
                    (if (metaPressed) 8 else 0)
                currentSession?.sendKey(keyCode, modifiers, false)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                requestFocusFromTouch()

                val tappedHandle = hitTestHandle(event.x, event.y)
                if (tappedHandle != SelectionHandle.NONE) {
                    activeHandle = tappedHandle
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                    return true
                }

                selectionStartX = event.x
                selectionStartY = event.y
                isSelecting = false
                longPressTriggered = false
                activeHandle = SelectionHandle.NONE

                longPressRunnable?.let { removeCallbacks(it) }
                longPressRunnable = Runnable {
                    if (!longPressTriggered && !isSelecting && activeHandle == SelectionHandle.NONE) {
                        longPressTriggered = true
                        vibrate()
                        val (col, row) = screenToCell(selectionStartX, selectionStartY)
                        selectionStartCol = col
                        selectionStartRow = row
                        selectionEndCol = col
                        selectionEndRow = row
                        hasPersistentSelection = true
                        updateNativeSelection()
                        isSelecting = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        invalidate()
                    }
                }
                postDelayed(longPressRunnable, 500)

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeHandle != SelectionHandle.NONE) {
                    longPressRunnable?.let { removeCallbacks(it) }
                    parent?.requestDisallowInterceptTouchEvent(true)
                    applyHandleDrag(event.x, event.y)
                    return true
                }

                val dx = kotlin.math.abs(event.x - selectionStartX)
                val dy = kotlin.math.abs(event.y - selectionStartY)
                if (!longPressTriggered && (dx > 16f || dy > 16f)) {
                    longPressRunnable?.let { removeCallbacks(it) }
                }

                if (longPressTriggered || isSelecting) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    isSelecting = true
                    updateSelection(event.x, event.y)
                    return true
                }

                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { removeCallbacks(it) }
                parent?.requestDisallowInterceptTouchEvent(false)

                if (isSelecting || longPressTriggered || activeHandle != SelectionHandle.NONE) {
                    isSelecting = false
                    activeHandle = SelectionHandle.NONE
                    longPressTriggered = false
                    invalidate()
                    return true
                }

                performClick()
                post {
                    showKeyboard()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startSelection() {
        isSelecting = true
        hasPersistentSelection = true
        Toast.makeText(context, "Selection mode - drag to select", Toast.LENGTH_SHORT).show()
    }

    private fun screenToCell(x: Float, y: Float): Pair<Int, Int> {
        val colBias = cellWidth * 0.5f
        val col = (((x - horizontalPadding + colBias) / cellWidth).toInt())
            .coerceIn(0, terminalColumns - 1)
        val row = (((y - verticalPadding) / cellHeight).toInt())
            .coerceIn(0, terminalRows - 1)
        return col to row
    }

    private fun normalizeSelection() {
        if (selectionStartRow > selectionEndRow ||
            (selectionStartRow == selectionEndRow && selectionStartCol > selectionEndCol)
        ) {
            val sc = selectionStartCol
            val sr = selectionStartRow
            selectionStartCol = selectionEndCol
            selectionStartRow = selectionEndRow
            selectionEndCol = sc
            selectionEndRow = sr

            activeHandle = when (activeHandle) {
                SelectionHandle.START -> SelectionHandle.END
                SelectionHandle.END -> SelectionHandle.START
                SelectionHandle.NONE -> SelectionHandle.NONE
            }
        }
    }

    private fun hitTestHandle(x: Float, y: Float): SelectionHandle {
        if (!hasPersistentSelection) return SelectionHandle.NONE

        val handleRadius = (cellHeight * 0.22f).coerceAtLeast(10f)
        fun near(px: Float, py: Float): Boolean {
            val dx = x - px
            val dy = y - py
            return dx * dx + dy * dy <= handleRadius * handleRadius * 4f
        }

        val startCx = horizontalPadding + (selectionStartCol * cellWidth)
        val startCy = verticalPadding + (selectionStartRow * cellHeight) + cellHeight
        val endCx = horizontalPadding + ((selectionEndCol + 1) * cellWidth)
        val endCy = verticalPadding + (selectionEndRow * cellHeight) + cellHeight

        return when {
            near(startCx, startCy) -> SelectionHandle.START
            near(endCx, endCy) -> SelectionHandle.END
            else -> SelectionHandle.NONE
        }
    }

    private fun applyHandleDrag(x: Float, y: Float) {
        val (col, row) = screenToCell(x, y)
        when (activeHandle) {
            SelectionHandle.START -> {
                selectionStartCol = col
                selectionStartRow = row
            }
            SelectionHandle.END -> {
                selectionEndCol = col
                selectionEndRow = row
            }
            SelectionHandle.NONE -> return
        }
        normalizeSelection()
        updateNativeSelection()
        invalidate()
    }

    private fun updateNativeSelection() {
        currentSession?.let { session ->
            try {
                NativeTerminal.setSelection(
                    session.getNativeHandle(),
                    selectionStartCol,
                    selectionStartRow,
                    selectionEndCol,
                    selectionEndRow
                )
                hasPersistentSelection = true
                requestRender()
            } catch (_: Exception) {
            }
        }
    }

    private fun clearPersistentSelection() {
        currentSession?.let { session ->
            try {
                NativeTerminal.clearSelection(session.getNativeHandle())
            } catch (_: Exception) {
            }
        }
        hasPersistentSelection = false
        activeHandle = SelectionHandle.NONE
    }

    private fun updateSelection(x: Float, y: Float) {
        val (startCol, startRow) = screenToCell(selectionStartX, selectionStartY)
        val (endCol, endRow) = screenToCell(x, y)

        selectionStartCol = startCol
        selectionStartRow = startRow
        selectionEndCol = endCol
        selectionEndRow = endRow
        normalizeSelection()
        updateNativeSelection()
    }

    private fun showKeyboard() {
        requestFocus()
        requestFocusFromTouch()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.restartInput(this)
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun vibrate() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(30)
            }
        }
    }
}
