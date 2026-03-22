package com.tx.terminal.data

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manages shell initialization files for /system/bin/sh
 * Creates default .profile and .bashrc equivalents for Android's shell
 */
class ShellInitManager(private val context: Context) {

    companion object {
        private const val TAG = "ShellInitManager"

        // Default shell profile content for Android's /system/bin/sh
        private const val DEFAULT_PROFILE = """# TX Terminal Profile
# This file is sourced by the shell on startup

# Set up the prompt
PS1='\$ '

# Set up aliases
alias ls='ls -F'
alias ll='ls -la'
alias la='ls -a'
alias l='ls -CF'

# Common navigation aliases
alias ..='cd ..'
alias ...='cd ../..'
alias ~='cd ~'

# Safety aliases
alias cp='cp -i'
alias mv='mv -i'
alias rm='rm -i'

# Useful shortcuts
alias h='history'
alias c='clear'
alias q='exit'

# Export common paths
export EDITOR=vi
export VISUAL=vi
export PAGER=less

# Set up history
export HISTSIZE=1000
export HISTFILESIZE=2000

# Color support
if [ -x /system/bin/ls ]; then
    alias ls='ls --color=auto 2>/dev/null || ls -F'
fi

# Welcome message
echo "TX Terminal - Android Shell"
echo "Type 'help' for available commands"
"""

        private const val DEFAULT_BASHRC = """# TX Terminal bashrc
# Additional settings for interactive shells

# Source the profile if it exists
if [ -f ~/.profile ]; then
    . ~/.profile
fi

# Custom functions
help() {
    echo "TX Terminal - Available Commands"
    echo "================================"
    echo "Navigation: cd, pwd, ls, ll, la"
    echo "File ops: cp, mv, rm, cat, touch"
    echo "System: ps, top, df, du, mount"
    echo "Network: ping, netstat, ifconfig"
    echo "Other: clear, exit, history"
    echo ""
    echo "Aliases: .., ..., ~, h, c, q"
}

# Quick directory bookmarking
mark() {
    echo "$(pwd)" > ~/.lastdir
    echo "Marked: $(pwd)"
}

gomark() {
    if [ -f ~/.lastdir ]; then
        cd "$(cat ~/.lastdir)"
    else
        echo "No mark set"
    fi
}
"""

        private const val DEFAULT_INPUTRC = """# TX Terminal input configuration
# Readline settings for line editing

# Enable arrow keys
set editing-mode emacs

# Enable tab completion
set show-all-if-ambiguous on
set completion-ignore-case on

# History search with arrows
"\e[A": history-search-backward
"\e[B": history-search-forward
"""
    }

    /**
     * Initialize shell configuration files in the home directory
     */
    fun initializeShellConfig(homeDir: File) {
        Log.d(TAG, "Initializing shell config in: ${homeDir.absolutePath}")

        // Create .profile
        createInitFile(homeDir, ".profile", DEFAULT_PROFILE)

        // Create .bashrc (for compatibility)
        createInitFile(homeDir, ".bashrc", DEFAULT_BASHRC)

        // Create .inputrc for readline configuration
        createInitFile(homeDir, ".inputrc", DEFAULT_INPUTRC)

        // Create a help file
        createHelpFile(homeDir)
    }

    /**
     * Check if shell config files exist
     */
    fun hasShellConfig(homeDir: File): Boolean {
        return File(homeDir, ".profile").exists()
    }

    /**
     * Create a single init file if it doesn't exist
     */
    private fun createInitFile(homeDir: File, filename: String, content: String) {
        val file = File(homeDir, filename)
        if (!file.exists()) {
            try {
                file.writeText(content)
                Log.d(TAG, "Created $filename")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create $filename", e)
            }
        }
    }

    /**
     * Create a help file with available commands
     */
    private fun createHelpFile(homeDir: File) {
        val helpContent = """# TX Terminal Help

## Navigation Commands
- cd [dir]     - Change directory
- pwd          - Print working directory
- ls [options] - List directory contents
- ll           - List with details (alias)
- la           - List all files including hidden (alias)

## File Operations
- cat [file]   - Display file contents
- cp src dst   - Copy files
- mv src dst   - Move/rename files
- rm [files]   - Remove files
- touch [file] - Create empty file or update timestamp
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
- ~            - Go to home directory
- h            - Show history
- c            - Clear screen
- q            - Exit shell

## Environment Variables
- ${'$'}HOME        - Home directory
- ${'$'}PWD         - Current directory
- ${'$'}PATH        - Executable search path
- ${'$'}SHELL       - Current shell
- ${'$'}TERM        - Terminal type
- ${'$'}TMPDIR      - Temporary directory

## Custom Functions
- help()       - Show this help
- mark()       - Bookmark current directory
- gomark()     - Go to bookmarked directory

## Tips
- Use Tab for command completion
- Use Up/Down arrows for command history
- Use Ctrl+C to interrupt running commands
- Use Ctrl+D to exit the shell
- Use Ctrl+L to clear the screen
"""
        createInitFile(homeDir, "HELP.txt", helpContent)
    }

    /**
     * Get the path to the profile file for sourcing
     */
    fun getProfilePath(homeDir: File): String {
        return File(homeDir, ".profile").absolutePath
    }
}
