package com.monday8am.koogagent.download

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.monday8am.presentation.modelselector.ModelDownloadManager
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModelDownloadManagerImpl(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ModelDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val modelDestinationPath = "${context.applicationContext.filesDir}/data/local/tmp/slm/"

    private val downloadedFilenames = MutableStateFlow<Set<String>>(emptySet())

    override val modelsStatus: Flow<Map<String, ModelDownloadManager.Status>> = combine(
        downloadedFilenames,
        workManager.getWorkInfosByTagFlow(WORK_TAG)
    ) { downloaded, workInfos ->
        val statusMap = mutableMapOf<String, ModelDownloadManager.Status>()

        // 1. Mark everything on disk as Completed
        downloaded.forEach { filename ->
            statusMap[filename] = ModelDownloadManager.Status.Completed(File(getModelPath(filename)))
        }

        // 2. Overlay WorkManager status for anything NOT finished
        workInfos.forEach { info ->
            val filename = info.tags
                .firstOrNull { it.startsWith(BUNDLE_FILENAME_PREFIX) }
                ?.removePrefix(BUNDLE_FILENAME_PREFIX) ?: return@forEach

            val status = info.toModelDownloadManagerStatus(null)

            // Only overlay if it's actually active or just failed
            // If it's SUCCEEDED, we trust the disk scan more
            if (status !is ModelDownloadManager.Status.Completed) {
                statusMap[filename] = status
            }
        }
        statusMap
    }.flowOn(dispatcher)

    init {
        updateDownloadedFiles()
        // Also observe work manager to catch completions from other components or previous sessions
        scope.launch {
            workManager.getWorkInfosByTagFlow(WORK_TAG).collect {
                updateDownloadedFiles()
            }
        }
    }

    private fun updateDownloadedFiles() {
        val files = File(modelDestinationPath).listFiles()?.map { it.name }?.toSet() ?: emptySet()
        downloadedFilenames.value = files
    }

    override fun getModelPath(bundleFilename: String) = "$modelDestinationPath$bundleFilename"

    override suspend fun modelExists(bundleFilename: String): Boolean = withContext(dispatcher) {
        File(getModelPath(bundleFilename)).exists()
    }

    override suspend fun deleteModel(bundleFilename: String): Boolean = withContext(dispatcher) {
        val modelFile = File(getModelPath(bundleFilename))
        val deleted = if (modelFile.exists()) {
            modelFile.delete()
        } else {
            true // Already deleted
        }
        if (deleted) {
            updateDownloadedFiles()
        }
        deleted
    }

    override fun downloadModel(
        modelId: String,
        downloadUrl: String,
        bundleFilename: String,
    ): Flow<ModelDownloadManager.Status> = channelFlow {
        val destinationFile = File(getModelPath(bundleFilename))

        if (destinationFile.exists()) {
            send(ModelDownloadManager.Status.Completed(destinationFile))
            close()
            return@channelFlow
        }

        val workName = "model-download-$modelId"
        var workInfo = findRunningWork(workName)

        if (workInfo == null) {
            val workRequest = createDownloadWorkRequest(modelId, downloadUrl, destinationFile)
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
        modelId: String,
        downloadUrl: String,
        destinationFile: File,
    ): OneTimeWorkRequest {
        // Detect download type: ZIP files need extraction, others are downloaded directly
        val requiresUnzip = downloadUrl.endsWith(".zip", ignoreCase = true)

        return OneTimeWorkRequestBuilder<DownloadUnzipWorker>()
            .setInputData(
                workDataOf(
                    DownloadUnzipWorker.KEY_URL to downloadUrl,
                    DownloadUnzipWorker.KEY_DESTINATION_PATH to destinationFile.absolutePath,
                    DownloadUnzipWorker.KEY_REQUIRES_UNZIP to requiresUnzip,
                ),
            )
            .addTag(WORK_TAG)
            .addTag("$MODEL_ID_PREFIX$modelId")
            .addTag("$BUNDLE_FILENAME_PREFIX${destinationFile.name}")
            .build()
    }

    private suspend fun findRunningWork(workName: String): WorkInfo? = withContext(dispatcher) {
        workManager
            .getWorkInfosForUniqueWork(workName)
            .get()
            .firstOrNull { !it.state.isFinished }
    }

    private suspend fun ProducerScope<ModelDownloadManager.Status>.observeWork(workId: UUID, destinationFile: File) {
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

    companion object {
        private const val WORK_TAG = "model-download"
        private const val MODEL_ID_PREFIX = "model-id:"
        private const val BUNDLE_FILENAME_PREFIX = "bundle-filename:"
    }
}

private fun WorkInfo.toModelDownloadManagerStatus(file: File?): ModelDownloadManager.Status = when (state) {
    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
        ModelDownloadManager.Status.Pending
    }

    WorkInfo.State.RUNNING -> {
        val progress = progress.getFloat(DownloadUnzipWorker.KEY_PROGRESS, 0f)
        // Ensure progress is within a valid range.
        ModelDownloadManager.Status.InProgress(progress.coerceIn(0f, 100f))
    }

    WorkInfo.State.SUCCEEDED -> {
        ModelDownloadManager.Status.Completed(file ?: File(""))
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
