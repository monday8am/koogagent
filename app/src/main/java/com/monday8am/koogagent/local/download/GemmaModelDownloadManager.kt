package com.monday8am.koogagent.local.download

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.getWorkInfoByIdFlow
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

class GemmaModelDownloadManager(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    fun downloadModel(url: String, destinationFile: File): Flow<DownloadStatus> = channelFlow {
        val result = withContext(dispatcher) {
            if (destinationFile.exists()) {
                destinationFile
            } else {
                destinationFile.parentFile?.mkdirs()
                null
            }
        }

        result?.let {
            send(DownloadStatus.Completed(it))
            return@channelFlow
        }

        val workRequest = OneTimeWorkRequestBuilder<GemmaModelDownloadWorker>()
            .setInputData(
                workDataOf(
                    GemmaModelDownloadWorker.KEY_URL to url,
                    GemmaModelDownloadWorker.KEY_DESTINATION_PATH to destinationFile.absolutePath,
                ),
            )
            .addTag(WORK_TAG)
            .build()

        send(DownloadStatus.Pending)
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest)

        val job = launch {
            workManager.getWorkInfoByIdFlow(workRequest.id).collectLatest { info ->
                send(info.toDownloadStatus(destinationFile))
            }
        }

        awaitClose {
            job.cancel()
            workManager.cancelWorkById(workRequest.id)
        }
    }

    private fun WorkInfo.toDownloadStatus(file: File): DownloadStatus = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadStatus.Pending
        WorkInfo.State.RUNNING -> {
            val progress = progress.getInt(GemmaModelDownloadWorker.KEY_PROGRESS, -1)
            DownloadStatus.InProgress(progress.takeIf { it >= 0 })
        }
        WorkInfo.State.SUCCEEDED -> DownloadStatus.Completed(file)
        WorkInfo.State.FAILED -> DownloadStatus.Failed(
            outputData.getString(GemmaModelDownloadWorker.KEY_ERROR_MESSAGE)
                ?: "Unknown error",
        )
        WorkInfo.State.CANCELLED -> DownloadStatus.Cancelled
    }

    sealed class DownloadStatus {
        data object Pending : DownloadStatus()
        data class InProgress(val progress: Int?) : DownloadStatus()
        data class Completed(val file: File) : DownloadStatus()
        data class Failed(val message: String) : DownloadStatus()
        data object Cancelled : DownloadStatus()
    }

    companion object {
        private const val WORK_NAME = "gemma-model-download"
        private const val WORK_TAG = "gemma-model-download-tag"
    }
}
