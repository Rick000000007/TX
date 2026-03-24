package com.tx.terminal.ui.components

import android.view.KeyEvent
import androidx.compose.foundation.background
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.waitForUpOrCancellation
import kotlinx.coroutines.withTimeoutOrNull
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

    fun sendKeyDirect(keyCode: Int) {
        val modifiers = if (ctrlLocked && ctrlActive) 2 else 0
        onKeyPressed(keyCode, modifiers, true)
        onKeyPressed(keyCode, modifiers, false)
    }

    fun sendTextDirect(text: String) {
        onSendText(text)
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
            CtrlKeyButton(
                isActive = ctrlActive,
                onTap = {
                    if (ctrlActive) {
                        ctrlLocked = false
                        ctrlActive = false
                        onCtrlLongPress()
                    } else {
                        ctrlLocked = false
                        ctrlActive = true
                        onCtrlTap()
                    }
                },
                onLongHold = {
                    ctrlLocked = !ctrlLocked
                    ctrlActive = ctrlLocked
                    onCtrlLongPress()
                }
            )

            ExtraKeyButton(
                text = "ESC",
                onClick = { sendTextDirect("\u001b") }
            )
            ExtraKeyButton(
                text = "TAB",
                onClick = { sendTextDirect("\t") }
            )

            VerticalSeparator(modifier = Modifier.height(24.dp))

            ExtraKeyIcon(
                icon = Icons.Default.KeyboardArrowUp,
                contentDescription = "Up",
                onClick = { sendKeyDirect(KeyEvent.KEYCODE_DPAD_UP) }
            )
            ExtraKeyIcon(
                icon = Icons.Default.KeyboardArrowDown,
                contentDescription = "Down",
                onClick = { sendKeyDirect(KeyEvent.KEYCODE_DPAD_DOWN) }
            )
            ExtraKeyIcon(
                icon = Icons.Default.KeyboardArrowLeft,
                contentDescription = "Left",
                onClick = { sendKeyDirect(KeyEvent.KEYCODE_DPAD_LEFT) }
            )
            ExtraKeyIcon(
                icon = Icons.Default.KeyboardArrowRight,
                contentDescription = "Right",
                onClick = { sendKeyDirect(KeyEvent.KEYCODE_DPAD_RIGHT) }
            )

            VerticalSeparator(modifier = Modifier.height(24.dp))

            ExtraKeyButton(
                text = "HOME",
                onClick = { sendKeyDirect(KeyEvent.KEYCODE_MOVE_HOME) }
            )
            ExtraKeyButton(
                text = "END",
                onClick = { sendKeyDirect(KeyEvent.KEYCODE_MOVE_END) }
            )
            ExtraKeyButton(
                text = "PGUP",
                onClick = { sendKeyDirect(KeyEvent.KEYCODE_PAGE_UP) }
            )
            ExtraKeyButton(
                text = "PGDN",
                onClick = { sendKeyDirect(KeyEvent.KEYCODE_PAGE_DOWN) }
            )

            VerticalSeparator(modifier = Modifier.height(24.dp))

            ExtraKeyIcon(
                icon = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                onClick = {
                    onCopy()
                }
            )
            ExtraKeyIcon(
                icon = Icons.Default.ContentPaste,
                contentDescription = "Paste",
                onClick = {
                    onPaste()
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

@Composable
private fun CtrlKeyButton(
    isActive: Boolean,
    onTap: () -> Unit,
    onLongHold: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(36.dp)
            .pointerInput(isActive) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val up = withTimeoutOrNull(2000L) {
                        waitForUpOrCancellation()
                    }
                    if (up == null) {
                        onLongHold()
                        waitForUpOrCancellation()
                    } else {
                        onTap()
                    }
                }
            },
        shape = MaterialTheme.shapes.small,
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (isActive) {
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
                text = "CTRL",
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
