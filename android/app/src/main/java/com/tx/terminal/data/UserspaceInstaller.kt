package com.tx.terminal.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class UserspaceInstaller(private val context: Context) {

    private val ROOTFS_NAME = "rootfs"

    fun installIfNeeded() {
        val targetDir = File(context.filesDir, ROOTFS_NAME)

        if (targetDir.exists()) {
            return // already installed
        }

        copyAssetsRecursively("rootfs", targetDir)

        fixPermissions(targetDir)
    }

    private fun copyAssetsRecursively(assetPath: String, targetDir: File) {
        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: return

        if (files.isEmpty()) {
            // it's a file
            copyFile(assetPath, targetDir)
        } else {
            // it's a directory
            if (!targetDir.exists()) targetDir.mkdirs()

            for (file in files) {
                val fullPath = if (assetPath.isEmpty()) file else "$assetPath/$file"
                val dest = File(targetDir, file)
                copyAssetsRecursively(fullPath, dest)
            }
        }
    }

    private fun copyFile(assetPath: String, outFile: File) {
        val assetManager = context.assets
        outFile.parentFile?.mkdirs()

        assetManager.open(assetPath).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun fixPermissions(root: File) {
        root.walkTopDown().forEach { file ->
            file.setReadable(true, false)
            file.setExecutable(true, false)
            file.setWritable(true, true)
        }
    }

    fun getRootfsPath(): String {
        return File(context.filesDir, ROOTFS_NAME).absolutePath
    }
}
