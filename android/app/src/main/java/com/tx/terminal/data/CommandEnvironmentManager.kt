package com.tx.terminal.data

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manages the bundled command environment for TX Terminal
 * 
 * Phase 2 Groundwork:
 * - Sets up directory structure for future bundled tools
 * - Manages PATH for user-installed binaries
 * - Provides foundation for package management
 */
class CommandEnvironmentManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CommandEnvironmentManager"
        
        // Directory names for command environment
        const val DIR_BIN = "bin"
        const val DIR_LIB = "lib"
        const val DIR_ETC = "etc"
        const val DIR_SHARE = "share"
        const val DIR_PACKAGES = "packages"
        const val DIR_MAN = "share/man"
        
        // Package metadata file
        private const val PACKAGE_INDEX = "package_index.json"
    }
    
    /**
     * Initialize the command environment directory structure
     */
    fun initialize(): CommandEnvironment {
        Log.d(TAG, "Initializing command environment")
        
        val baseDir = context.filesDir
        
        // Create directory structure
        val binDir = createDir(baseDir, DIR_BIN)
        val libDir = createDir(baseDir, DIR_LIB)
        val etcDir = createDir(baseDir, DIR_ETC)
        val shareDir = createDir(baseDir, DIR_SHARE)
        val packagesDir = createDir(baseDir, DIR_PACKAGES)
        val manDir = createDir(baseDir, DIR_MAN)
        
        // Create package index if it doesn't exist
        createPackageIndex(packagesDir)
        
        // Create placeholder files for future packages
        createPlaceholderFiles(etcDir, binDir)
        
        Log.d(TAG, "Command environment initialized")
        
        return CommandEnvironment(
            binDir = binDir,
            libDir = libDir,
            etcDir = etcDir,
            shareDir = shareDir,
            packagesDir = packagesDir,
            manDir = manDir
        )
    }
    
    /**
     * Get the bin directory path for PATH
     */
    fun getBinPath(): String {
        return File(context.filesDir, DIR_BIN).absolutePath
    }
    
    /**
     * Check if a command exists in the bundled environment
     */
    fun hasCommand(command: String): Boolean {
        val binDir = File(context.filesDir, DIR_BIN)
        return File(binDir, command).exists() || File(binDir, "$command.sh").exists()
    }
    
    /**
     * Get the full path to a command if it exists
     */
    fun getCommandPath(command: String): String? {
        val binDir = File(context.filesDir, DIR_BIN)
        val directPath = File(binDir, command)
        if (directPath.exists() && directPath.canExecute()) {
            return directPath.absolutePath
        }
        val scriptPath = File(binDir, "$command.sh")
        if (scriptPath.exists()) {
            return scriptPath.absolutePath
        }
        return null
    }
    
    /**
     * Install a wrapper script for a system command
     * This allows adding custom behavior or aliases for system commands
     */
    fun installWrapperScript(name: String, content: String): Boolean {
        return try {
            val binDir = File(context.filesDir, DIR_BIN)
            val scriptFile = File(binDir, name)
            scriptFile.writeText("#!/system/bin/sh\n$content")
            scriptFile.setExecutable(true)
            Log.d(TAG, "Installed wrapper script: $name")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install wrapper script: $name", e)
            false
        }
    }
    
    /**
     * Install a helper script that provides additional functionality
     */
    fun installHelperScript(name: String, content: String): Boolean {
        return installWrapperScript(name, content)
    }
    
    /**
     * Get environment variables for the command environment
     */
      fun getEnvironmentVariables(): Map<String, String> {
    val baseDir = context.filesDir
    val usrDir = File(baseDir, "usr")

    return mapOf(
        // ✅ CORRECT PATH (ONLY BUSYBOX)
        val rootfsBin = File(context.filesDir, "rootfs/bin").absolutePath

        "PATH" to "$rootfsBin:/system/bin"

        // ❌ REMOVE BROKEN LIB USAGE
        // (no LD_LIBRARY_PATH needed for now)

        // ✅ CLEAN ENV (VERY IMPORTANT)
        "HOME" to baseDir.absolutePath,
        "TMPDIR" to context.cacheDir.absolutePath,

        // 🔥 THIS FIXES YOUR ERROR
        "LD_PRELOAD" to "",

        // Optional TX paths (safe)
        "TX_HOME" to baseDir.absolutePath
    )
}     
   
    /**
     * Create a directory if it doesn't exist
     */
    private fun createDir(parent: File, name: String): File {
        val dir = File(parent, name)
        if (!dir.exists()) {
            val created = dir.mkdirs()
            if (created) {
                Log.d(TAG, "Created directory: ${dir.absolutePath}")
            }
        }
        return dir
    }
    
    /**
     * Create the package index file
     */
    private fun createPackageIndex(packagesDir: File) {
        val indexFile = File(packagesDir, PACKAGE_INDEX)
        if (!indexFile.exists()) {
            try {
                val initialIndex = """{
  "version": 1,
  "packages": [],
  "installed": [],
  "repositories": [
    {
      "name": "system",
      "description": "Android system binaries",
      "path": "/system/bin"
    }
  ]
}"""
                indexFile.writeText(initialIndex)
                Log.d(TAG, "Created package index")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create package index", e)
            }
        }
    }
    
    /**
     * Create placeholder files for future package support
     */
    private fun createPlaceholderFiles(etcDir: File, binDir: File) {
        // Create a placeholder PATH configuration
        val pathConfig = File(etcDir, "paths.conf")
        if (!pathConfig.exists()) {
            pathConfig.writeText("""# TX Terminal PATH Configuration
# Additional paths to include in shell PATH

# User-installed binaries
${binDir.absolutePath}
""")
        }
        
        // Create a README for the packages directory
        val packagesDir = File(context.filesDir, DIR_PACKAGES)
        val readme = File(packagesDir, "README.txt")
        if (!readme.exists()) {
            readme.writeText("""# TX Packages Directory

This directory contains bundled and user-installed packages.

## Structure
- bin/ - Executable binaries
- lib/ - Shared libraries
- etc/ - Configuration files
- share/ - Shared data files
- packages/ - Package metadata and sources

## Future Features (Phase 3+)
- Package installation from repositories
- Dependency management
- Auto-updates
""")
        }
    }
}

/**
 * Data class representing the command environment paths
 */
data class CommandEnvironment(
    val binDir: File,
    val libDir: File,
    val etcDir: File,
    val shareDir: File,
    val packagesDir: File,
    val manDir: File
)
