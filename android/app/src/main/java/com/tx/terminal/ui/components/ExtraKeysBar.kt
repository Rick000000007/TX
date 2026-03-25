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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Extra keys bar for terminal input
 * Provides easy access to special keys that are hard to type on soft keyboards
 * 
 * Keys provided:
 * - ESC, TAB for control sequences
 * - Arrow keys for navigation
 * - HOME, END, PGUP, PGDN for cursor movement
 * - Ctrl+C, Ctrl+D, Ctrl+Z for signals
 * - Copy/Paste for clipboard operations
 */
@Composable
fun ExtraKeysBar(
    onKeyPressed: (Int, Int) -> Unit,
    onSendText: (String) -> Unit,
    onCtrlC: () -> Unit,
    onCtrlD: () -> Unit,
    onCtrlZ: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit
) {
    val scrollState = rememberScrollState()

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

            ExtraKeyButton(
                text = "CTRL",
                onClick = { },
                isActive = false
            )

            ExtraKeyButton(
                text = "ALT",
                onClick = { }
            )

            // Control keys section
            ExtraKeyButton(
                text = "ESC",
                onClick = { onKeyPressed(KeyEvent.KEYCODE_ESCAPE, 0) }
            )
            ExtraKeyButton(
                text = "TAB",
                onClick = { onKeyPressed(KeyEvent.KEYCODE_TAB, 0) }
            )

            VerticalSeparator(modifier = Modifier.height(24.dp))

            // Arrow keys section
            ExtraKeyIcon(
                icon = Icons.Default.KeyboardArrowUp,
                contentDescription = "Up",
                onClick = { onKeyPressed(KeyEvent.KEYCODE_DPAD_UP, 0) }
            )
            ExtraKeyIcon(
                icon = Icons.Default.KeyboardArrowDown,
                contentDescription = "Down",
                onClick = { onKeyPressed(KeyEvent.KEYCODE_DPAD_DOWN, 0) }
            )
            ExtraKeyIcon(
                icon = Icons.Default.KeyboardArrowLeft,
                contentDescription = "Left",
                onClick = { onKeyPressed(KeyEvent.KEYCODE_DPAD_LEFT, 0) }
            )
            ExtraKeyIcon(
                icon = Icons.Default.KeyboardArrowRight,
                contentDescription = "Right",
                onClick = { onKeyPressed(KeyEvent.KEYCODE_DPAD_RIGHT, 0) }
            )

            VerticalSeparator(modifier = Modifier.height(24.dp))

            // Navigation keys section
            ExtraKeyButton(
                text = "HOME",
                onClick = { onKeyPressed(KeyEvent.KEYCODE_MOVE_HOME, 0) }
            )
            ExtraKeyButton(
                text = "END",
                onClick = { onKeyPressed(KeyEvent.KEYCODE_MOVE_END, 0) }
            )
            ExtraKeyButton(
                text = "PGUP",
                onClick = { onKeyPressed(KeyEvent.KEYCODE_PAGE_UP, 0) }
            )
            ExtraKeyButton(
                text = "PGDN",
                onClick = { onKeyPressed(KeyEvent.KEYCODE_PAGE_DOWN, 0) }
            )

            VerticalSeparator(modifier = Modifier.height(24.dp))

            // Signal keys section (accent color for visibility)
            // Ctrl+L for clear screen
            }, // FF (Ctrl+L)
                isAccent = false
            )

            VerticalSeparator(modifier = Modifier.height(24.dp))

            // Clipboard section
            ExtraKeyIcon(
                icon = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                onClick = onCopy
            )
            ExtraKeyIcon(
                icon = Icons.Default.ContentPaste,
                contentDescription = "Paste",
                onClick = onPaste
            )
        }
    }
}

@Composable
private fun ExtraKeyButton(
    text: String,
    onClick: () -> Unit,
    isActive: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isActive) {
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
