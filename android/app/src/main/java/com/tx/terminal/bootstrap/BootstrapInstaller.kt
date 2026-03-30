package com.tx.terminal.bootstrap

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object BootstrapInstaller {

    private const val TAG = "BootstrapInstaller"

    fun installIfNeeded(context: Context) {
        val usrDir = File(context.filesDir, "usr")
        val binDir = File(usrDir, "bin")
        val shFile = File(binDir, "sh")

        // ✅ Skip if already installed
        if (shFile.exists()) {
            Log.i(TAG, "Bootstrap already installed (sh exists)")
            return
        }

        Log.i(TAG, "Installing bootstrap...")

        // 🔥 Clean broken install
        if (usrDir.exists()) {
            Log.w(TAG, "Removing old bootstrap")
            usrDir.deleteRecursively()
        }
        usrDir.mkdirs()

        try {
            // 🔥 Copy assets/usr → files/usr
            copyAssetFolder(context.assets, "usr", usrDir)

            // 🔥 Fix permissions
            fixPermissions(usrDir)

            Log.i(TAG, "Bootstrap installed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Bootstrap installation failed", e)
        }
    }

    // ✅ Recursive asset copy
    private fun copyAssetFolder(
        assetManager: AssetManager,
        src: String,
        dest: File
    ) {
        val files = assetManager.list(src) ?: return

        if (files.isEmpty()) {
            // File
            assetManager.open(src).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            // Directory
            dest.mkdirs()
            for (file in files) {
                copyAssetFolder(
                    assetManager,
                    "$src/$file",
                    File(dest, file)
                )
            }
        }
    }

    // 🔥 Fix executable permissions (VERY IMPORTANT)
    private fun fixPermissions(usrDir: File) {
        val binDir = File(usrDir, "bin")

        binDir.listFiles()?.forEach { file ->
            try {
                file.setReadable(true, false)
                file.setWritable(true, true)
                file.setExecutable(true, false)
            } catch (e: Exception) {
                Log.e(TAG, "Permission fix failed: ${file.absolutePath}", e)
            }
        }
    }
}
