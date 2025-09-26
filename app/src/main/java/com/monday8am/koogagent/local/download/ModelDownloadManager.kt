package com.monday8am.koogagent.local.download

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.flow.mapNotNull

class ModelDownloadManager(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val modelDestinationPath = "${context.applicationContext.filesDir}/data/local/tmp/slm/"

    fun getModelPath(modelName: String) = "$modelDestinationPath$modelName"

    fun modelExists(path: String) = File(path).exists()

    fun downloadModel(url: String, modelName: String): Flow<DownloadStatus> = channelFlow {
        val destinationPath = "$modelDestinationPath$modelName"
        val destinationFile = File(destinationPath)

        if (destinationFile.exists()) {
            send(DownloadStatus.Completed(destinationFile))
            close() // Close flow on early completion
            return@channelFlow
        }

        withContext(dispatcher) {
            destinationFile.parentFile?.mkdirs() ?: false // mkdirs returns true if successful or already exists
        }
        val workRequest = OneTimeWorkRequestBuilder<DownloadUnzipWorker>()
            .setInputData(
                workDataOf(
                    DownloadUnzipWorker.KEY_URL to url,
                    DownloadUnzipWorker.KEY_DESTINATION_PATH to destinationFile.absolutePath,
                ),
            )
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest)
        send(DownloadStatus.Pending) // Send pending after enqueueing

        val job = launch {
            workManager.getWorkInfoByIdFlow(workRequest.id)
                .mapNotNull { it } // Filter out null WorkInfo initially
                .collectLatest { info ->
                    send(info.toDownloadStatus(destinationFile))
                    if (info.state.isFinished) {
                        close()
                    }
                }
        }

        awaitClose {
            job.cancel()
            // Only cancel if this flow instance actually started the work,
            // or if you always want to cancel when the collector is gone.
            // If using ExistingWorkPolicy.KEEP and observing existing work,
            // you might not want to cancel it here.
            workManager.cancelWorkById(workRequest.id)
        }
    }

    private fun WorkInfo.toDownloadStatus(file: File): DownloadStatus = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadStatus.Pending
        WorkInfo.State.RUNNING -> {
            val progress = progress.getInt(DownloadUnzipWorker.KEY_PROGRESS, -1)
            DownloadStatus.InProgress(progress.takeIf { it in 0..100 })
        }
        WorkInfo.State.SUCCEEDED -> DownloadStatus.Completed(file)
        WorkInfo.State.FAILED -> DownloadStatus.Failed(
            outputData.getString(DownloadUnzipWorker.KEY_ERROR_MESSAGE)
                ?: "Download failed due to an unknown error.", // Slightly more descriptive
        )
        WorkInfo.State.CANCELLED -> DownloadStatus.Cancelled
    }

    sealed interface DownloadStatus {
        data object Pending : DownloadStatus
        data class InProgress(val progress: Int?) : DownloadStatus // progress typically 0-100
        data class Completed(val file: File) : DownloadStatus
        data class Failed(val message: String) : DownloadStatus
        data object Cancelled : DownloadStatus
    }

    companion object {
        private const val WORK_NAME = "gemma-model-download"
        private const val WORK_TAG = "gemma-model-download-tag"
    }
}
