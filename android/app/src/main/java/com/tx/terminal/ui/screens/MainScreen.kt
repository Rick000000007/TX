package com.tx.terminal.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tx.terminal.ui.components.*
import com.tx.terminal.viewmodel.TerminalViewModel
import kotlinx.coroutines.launch

/**
 * Main screen for TX Terminal
 * Contains the terminal surface, tab bar, extra keys, and navigation drawer
 */

private fun nextTerminalName(existingCount: Int): String = "Terminal ${existingCount + 1}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: TerminalViewModel) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    var terminalSurfaceView by remember { mutableStateOf<TerminalSurfaceView?>(null) }
    
    val sessions by viewModel.sessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val showExtraKeys by viewModel.showExtraKeys.collectAsState()
    val activeSession = viewModel.activeSession

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "TX Terminal",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                Divider()
                
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text("New Session") },
                    selected = false,
                    onClick = {
                        viewModel.createSession(nextTerminalName(sessions.size))
                        scope.launch { drawerState.close() }
                    }
                )
                
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = {
                        showSettingsDialog = true
                        scope.launch { drawerState.close() }
                    }
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    "Sessions",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
                
                sessions.forEach { session ->
                    NavigationDrawerItem(
                        icon = { 
                            Icon(
                                if (session.id == activeSessionId) 
                                    Icons.Default.Terminal 
                                else 
                                    Icons.Default.Circle,
                                contentDescription = null
                            )
                        },
                        label = { Text(session.title.value) },
                        selected = session.id == activeSessionId,
                        onClick = {
                            viewModel.switchToSession(session.id)
                            scope.launch { drawerState.close() }
                        },
                        badge = {
                            if (!session.isRunning.value) {
                                Text("✓")
                            }
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = { 
                        val activeSession = viewModel.activeSession
                        Text("TX Terminal")
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        // Tab switcher
                        if (sessions.size > 1) {
                            IconButton(onClick = { 
                                val currentIndex = sessions.indexOfFirst { it.id == activeSessionId }
                                val nextIndex = (currentIndex + 1) % sessions.size
                                viewModel.switchToSession(sessions[nextIndex].id)
                            }) {
                                Icon(Icons.Default.SwitchLeft, contentDescription = "Switch Tab")
                            }
                        }
                        
                        // More actions
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Session") },
                                onClick = {
                                    viewModel.createSession(nextTerminalName(sessions.size))
                                    menuExpanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Close Session") },
                                onClick = {
                                    activeSessionId?.let { viewModel.closeSession(it) }
                                    menuExpanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Copy") },
                                onClick = {
                                    viewModel.copyToClipboard()
                                    menuExpanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Paste") },
                                onClick = {
                                    viewModel.pasteFromClipboard()
                                    menuExpanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null)
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text(if (showExtraKeys) "Hide Extra Keys" else "Show Extra Keys") },
                                onClick = {
                                    viewModel.toggleExtraKeys()
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    showSettingsDialog = true
                                    menuExpanded = false
                                }
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Terminal view
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    TerminalSurface(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                        onViewReady = { terminalSurfaceView = it }
                    )
                }
                
                // Extra keys
                if (showExtraKeys) {
                    ExtraKeysBar(
                        onKeyPressed = { key, modifiers, pressed ->
                            viewModel.sendKey(key, modifiers, pressed)
                        },
                        onSendText = { text ->
                            viewModel.sendText(text)
                        },
                        onCtrlC = { viewModel.sendInterrupt() },
                        onCtrlD = { viewModel.sendEOF() },
                        onCtrlZ = { viewModel.sendText("\u001a") }, // SUB (Ctrl+Z)
                        onCopy = { viewModel.copyToClipboard() },
                        onPaste = { viewModel.pasteFromClipboard() },
                        onCtrlTap = { terminalSurfaceView?.activateVirtualCtrl(false) },
                        onCtrlLongPress = { terminalSurfaceView?.toggleVirtualCtrlLock() }
                    )
                }
            }
        }
    }
    
    // Dialogs
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }
}
