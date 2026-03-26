package com.tx.terminal.bootstrap

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object BootstrapInstaller {

    private const val TAG = "BootstrapInstaller"

    fun installIfNeeded(context: Context) {
        val usrDir = File(context.filesDir, "usr")

        // ✅ REAL CHECK (only skip if sh exists)
        val shFile = File(usrDir, "bin/sh")
        if (shFile.exists()) {
            Log.i(TAG, "Bootstrap already installed (sh exists), skipping")
            return
        }

        Log.i(TAG, "Starting bootstrap installation...")

        // 🔥 CLEAN BROKEN INSTALL
        if (usrDir.exists()) {
            Log.w(TAG, "Deleting broken bootstrap")
            usrDir.deleteRecursively()
        }
        usrDir.mkdirs()

        val inputStream = context.assets.open("bootstrap/bootstrap-aarch64.zip")

        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry

            while (entry != null) {
                val file = File(usrDir, entry.name)

                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { output ->
                        zip.copyTo(output)
                    }

                    // ✅ Correct permissions
                    file.setReadable(true, false)
                    file.setWritable(true, false)
                    file.setExecutable(true, false)
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        // 🔥 CREATE SYMLINKS
        createSymlinks(usrDir)

        Log.i(TAG, "Bootstrap installation completed")
    }

    private fun createSymlinks(usrDir: File) {
        val symlinkFile = File(usrDir, "SYMLINKS.txt")
        if (!symlinkFile.exists()) return

        symlinkFile.forEachLine { line ->
            val parts = line.split("←")
            if (parts.size != 2) return@forEachLine

            val link = File(usrDir, parts[0].trim())
            val target = File(usrDir, parts[1].trim())

            try {
                if (!link.exists()) {
                    link.parentFile?.mkdirs()
                    Runtime.getRuntime().exec(
                        arrayOf("ln", "-s", target.absolutePath, link.absolutePath)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Symlink failed: ${link.absolutePath}", e)
            }
        }
    }
}
