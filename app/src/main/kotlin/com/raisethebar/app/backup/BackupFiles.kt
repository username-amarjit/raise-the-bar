package com.raisethebar.app.backup

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.raisethebar.app.data.Backup
import com.raisethebar.app.data.BackupRejected
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.LocalDate

/** Export files live in the app's private cache and reach other apps only through a one-off read grant. */
object BackupFiles {
    private const val DIR = "backup"
    private const val BUFFER = 64 * 1024

    /** Writes the export, replacing any earlier one so only the latest copy is kept. */
    fun write(context: Context, text: String, date: LocalDate): File {
        val dir = File(context.cacheDir, DIR)
        dir.deleteRecursively()
        dir.mkdirs()
        return File(dir, "raise-the-bar-backup-$date.json").apply { writeText(text, Charsets.UTF_8) }
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.backup", file)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, file.name)
                clipData = ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        return Intent.createChooser(send, "Save your backup")
    }

    /** Reads a picked file as text, refusing anything bigger than a backup could be. */
    fun read(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri) ?: throw BackupRejected("The file couldn't be opened. Nothing was changed.")
        val bytes = stream.use { it.readAtMost(Backup.MAX_BYTES) }
        if (bytes.size > Backup.MAX_BYTES) throw BackupRejected(Backup.NOT_A_BACKUP)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        while (out.size() <= limit) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
