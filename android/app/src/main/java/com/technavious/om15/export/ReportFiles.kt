package com.technavious.om15.export

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

data class ReportFile(val uri: Uri, val name: String, val sizeBytes: Long, val modifiedMillis: Long)

object ReportFiles {

    /** Excel files this app has saved to Download, newest first. */
    fun list(context: Context): List<ReportFile> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val out = mutableListOf<ReportFile>()
            val projection = arrayOf(
                MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE, MediaStore.Downloads.DATE_MODIFIED
            )
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection,
                "${MediaStore.Downloads.MIME_TYPE} = ?", arrayOf(XLSX_MIME),
                "${MediaStore.Downloads.DATE_MODIFIED} DESC"
            )?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val name = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val size = c.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                val date = c.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
                while (c.moveToNext()) {
                    out += ReportFile(
                        ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(id)),
                        c.getString(name), c.getLong(size), c.getLong(date) * 1000
                    )
                }
            }
            return out
        }
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return dir.listFiles { f -> f.name.endsWith(".xlsx", true) }.orEmpty()
            .sortedByDescending(File::lastModified)
            .map { ReportFile(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it), it.name, it.length(), it.lastModified()) }
    }

    /** Permanently removes a report this app saved to Download. */
    fun delete(context: Context, file: ReportFile): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return runCatching { context.contentResolver.delete(file.uri, null, null) > 0 }.getOrDefault(false)
        }
        @Suppress("DEPRECATION")
        val target = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), file.name)
        return target.delete()
    }

    fun share(context: Context, uri: Uri, name: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = XLSX_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name.removeSuffix(".xlsx"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openWith(context: Context, uri: Uri, name: String) {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, XLSX_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(view, "Open $name with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No Excel app installed. Use View to open it inside this app.", Toast.LENGTH_LONG).show()
        }
    }
}
