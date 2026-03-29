package com.tx.terminal.data

import android.content.Context
import java.io.File


object UserspaceInstaller {

    fun setup(context: Context) {
        val filesDir = context.filesDir
        val usrDir = File(filesDir, "usr")
        val binDir = File(usrDir, "bin")

        // Step 1: Always ensure assets copied
        copyAssets(context, "usr", usrDir.absolutePath)

        // Step 2: Setup busybox permissions
        val busybox = File(binDir, "busybox")
        if (busybox.exists()) {
            busybox.setExecutable(true, false)
            busybox.setReadable(true, false)
            busybox.setWritable(true, true)
        }

        // Step 3: Install busybox applets (IMPORTANT: -s)
        try {
            val process = ProcessBuilder(
                busybox.absolutePath,
                "--install",
                "-s",
                binDir.absolutePath
            )
                .redirectErrorStream(true)
                .start()

            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyAssets(context: Context, path: String, destPath: String) {
        val assetManager = context.assets
        val files = assetManager.list(path) ?: return

        val destDir = File(destPath)
        if (!destDir.exists()) destDir.mkdirs()

        for (file in files) {
            val assetPath = "$path/$file"
            val outFile = File(destDir, file)

            val subFiles = assetManager.list(assetPath)

            if (subFiles != null && subFiles.isNotEmpty()) {
                copyAssets(context, assetPath, outFile.absolutePath)
            } else {
                assetManager.open(assetPath).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
