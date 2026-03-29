package com.tx.terminal.data

import android.content.Context
import android.util.Log
import java.io.File

object UserspaceInstaller {

    private const val TAG = "UserspaceInstaller"

    fun setup(context: Context) {
        val filesDir = context.filesDir
        val usrDir = File(filesDir, "usr")
        val binDir = File(usrDir, "bin")

        // 🔥 Step 1: ALWAYS copy assets (overwrite fix)
        copyAssets(context, "usr", usrDir.absolutePath)

        // 🔥 Step 2: Ensure bin directory exists
        if (!binDir.exists()) {
            binDir.mkdirs()
        }

        // 🔥 Step 3: Fix busybox permissions
        val busybox = File(binDir, "busybox")
        if (busybox.exists()) {
            Log.d(TAG, "Setting busybox permissions")

            busybox.setReadable(true, false)
            busybox.setWritable(true, true)
            busybox.setExecutable(true, false)

        } else {
            Log.e(TAG, "Busybox NOT found at: ${busybox.absolutePath}")
        }

        // 🔥 Step 4: Install busybox applets (symlinks)
        try {
            Log.d(TAG, "Installing busybox applets")

            val process = ProcessBuilder(
                busybox.absolutePath,
                "--install",
                "-s",
                binDir.absolutePath
            )
                .redirectErrorStream(true)
                .start()

            process.waitFor()

            Log.d(TAG, "Busybox install completed")

        } catch (e: Exception) {
            Log.e(TAG, "Busybox install failed", e)
        }
    }

    private fun copyAssets(context: Context, path: String, destPath: String) {
        val assetManager = context.assets
        val files = assetManager.list(path) ?: return

        val destDir = File(destPath)
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        for (file in files) {
            val assetPath = "$path/$file"
            val outFile = File(destDir, file)

            val subFiles = assetManager.list(assetPath)

            if (subFiles != null && subFiles.isNotEmpty()) {
                // 🔁 Recursive copy
                copyAssets(context, assetPath, outFile.absolutePath)
            } else {
                try {
                    assetManager.open(assetPath).use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
