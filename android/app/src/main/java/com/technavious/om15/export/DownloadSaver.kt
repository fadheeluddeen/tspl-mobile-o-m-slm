package com.technavious.om15.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

data class SavedFile(val uri: Uri, val displayPath: String)

object DownloadSaver {
    private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun safeName(raw: String): String =
        raw.trim().replace(Regex("""[\\/:*?"<>|]"""), "_").replace(Regex("\\s+"), "_").take(80).ifBlank { "Untitled" }

    fun saveXlsx(context: Context, fileName: String, write: (OutputStream) -> Unit): SavedFile {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, XLSX_MIME)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create file in Downloads")
            try {
                resolver.openOutputStream(uri)?.use(write) ?: throw IllegalStateException("Could not open Downloads file")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            SavedFile(uri, "Download/$fileName")
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
            val file = File(dir, fileName)
            FileOutputStream(file).use(write)
            SavedFile(Uri.fromFile(file), "Download/$fileName")
        }
    }
}
