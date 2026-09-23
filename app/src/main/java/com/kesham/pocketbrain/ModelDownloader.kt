package com.kesham.pocketbrain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Headroom kept free so a download can't fill the device completely. */
private const val STORAGE_SLACK_BYTES = 300L * 1024 * 1024

private const val TAG = "ModelDownloader"

sealed interface DownloadStage {
    data class Downloading(val bytesDone: Long, val totalBytes: Long) : DownloadStage
    data object Verifying : DownloadStage
}

sealed interface DownloadResult {
    data object Success : DownloadResult
    data class Failed(val message: String) : DownloadResult
}

object ModelDownloader {

    /** True on Wi-Fi or any other network the system does not consider metered. */
    fun isUnmetered(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun hasNetwork(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Bytes still needed for [entry], accounting for a partially downloaded file. Negative or
     * zero means the download already fits.
     */
    fun shortfallBytes(context: Context, entry: CatalogEntry): Long {
        val already = ModelStorage.partFile(context, entry.model).length()
        val needed = entry.sizeBytes - already + STORAGE_SLACK_BYTES
        return needed - ModelStorage.freeBytes(context)
    }

    /**
     * Downloads [entry] into private storage, resuming a previous partial file when the server
     * supports it. The file is only moved into place once its size and SHA-256 both match.
     */
    suspend fun download(
        context: Context,
        entry: CatalogEntry,
        onStage: (DownloadStage) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val part = ModelStorage.partFile(context, entry.model)
        val target = ModelStorage.downloadedFile(context, entry.model)

        try {
            var offset = if (part.exists()) part.length() else 0L
            if (offset > entry.sizeBytes) {
                // Partial file is bigger than the real thing, so it is junk.
                part.delete()
                offset = 0L
            }

            if (offset < entry.sizeBytes) {
                val connection = (URL(entry.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
                }
                connection.connect()

                // 206 means the server honoured the range; 200 means it is sending the whole
                // file, so anything already on disk has to be thrown away.
                val resuming = connection.responseCode == HttpURLConnection.HTTP_PARTIAL
                if (!resuming && offset > 0) {
                    Log.i(TAG, "Server ignored Range for ${entry.fileName}, restarting")
                    offset = 0L
                }
                if (connection.responseCode !in 200..299) {
                    connection.disconnect()
                    return@withContext DownloadResult.Failed("Server returned ${connection.responseCode}")
                }

                connection.inputStream.use { input ->
                    FileOutputStream(part, resuming).use { output ->
                        val buffer = ByteArray(1 shl 16)
                        var done = offset
                        onStage(DownloadStage.Downloading(done, entry.sizeBytes))
                        var lastReported = done
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            // Reporting every chunk would swamp the UI on a multi-GB file.
                            if (done - lastReported >= 4L * 1024 * 1024) {
                                lastReported = done
                                onStage(DownloadStage.Downloading(done, entry.sizeBytes))
                            }
                        }
                    }
                }
                connection.disconnect()
            }

            if (part.length() != entry.sizeBytes) {
                return@withContext DownloadResult.Failed(
                    "Size mismatch: got ${part.length()} bytes, expected ${entry.sizeBytes}"
                )
            }

            onStage(DownloadStage.Verifying)
            val actual = sha256Of(part)
            if (!actual.equals(entry.sha256, ignoreCase = true)) {
                part.delete()
                return@withContext DownloadResult.Failed("Checksum mismatch — download discarded")
            }

            target.delete()
            if (!part.renameTo(target)) {
                return@withContext DownloadResult.Failed("Could not move the file into place")
            }
            Log.i(TAG, "Downloaded and verified ${entry.fileName}")
            DownloadResult.Success
        } catch (e: Exception) {
            Log.w(TAG, "Download of ${entry.fileName} failed: ${e.message}", e)
            // The partial file is deliberately kept so the next attempt can resume.
            DownloadResult.Failed(e.localizedMessage ?: "Download failed")
        }
    }

    private suspend fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
