package com.tx.terminal.bootstrap

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object BootstrapInstaller {

    fun installIfNeeded(context: Context) {
        val usrDir = File(context.filesDir, "usr")

        // Skip if already installed
        if (usrDir.exists() && usrDir.list()?.isNotEmpty() == true) return

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
                    file.setExecutable(true)
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
