package com.tx.terminal.data

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manages terminal environment setup including:
 * - App-private directory structure (home, tmp, projects)
 * - Environment variables (HOME, PWD, TMPDIR, PATH, SHELL, TERM)
 * - Directory creation and permissions
 */
object TerminalEnvironment {
    private const val TAG = "TerminalEnvironment"

    // Directory names
    const val DIR_HOME = "home"
    const val DIR_TMP = "tmp"
    const val DIR_PROJECTS = "projects"
    const val DIR_BIN = "bin"

    /**
     * Initialize the terminal environment
     * Creates required directories and returns environment variable map
     */
    fun initialize(context: Context): EnvironmentConfig {
        Log.d(TAG, "Initializing terminal environment")

        // Create directory structure
        val homeDir = createDirectory(context, DIR_HOME)
        val tmpDir = createDirectory(context, DIR_TMP)
        val projectsDir = createDirectory(context, DIR_PROJECTS)
        val binDir = createDirectory(context, DIR_BIN)

        // Create shell initialization files
        val shellInitManager = ShellInitManager(context)
        shellInitManager.initializeShellConfig(homeDir)

        // Build environment variables
        val env = buildEnvironmentVariables(
            context = context,
            homeDir = homeDir,
            tmpDir = tmpDir,
            binDir = binDir
        ).toMutableMap()

        // Add ENV variable to source profile
        val profilePath = shellInitManager.getProfilePath(homeDir)
        env["ENV"] = profilePath

        Log.d(TAG, "Environment initialized: HOME=${homeDir.absolutePath}")

        return EnvironmentConfig(
            homeDir = homeDir,
            tmpDir = tmpDir,
            projectsDir = projectsDir,
            binDir = binDir,
            environmentVariables = env,
            workingDirectory = homeDir.absolutePath
        )
    }

    /**
     * Create a subdirectory in app's private files directory
     */
    private fun createDirectory(context: Context, name: String): File {
        val dir = File(context.filesDir, name)
        if (!dir.exists()) {
            val created = dir.mkdirs()
            if (created) {
                Log.d(TAG, "Created directory: ${dir.absolutePath}")
            } else {
                Log.w(TAG, "Failed to create directory: ${dir.absolutePath}")
            }
        } else {
            Log.d(TAG, "Directory already exists: ${dir.absolutePath}")
        }

        if (!dir.canWrite()) {
            Log.w(TAG, "Directory not writable: ${dir.absolutePath}")
        }

        return dir
    }

    /**
     * Build environment variables for shell sessions
     */
    private fun buildEnvironmentVariables(
        context: Context,
        homeDir: File,
        tmpDir: File,
        binDir: File
    ): Map<String, String> {
        val env = mutableMapOf<String, String>()

        // Core terminal environment variables
        env["HOME"] = homeDir.absolutePath
        env["PWD"] = homeDir.absolutePath
        env["TMPDIR"] = tmpDir.absolutePath
        env["SHELL"] = "/system/bin/sh"
        env["TERM"] = "xterm-256color"
        env["COLORTERM"] = "truecolor"

        // Android-specific
        env["ANDROID"] = "1"
        env["ANDROID_ROOT"] = "/system"
        env["ANDROID_DATA"] = "/data"

        // Build PATH with app-private bin directory first, then system paths
        val existingPath = System.getenv("PATH") ?: ""
        val pathBuilder = StringBuilder()

        pathBuilder.append(binDir.absolutePath)
        pathBuilder.append(":/system/bin")
        pathBuilder.append(":/system/xbin")
        pathBuilder.append(":/vendor/bin")

        if (existingPath.isNotEmpty() && existingPath != "/system/bin") {
            val existingPaths = existingPath.split(":").filter {
                it.isNotEmpty() &&
                it != "/system/bin" &&
                it != "/system/xbin" &&
                it != "/vendor/bin"
            }

            if (existingPaths.isNotEmpty()) {
                pathBuilder.append(":")
                pathBuilder.append(existingPaths.joinToString(":"))
            }
        }

        val usrPath = File(context.filesDir, "usr").absolutePath
        env["PATH"] = "$usrPath/bin:/system/bin"

        // User and locale
        env["USER"] = "shell"
        env["LOGNAME"] = "shell"
        env["LANG"] = System.getenv("LANG") ?: "en_US.UTF-8"

        // Editor fallback
        env["EDITOR"] = "vi"
        env["VISUAL"] = "vi"

        // History configuration
        env["HISTFILE"] = File(homeDir, ".bash_history").absolutePath
        env["HISTSIZE"] = "1000"
        env["HISTFILESIZE"] = "2000"

        // Prompt
        env["PS1"] = "\\u@\\h:\\w\\$ "

        return env
    }

    /**
     * Get the working directory for new shell sessions
     * Returns the home directory path
     */
    fun getWorkingDirectory(context: Context): String {
        return File(context.filesDir, DIR_HOME).absolutePath
    }

    /**
     * Ensure all required directories exist
     * Call this on app startup to verify filesystem structure
     */
    fun verifyDirectories(context: Context): Boolean {
        val requiredDirs = listOf(DIR_HOME, DIR_TMP, DIR_PROJECTS, DIR_BIN)
        var allValid = true

        for (dirName in requiredDirs) {
            val dir = File(context.filesDir, dirName)
            if (!dir.exists()) {
                val created = dir.mkdirs()
                if (!created) {
                    Log.e(TAG, "Failed to create required directory: $dirName")
                    allValid = false
                }
            }
            if (!dir.canWrite()) {
                Log.e(TAG, "Required directory not writable: $dirName")
                allValid = false
            }
        }

        return allValid
    }

    /**
     * Convert environment map to array format for JNI
     * Each entry is in "KEY=value" format
     */
    fun toEnvArray(envMap: Map<String, String>): Array<String> {
        return envMap.map { "${it.key}=${it.value}" }.toTypedArray()
    }
}

/**
 * Configuration data class containing all environment setup
 */
data class EnvironmentConfig(
    val homeDir: File,
    val tmpDir: File,
    val projectsDir: File,
    val binDir: File,
    val environmentVariables: Map<String, String>,
    val workingDirectory: String
)
