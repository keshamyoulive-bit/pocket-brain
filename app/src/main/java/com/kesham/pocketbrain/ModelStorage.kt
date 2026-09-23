package com.kesham.pocketbrain

import android.content.Context
import android.os.StatFs
import java.io.File

/**
 * Locates model files. A model downloaded in-app lives in private storage; a model side-loaded
 * with push_model.sh lives in [ADB_DIR]. The downloaded copy wins so an in-app download can
 * supersede a stale pushed file, but the pushed path keeps working on its own.
 */
object ModelStorage {

    private const val ADB_DIR = "/data/local/tmp/llm"

    fun downloadDir(context: Context): File =
        File(context.filesDir, "models").apply { mkdirs() }

    fun downloadedFile(context: Context, model: Model): File =
        File(downloadDir(context), model.fileName)

    /** Partial download, renamed onto [downloadedFile] only after it verifies. */
    fun partFile(context: Context, model: Model): File =
        File(downloadDir(context), model.fileName + ".part")

    fun pushedFile(model: Model): File = File(ADB_DIR, model.fileName)

    fun resolve(context: Context, model: Model): File? {
        val downloaded = downloadedFile(context, model)
        if (downloaded.exists()) return downloaded
        val pushed = pushedFile(model)
        if (pushed.exists()) return pushed
        return null
    }

    fun isAvailable(context: Context, model: Model): Boolean = resolve(context, model) != null

    /** True when the resident copy came from push_model.sh, which the app must not delete. */
    fun isPushed(context: Context, model: Model): Boolean =
        !downloadedFile(context, model).exists() && pushedFile(model).exists()

    /** Removes only the downloaded copy; a pushed file is left alone. */
    fun deleteDownload(context: Context, model: Model): Boolean {
        partFile(context, model).delete()
        val downloaded = downloadedFile(context, model)
        return if (downloaded.exists()) downloaded.delete() else false
    }

    fun freeBytes(context: Context): Long =
        StatFs(context.filesDir.absolutePath).availableBytes
}
