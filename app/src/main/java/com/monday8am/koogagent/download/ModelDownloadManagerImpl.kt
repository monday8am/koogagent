package com.monday8am.koogagent.download

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.monday8am.koogagent.data.ModelConfiguration
import com.monday8am.presentation.notifications.ModelDownloadManager
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModelDownloadManagerImpl(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ModelDownloadManager {
    val modelDestinationPath = "${context.applicationContext.filesDir}/data/local/tmp/slm/"

    override fun getModelPath(model: ModelConfiguration) = "$modelDestinationPath${model.bundleFilename}"

    override suspend fun modelExists(model: ModelConfiguration) =
        withContext(dispatcher) {
            File(getModelPath(model)).exists()
        }

    override fun downloadModel(model: ModelConfiguration): Flow<ModelDownloadManager.Status> =
        channelFlow {
            val destinationPath = getModelPath(model)
            val destinationFile = File(destinationPath)

            if (destinationFile.exists()) {
                send(ModelDownloadManager.Status.Completed(destinationFile))
                close() // Close flow on early completion
                return@channelFlow
            }

            val workName = "model-download-${model.id}"
            val existingWork = workManager.getWorkInfosForUniqueWork(workName).get()
            if (existingWork.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }) {
                // Observe the existing work instead of creating new work
                val existingWorkId = existingWork.first { it.state.isFinished.not() }.id
                val job =
                    launch {
                        workManager
                            .getWorkInfoByIdFlow(existingWorkId)
                            .mapNotNull { it }
                            .collectLatest { info ->
                                send(info.toModelDownloadManagerStatus(destinationFile))
                                if (info.state.isFinished) {
                                    close()
                                }
                            }
                    }
                awaitClose { job.cancel() }
                return@channelFlow
            }

            withContext(dispatcher) {
                destinationFile.parentFile?.mkdirs() ?: false // mkdirs returns true if successful or already exists
            }
            val workRequest =
                OneTimeWorkRequestBuilder<DownloadUnzipWorker>()
                    .setInputData(
                        workDataOf(
                            DownloadUnzipWorker.KEY_URL to model.downloadUrl,
                            DownloadUnzipWorker.KEY_DESTINATION_PATH to destinationFile.absolutePath,
                        ),
                    ).addTag(WORK_TAG)
                    .build()

            workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, workRequest)
            send(ModelDownloadManager.Status.Pending) // Send pending after enqueueing

            val job =
                launch {
                    workManager
                        .getWorkInfoByIdFlow(workRequest.id)
                        .mapNotNull { it } // Filter out null WorkInfo initially
                        .collectLatest { info ->
                            send(info.toModelDownloadManagerStatus(destinationFile))
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

    override fun cancelDownload() {
        // Cancel all model downloads
        workManager.cancelAllWorkByTag(WORK_TAG)
    }

    private fun WorkInfo.toModelDownloadManagerStatus(file: File): ModelDownloadManager.Status =
        when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                ModelDownloadManager.Status.Pending
            }

            WorkInfo.State.RUNNING -> {
                val progress = progress.getFloat(DownloadUnzipWorker.KEY_PROGRESS, 0f)
                ModelDownloadManager.Status.InProgress(progress.takeIf { it in 0f..100f })
            }

            WorkInfo.State.SUCCEEDED -> {
                ModelDownloadManager.Status.Completed(file)
            }

            WorkInfo.State.FAILED -> {
                ModelDownloadManager.Status.Failed(
                    outputData.getString(DownloadUnzipWorker.KEY_ERROR_MESSAGE)
                        ?: "Download failed due to an unknown error.", // Slightly more descriptive
                )
            }

            WorkInfo.State.CANCELLED -> {
                ModelDownloadManager.Status.Cancelled
            }
        }

    companion object {
        private const val WORK_TAG = "model-download"
    }
}
