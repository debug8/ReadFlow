package net.readflow.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Ділення результатом і історією через **системний Android Share**
 * (`SPEC_ANDROID.md`, 2.1). Живе в шарі інтерфейсу, а не у ViewModel: тут
 * потрібні `Context` та `Intent`, а ViewModel лишається без залежностей від
 * Android.
 *
 * Ніколи не кидає виняток назовні: не поділитися — не привід валити застосунок.
 */
object HistoryShare {

    private const val EXPORTS_DIR = "exports"
    private const val CSV_MIME = "text/csv"
    private const val TEXT_MIME = "text/plain"

    /** Поділитися одним результатом як звичайним текстом. */
    fun shareText(context: Context, subject: String, body: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = TEXT_MIME
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        launchChooser(context, intent, chooserTitle)
    }

    /**
     * Поділитися історією у вигляді `.csv`.
     *
     * Файл пишеться в `cacheDir/exports/` і віддається через FileProvider
     * разовим грантом на читання — жодних файлових дозволів. BOM уже всередині
     * рядка [csv]; пишемо його як UTF-8 байти без змін.
     */
    fun shareCsv(
        context: Context,
        csv: String,
        fileName: String,
        chooserTitle: String
    ) {
        val uri = writeToCache(context, csv, fileName) ?: return

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = CSV_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        launchChooser(context, intent, chooserTitle)
    }

    private fun writeToCache(context: Context, csv: String, fileName: String): Uri? = try {
        val dir = File(context.cacheDir, EXPORTS_DIR).apply { mkdirs() }
        val file = File(dir, fileName)

        file.writeBytes(csv.toByteArray(Charsets.UTF_8))

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: Exception) {
        null
    }

    private fun launchChooser(context: Context, intent: Intent, title: String) {
        try {
            val chooser = Intent.createChooser(intent, title).apply {
                // Застосунок запускає чузер поза Activity-стеком лише коли треба.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            // Немає застосунку, який приймає Share — мовчки нічого не робимо.
        }
    }
}
