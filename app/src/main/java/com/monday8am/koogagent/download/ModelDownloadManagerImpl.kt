package com.monday8am.koogagent.download

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.monday8am.presentation.modelselector.ModelDownloadManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ModelDownloadManagerImpl(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ModelDownloadManager {
    private val modelDestinationPath = "${context.applicationContext.filesDir}/data/local/tmp/slm/"

    override fun getModelPath(bundleFilename: String) = "$modelDestinationPath$bundleFilename"

    override suspend fun modelExists(bundleFilename: String): Boolean =
        withContext(dispatcher) {
            File(getModelPath(bundleFilename)).exists()
        }

    override fun downloadModel(
        modelId: String,
        downloadUrl: String,
        bundleFilename: String,
    ): Flow<ModelDownloadManager.Status> =
        channelFlow {
            val destinationFile = File(getModelPath(bundleFilename))

            if (destinationFile.exists()) {
                send(ModelDownloadManager.Status.Completed(destinationFile))
                close()
                return@channelFlow
            }

            val workName = "model-download-$modelId"
            var workInfo = findRunningWork(workName)

            if (workInfo == null) {
                val workRequest = createDownloadWorkRequest(downloadUrl, destinationFile)
                workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, workRequest)
                workInfo = findRunningWork(workName)
            }

            if (workInfo != null) {
                observeWork(workInfo.id, destinationFile)
            } else {
                // This is a safeguard for the unlikely case that work fails to be found even after enqueuing.
                val errorStatus = ModelDownloadManager.Status.Failed("Could not find or start the download job.")
                send(errorStatus)
                close()
            }
        }

    private fun createDownloadWorkRequest(
        downloadUrl: String,
        destinationFile: File,
    ): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<DownloadUnzipWorker>()
            .setInputData(
                workDataOf(
                    DownloadUnzipWorker.KEY_URL to downloadUrl,
                    DownloadUnzipWorker.KEY_DESTINATION_PATH to destinationFile.absolutePath,
                ),
            ).addTag(WORK_TAG)
            .build()

    private suspend fun findRunningWork(workName: String): WorkInfo? =
        withContext(dispatcher) {
            workManager
                .getWorkInfosForUniqueWork(workName)
                .get()
                .firstOrNull { !it.state.isFinished }
        }

    private suspend fun ProducerScope<ModelDownloadManager.Status>.observeWork(
        workId: UUID,
        destinationFile: File,
    ) {
        val observerJob =
            launch {
                workManager
                    .getWorkInfoByIdFlow(workId)
                    .mapNotNull { it } // Filter out any null initial values
                    .collectLatest { info ->
                        send(info.toModelDownloadManagerStatus(destinationFile))
                        if (info.state.isFinished) {
                            close() // Close the flow when work is complete
                        }
                    }
            }

        // awaitClose is the crucial part for cleanup.
        awaitClose {
            // This cancels the observerJob, stopping the collection of WorkInfo updates.
            // It DOES NOT cancel the background download itself, which is the desired behavior.
            observerJob.cancel()
        }
    }

    override fun cancelDownload() {
        // This function correctly cancels all work tagged as "model-download".
        workManager.cancelAllWorkByTag(WORK_TAG)
    }

    private fun WorkInfo.toModelDownloadManagerStatus(file: File): ModelDownloadManager.Status =
        when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                ModelDownloadManager.Status.Pending
            }

            WorkInfo.State.RUNNING -> {
                val progress = progress.getFloat(DownloadUnzipWorker.KEY_PROGRESS, 0f)
                // Ensure progress is within a valid range.
                ModelDownloadManager.Status.InProgress(progress.coerceIn(0f, 100f))
            }

            WorkInfo.State.SUCCEEDED -> {
                ModelDownloadManager.Status.Completed(file)
            }

            WorkInfo.State.FAILED -> {
                val errorMessage =
                    outputData.getString(DownloadUnzipWorker.KEY_ERROR_MESSAGE)
                        ?: "Download failed due to an unknown error."
                ModelDownloadManager.Status.Failed(errorMessage)
            }

            WorkInfo.State.CANCELLED -> {
                ModelDownloadManager.Status.Cancelled
            }
        }

    companion object {
        private const val WORK_TAG = "model-download"
    }
}
