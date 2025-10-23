package com.monday8am.koogagent.mediapipe.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

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

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            try {
                val url =
                    inputData.getString(KEY_URL)
                        ?: return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to "URL not provided"))
                val destinationPath =
                    inputData.getString(KEY_DESTINATION_PATH)
                        ?: return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Destination path not provided"))

                val destinationFile = File(destinationPath)
                val parentDir = destinationFile.parentFile
                if (parentDir == null) {
                    return@withContext Result.failure(
                        workDataOf(KEY_ERROR_MESSAGE to "Could not create destination directory: $destinationFile"),
                    )
                }
                if (!parentDir.exists()) {
                    if (!parentDir.mkdirs()) {
                        return@withContext Result.failure(
                            workDataOf(KEY_ERROR_MESSAGE to "Could not create destination directory: ${parentDir.absolutePath}"),
                        )
                    }
                }

                val zipFile = File(applicationContext.cacheDir, "demo-data.zip")

                downloadFile(url, zipFile)
                unzipWithProgress(zipFile, parentDir)
                zipFile.delete()

                setProgress(workDataOf(KEY_PROGRESS to 100))
                Result.success()
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure()
            }
        }

    private suspend fun downloadFile(
        url: String,
        destFile: File,
    ) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP error ${response.code}")

            val body = response.body
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: -1L

            body.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    copyStreamWithProgress(input, output, totalBytes) { progress ->
                        setProgress(workDataOf(KEY_PROGRESS to progress))
                    }
                }
            }
        }
    }

    private suspend fun unzipWithProgress(
        zipFile: File,
        targetDir: File,
    ) {
        // Count entries first (for percentage calculation)
        val totalEntries = countZipEntries(zipFile)

        var processedEntries = 0
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDir, entry.name)

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        copyStream(zis, fos) // standard chunked copy
                    }
                }

                processedEntries++
                val unzipProgress =
                    if (totalEntries > 0) {
                        (processedEntries * 100 / totalEntries.toFloat())
                    } else {
                        100f
                    }
                setProgress(workDataOf(KEY_PROGRESS to unzipProgress))

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun countZipEntries(zipFile: File): Int {
        var count = 0
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            while (zis.nextEntry != null) {
                count++
            }
        }
        return count
    }

    private suspend fun copyStreamWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        onProgress: suspend (Float) -> Unit,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE) // 8KB
        var bytesRead: Int
        var bytesCopied: Long = 0

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            bytesCopied += bytesRead

            if (totalBytes > 0) {
                val progress = (bytesCopied * 100).toFloat() / totalBytes.toFloat()
                onProgress(progress)
            }
        }
    }

    private fun copyStream(
        input: InputStream,
        output: OutputStream,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }
    }

    companion object {
        const val KEY_URL = "KEY_URL"
        const val KEY_DESTINATION_PATH = "KEY_DESTINATION_PATH"
        const val KEY_PROGRESS = "KEY_PROGRESS"
        const val KEY_ERROR_MESSAGE = "KEY_ERROR_MESSAGE"
    }
}
