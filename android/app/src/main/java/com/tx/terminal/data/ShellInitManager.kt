package com.tx.terminal.data

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manages shell initialization files for /system/bin/sh
 * Creates safe startup files for Android shell.
 */
class ShellInitManager(private val context: Context) {

    companion object {
        private const val TAG = "ShellInitManager"

        private const val DEFAULT_PROFILE = """# TX Terminal Profile
# This file is sourced by /system/bin/sh on startup

# Simple prompt
PS1='$ '

# Safe aliases
alias ls='toybox ls'
alias ll='ls -la'
alias la='ls -a'
alias l='ls -CF'

alias ..='cd ..'
alias ...='cd ../..'

alias cp='cp -i'
alias mv='mv -i'
alias rm='rm -i'

alias c='clear'
alias q='exit'

export EDITOR=vi
export VISUAL=vi
export PAGER=less
export HISTSIZE=1000
export HISTFILESIZE=2000

echo "TX Terminal - Android Shell"
echo "Type 'cat HELP.txt' for available commands"
"""

        private const val DEFAULT_INPUTRC = """# TX Terminal input configuration
set editing-mode emacs
set show-all-if-ambiguous on
set completion-ignore-case on
"""
    }

    /**
     * Initialize shell configuration files in the home directory
     */
    fun initializeShellConfig(homeDir: File) {
        Log.d(TAG, "Initializing shell config in: ${homeDir.absolutePath}")

        createOrReplaceFile(homeDir, ".profile", DEFAULT_PROFILE)
        createOrReplaceFile(homeDir, ".inputrc", DEFAULT_INPUTRC)
        createOrReplaceFile(homeDir, "HELP.txt", buildHelpText())
    }

    /**
     * Create or replace file content
     */
    private fun createOrReplaceFile(homeDir: File, filename: String, content: String) {
        val file = File(homeDir, filename)
        try {
            file.writeText(content)
            Log.d(TAG, "Wrote $filename")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write $filename", e)
        }
    }

    /**
     * Check if shell config files exist
     */
    fun hasShellConfig(homeDir: File): Boolean {
        return File(homeDir, ".profile").exists()
    }

    /**
     * Create help text
     */
    private fun buildHelpText(): String {
        return """# TX Terminal Help

## Navigation Commands
- cd [dir]     - Change directory
- pwd          - Print working directory
- ls [options] - List directory contents
- ll           - List with details
- la           - List all files including hidden

## File Operations
- cat [file]   - Display file contents
- cp src dst   - Copy files
- mv src dst   - Move or rename files
- rm [files]   - Remove files
- touch [file] - Create empty file
- mkdir [dir]  - Create directory
- rmdir [dir]  - Remove empty directory

## System Commands
- ps           - List processes
- top          - Show system processes
- df           - Show disk free space
- du [dir]     - Show directory usage
- mount        - Show mounted filesystems
- uname -a     - Show system information

## Network Commands
- ping [host]  - Test network connectivity
- netstat      - Show network connections
- ifconfig     - Show network interfaces

## Useful Shortcuts
- ..           - Go to parent directory
- ...          - Go to grandparent directory
- c            - Clear screen
- q            - Exit shell

## Environment Variables
- ${'$'}HOME        - Home directory
- ${'$'}PWD         - Current directory
- ${'$'}PATH        - Executable search path
- ${'$'}SHELL       - Current shell
- ${'$'}TERM        - Terminal type
- ${'$'}TMPDIR      - Temporary directory

## Tips
- Use Tab for command completion
- Use Up or Down arrows for command history
- Use Ctrl+C to interrupt running commands
- Use Ctrl+D to exit the shell
- Use Ctrl+L to clear the screen
"""
    }

    /**
     * Get the path to the profile file for sourcing
     */
    fun getProfilePath(homeDir: File): String {
        return File(homeDir, ".profile").absolutePath
    }
}
