package com.monday8am.koogagent.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class DownloadUnzipWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    // Redirects is needed to download the file from
    // Amazon S3 bucket
    private val client =
        OkHttpClient
            .Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val url =
                inputData.getString(KEY_URL)
                    ?: return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to "URL not provided"))
            val destinationPath =
                inputData.getString(KEY_DESTINATION_PATH)
                    ?: return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Destination path not provided"))
            val requiresUnzip = inputData.getBoolean(KEY_REQUIRES_UNZIP, true)

            val destinationFile = File(destinationPath)
            val parentDir =
                destinationFile.parentFile ?: return@withContext Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to "Could not create destination directory: $destinationFile"),
                )
            if (!parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    return@withContext Result.failure(
                        workDataOf(
                            KEY_ERROR_MESSAGE to "Could not create destination directory: ${parentDir.absolutePath}"
                        ),
                    )
                }
            }

            if (requiresUnzip) {
                // ZIP download flow: download to temp file, extract, delete temp
                // Download uses 0-85% of progress, extraction uses 85-100%
                val zipFile = File(applicationContext.cacheDir, "model-download.zip")
                downloadFile(url, zipFile, scaleForUnzip = true)
                unzipWithProgress(zipFile, parentDir)
                zipFile.delete()
            } else {
                // Direct download flow: download directly to destination (full 0-100%)
                downloadFile(url, destinationFile, scaleForUnzip = false)
            }

            setProgress(workDataOf(KEY_PROGRESS to 100))
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private suspend fun downloadFile(url: String, destFile: File, scaleForUnzip: Boolean = false) {
        val existingBytes = if (destFile.exists()) destFile.length() else 0L

        val requestBuilder = Request.Builder().url(url)
        if (existingBytes > 0) {
            requestBuilder.addHeader("Range", "bytes=$existingBytes-")
        }
        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (response.code == 416) { // Requested Range Not Satisfiable
                // File might be already fully downloaded or server doesn't support the range
                // For safety, let's assume it might be corrupted or complete.
                // If it's the exact same size as the content-length from a fresh request, it's complete.
                // But for now, let's just log and return if it's already there and we can't get more.
                return
            }
            if (!response.isSuccessful) throw IOException("HTTP error ${response.code}")

            val body = response.body
            val isResuming = response.code == 206
            val contentLen = body.contentLength()
            val totalBytes = if (isResuming) contentLen + existingBytes else contentLen.takeIf { it > 0 } ?: -1L

            body.byteStream().use { input ->
                FileOutputStream(destFile, isResuming).use { output ->
                    copyStreamWithProgress(input, output, totalBytes, existingBytes) { progress ->
                        // Scale progress: 0-85% for ZIP downloads, 0-100% for direct downloads
                        val scaledProgress =
                            if (scaleForUnzip) {
                                progress * DOWNLOAD_PROGRESS_WEIGHT
                            } else {
                                progress
                            }
                        setProgress(workDataOf(KEY_PROGRESS to scaledProgress))
                    }
                }
            }
        }
    }

    private suspend fun unzipWithProgress(zipFile: File, targetDir: File) {
        // Calculate total uncompressed size for byte-based progress (more accurate than entry count)
        val totalUncompressedSize = calculateTotalUncompressedSize(zipFile)
        var bytesExtracted = 0L

        ZipInputStream(BufferedInputStream(FileInputStream(zipFile), BUFFER_SIZE)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDir, entry.name)

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        bytesExtracted +=
                            copyStreamWithExtractProgress(
                                zis,
                                fos,
                                bytesExtracted,
                                totalUncompressedSize,
                            )
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun calculateTotalUncompressedSize(zipFile: File): Long {
        var totalSize = 0L
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile), BUFFER_SIZE)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    // Use entry.size if available, otherwise estimate from compressed size
                    totalSize += entry.size.takeIf { it >= 0 } ?: (entry.compressedSize * 2)
                }
                entry = zis.nextEntry
            }
        }
        return totalSize
    }

    private suspend fun copyStreamWithExtractProgress(
        input: InputStream,
        output: OutputStream,
        currentBytesExtracted: Long,
        totalBytes: Long,
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var bytesCopied = 0L
        var lastUpdateProgress = -1f
        var lastUpdateTime = 0L

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            bytesCopied += bytesRead

            if (totalBytes > 0) {
                val currentTime = System.currentTimeMillis()
                val extractProgress = ((currentBytesExtracted + bytesCopied) * 100f) / totalBytes

                // Throttle updates: at least 1% change or 500ms passed
                if (extractProgress - lastUpdateProgress >= 1f || currentTime - lastUpdateTime >= 500L) {
                    val scaledProgress = EXTRACT_PROGRESS_START + (extractProgress * EXTRACT_PROGRESS_WEIGHT)
                    setProgress(workDataOf(KEY_PROGRESS to scaledProgress.coerceAtMost(100f)))
                    lastUpdateProgress = extractProgress
                    lastUpdateTime = currentTime
                }
            }
        }
        return bytesCopied
    }

    private suspend fun copyStreamWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        alreadyCopied: Long,
        onProgress: suspend (Float) -> Unit,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var bytesCopied: Long = alreadyCopied
        var lastUpdateProgress = -1f
        var lastUpdateTime = 0L

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            bytesCopied += bytesRead

            if (totalBytes > 0) {
                val currentTime = System.currentTimeMillis()
                val progress = (bytesCopied * 100).toFloat() / totalBytes.toFloat()

                // Throttle updates: at least 1% change or 500ms passed
                if (progress - lastUpdateProgress >= 1f || currentTime - lastUpdateTime >= 500L) {
                    onProgress(progress)
                    lastUpdateProgress = progress
                    lastUpdateTime = currentTime
                }
            }
        }
    }

    companion object {
        const val KEY_URL = "KEY_URL"
        const val KEY_DESTINATION_PATH = "KEY_DESTINATION_PATH"
        const val KEY_REQUIRES_UNZIP = "KEY_REQUIRES_UNZIP"
        const val KEY_PROGRESS = "KEY_PROGRESS"
        const val KEY_ERROR_MESSAGE = "KEY_ERROR_MESSAGE"

        // Progress weight constants for split progress reporting
        // Download: 0-85%, Extract: 85-100%
        private const val DOWNLOAD_PROGRESS_WEIGHT = 0.85f
        private const val EXTRACT_PROGRESS_START = 85f
        private const val EXTRACT_PROGRESS_WEIGHT = 0.15f
        private const val BUFFER_SIZE = 64 * 1024 // 64KB
    }
}
