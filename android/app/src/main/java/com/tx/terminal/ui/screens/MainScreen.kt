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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: TerminalViewModel) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    var showNewSessionDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    val sessions by viewModel.sessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val showExtraKeys by viewModel.showExtraKeys.collectAsState()
    val activeSession = viewModel.activeSession
    
    ModalNavigationDrawer(
        drawerState = drawerState,
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
                        showNewSessionDialog = true
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
                        Text((activeSession?.title?.value ?: "TX Terminal") + " DEBUG1")
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
                                    showNewSessionDialog = true
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
                // Tab bar
                if (sessions.size > 1) {
                    TabBar(
                        sessions = sessions,
                        activeSessionId = activeSessionId,
                        onSessionSelected = { viewModel.switchToSession(it) },
                        onSessionClosed = { viewModel.closeSession(it) }
                    )
                }
                
                // Terminal view
                Box(modifier = Modifier.weight(1f)) {
                    TerminalSurface(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                // Extra keys
                if (showExtraKeys) {
                    ExtraKeysBar(
                        onKeyPressed = { key, modifiers ->
                            viewModel.sendKey(key, modifiers, true)
                        },
                        onCtrlC = { viewModel.sendInterrupt() },
                        onCtrlD = { viewModel.sendEOF() },
                        onCopy = { viewModel.copyToClipboard() },
                        onPaste = { viewModel.pasteFromClipboard() }
                    )
                }
            }
        }
    }
    
    // Dialogs
    if (showNewSessionDialog) {
        NewSessionDialog(
            onDismiss = { showNewSessionDialog = false },
            onConfirm = { name ->
                viewModel.createSession(name)
                showNewSessionDialog = false
            }
        )
    }
    
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }
}
