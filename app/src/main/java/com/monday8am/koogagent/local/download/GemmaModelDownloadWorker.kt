package com.monday8am.koogagent.local.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal class GemmaModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val destinationPath = inputData.getString(KEY_DESTINATION_PATH) ?: return@withContext Result.failure()

        val destinationFile = File(destinationPath)
        destinationFile.parentFile?.mkdirs()

        val tempFile = File.createTempFile(destinationFile.name, TEMP_EXTENSION, destinationFile.parentFile)

        try {
            downloadFile(url, tempFile)
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }
            Result.success()
        } catch (throwable: Throwable) {
            tempFile.delete()
            if (throwable is CancellationException) {
                throw throwable
            }
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to throwable.message))
        }
    }

    private suspend fun downloadFile(sourceUrl: String, destinationFile: File) {
        val connection = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECTION_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            doInput = true
        }

        try {
            connection.connect()
            val contentLength = connection.contentLengthLong.takeIf { it > 0 }
            setProgress(workDataOf(KEY_PROGRESS to 0))

            connection.inputStream.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    copyStream(inputStream, outputStream, contentLength)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun copyStream(
        inputStream: InputStream,
        outputStream: FileOutputStream,
        contentLength: Long?,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesCopied = 0L
        while (true) {
            coroutineContext.ensureActive()
            val bytesRead = inputStream.read(buffer)
            if (bytesRead == -1) break
            outputStream.write(buffer, 0, bytesRead)
            bytesCopied += bytesRead
            if (contentLength != null && contentLength > 0) {
                val progress = ((bytesCopied * 100) / contentLength).toInt().coerceIn(0, 100)
                setProgress(workDataOf(KEY_PROGRESS to progress))
            }
        }
        outputStream.fd.sync()
    }

    companion object {
        const val KEY_URL = "key_url"
        const val KEY_DESTINATION_PATH = "key_destination_path"
        const val KEY_PROGRESS = "key_progress"
        const val KEY_ERROR_MESSAGE = "key_error_message"

        private const val CONNECTION_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val TEMP_EXTENSION = ".download"
    }
}
