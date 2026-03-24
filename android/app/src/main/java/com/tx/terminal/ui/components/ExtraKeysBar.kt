package com.tx.terminal.ui.components

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun ExtraKeysBar(
    onKeyPressed: (Int, Int, Boolean) -> Unit,
    onSendText: (String) -> Unit,
    onCtrlC: () -> Unit,
    onCtrlD: () -> Unit,
    onCtrlZ: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onCtrlTap: () -> Unit,
    onCtrlLongPress: () -> Unit
) {
    val scrollState = rememberScrollState()
    var ctrlActive by remember { mutableStateOf(false) }
    var ctrlLocked by remember { mutableStateOf(false) }

    fun sendKeyWithOptionalCtrl(keyCode: Int) {
        val modifiers = if (ctrlActive) 2 else 0
        onKeyPressed(keyCode, modifiers, true)
        onKeyPressed(keyCode, modifiers, false)
        if (ctrlActive && !ctrlLocked) ctrlActive = false
    }

    fun sendTextWithOptionalCtrl(text: String) {
        if (ctrlActive && text.length == 1) {
            val c = text[0]
            val ctrlChar = when (c.uppercaseChar()) {
                '@' -> 0x00
                'A' -> 0x01
                'B' -> 0x02
                'C' -> 0x03
                'D' -> 0x04
                'E' -> 0x05
                'F' -> 0x06
                'G' -> 0x07
                'H' -> 0x08
                'I' -> 0x09
                'J' -> 0x0A
                'K' -> 0x0B
                'L' -> 0x0C
                'M' -> 0x0D
                'N' -> 0x0E
                'O' -> 0x0F
                'P' -> 0x10
                'Q' -> 0x11
                'R' -> 0x12
                'S' -> 0x13
                'T' -> 0x14
                'U' -> 0x15
                'V' -> 0x16
                'W' -> 0x17
                'X' -> 0x18
                'Y' -> 0x19
                'Z' -> 0x1A
                '[' -> 0x1B
                '\\' -> 0x1C
                ']' -> 0x1D
                '^' -> 0x1E
                '_' -> 0x1F
                else -> null
            }
            if (ctrlChar != null) {
                onSendText(ctrlChar.toChar().toString())
                if (!ctrlLocked) ctrlActive = false
                return
            }
        }

        onSendText(text)
        if (ctrlActive && !ctrlLocked) ctrlActive = false
    }

    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExtraKeyToggleButton(
                text = "CTRL",
                isAccent = ctrlActive,
                onClick = {
                    if (ctrlLocked) {
                        ctrlLocked = false
                        ctrlActive = false
                        onCtrlLongPress()
                    } else {
                        ctrlLocked = false
                        ctrlActive = true
                        onCtrlTap()
                    }
                },
                onLongClick = {
                    ctrlLocked = !ctrlLocked
                    ctrlActive = ctrlLocked
                    onCtrlLongPress()
                }
            )

            ExtraKeyButton(
                text = "ESC",
                onClick = { sendTextWithOptionalCtrl("\u001b") }
            )
            ExtraKeyButton(
                text = "TAB",
                onClick = { sendTextWithOptionalCtrl("\t") }
            )

            VerticalSeparator(modifier = Modifier.height(24.dp))

            ExtraKeyIcon(
                icon = Icons.Default.KeyboardArrowUp,
                contentDescription = "Up",
                onClick = { sendKeyWithOptionalCtrl(KeyEvent.KEYCODE_DPAD_UP) }
            )
            ExtraKeyIcon(
                icon = Icons.Default.KeyboardArrowDown,
                contentDescription = "Down",
                onClick = { sendKeyWithOptionalCtrl(KeyEvent.KEYCODE_DPAD_DOWN) }
            )
            ExtraKeyIcon(
                icon = Icons.Default.KeyboardArrowLeft,
                contentDescription = "Left",
                onClick = { sendKeyWithOptionalCtrl(KeyEvent.KEYCODE_DPAD_LEFT) }
            )
            ExtraKeyIcon(
                icon = Icons.Default.KeyboardArrowRight,
                contentDescription = "Right",
                onClick = { sendKeyWithOptionalCtrl(KeyEvent.KEYCODE_DPAD_RIGHT) }
            )

            VerticalSeparator(modifier = Modifier.height(24.dp))

            ExtraKeyButton(
                text = "HOME",
                onClick = { sendKeyWithOptionalCtrl(KeyEvent.KEYCODE_MOVE_HOME) }
            )
            ExtraKeyButton(
                text = "END",
                onClick = { sendKeyWithOptionalCtrl(KeyEvent.KEYCODE_MOVE_END) }
            )
            ExtraKeyButton(
                text = "PGUP",
                onClick = { sendKeyWithOptionalCtrl(KeyEvent.KEYCODE_PAGE_UP) }
            )
            ExtraKeyButton(
                text = "PGDN",
                onClick = { sendKeyWithOptionalCtrl(KeyEvent.KEYCODE_PAGE_DOWN) }
            )

            VerticalSeparator(modifier = Modifier.height(24.dp))

            ExtraKeyIcon(
                icon = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                onClick = {
                    onCopy()
                    if (ctrlActive && !ctrlLocked) ctrlActive = false
                }
            )
            ExtraKeyIcon(
                icon = Icons.Default.ContentPaste,
                contentDescription = "Paste",
                onClick = {
                    onPaste()
                    if (ctrlActive && !ctrlLocked) ctrlActive = false
                }
            )
        }
    }
}

@Composable
private fun ExtraKeyButton(
    text: String,
    onClick: () -> Unit,
    isAccent: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isAccent) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isAccent) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExtraKeyToggleButton(
    text: String,
    isAccent: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(36.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = MaterialTheme.shapes.small,
        color = if (isAccent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (isAccent) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun ExtraKeyIcon(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun VerticalSeparator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
