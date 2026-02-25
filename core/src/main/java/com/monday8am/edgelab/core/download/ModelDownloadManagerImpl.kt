package com.monday8am.edgelab.core.download

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.monday8am.edgelab.data.auth.AuthRepository
import com.monday8am.edgelab.presentation.modelselector.ModelDownloadManager
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModelDownloadManagerImpl(
    context: Context,
    private val authRepository: AuthRepository,
    private val workManager: WorkManager = WorkManager.getInstance(context),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ModelDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val modelDestinationPath = "${context.applicationContext.filesDir}/data/local/tmp/slm/"

    // Initialize with current disk state to avoid race condition
    private val downloadedFilenames = MutableStateFlow(scanDiskFiles())

    override val modelsStatus: Flow<Map<String, ModelDownloadManager.Status>> =
        combine(downloadedFilenames, workManager.getWorkInfosByTagFlow(WORK_TAG)) {
                downloaded,
                workInfos ->
                buildStatusMap(downloaded, workInfos)
            }
            .flowOn(dispatcher)

    init {
        // Observe WorkManager to update disk state when downloads complete
        scope.launch {
            workManager.getWorkInfosByTagFlow(WORK_TAG).collect { workInfos ->
                val hasNewCompletion = workInfos.any { it.state == WorkInfo.State.SUCCEEDED }
                if (hasNewCompletion) {
                    refreshDiskState()
                }
            }
        }
    }

    private fun buildStatusMap(
        downloadedFiles: Set<String>,
        workInfos: List<WorkInfo>,
    ): Map<String, ModelDownloadManager.Status> {
        val statusMap = mutableMapOf<String, ModelDownloadManager.Status>()

        // 1. Mark files on disk as Completed
        downloadedFiles.forEach { filename ->
            statusMap[filename] =
                ModelDownloadManager.Status.Completed(File(getModelPath(filename)))
        }

        // 2. Active work takes precedence over disk state
        workInfos.forEach { info ->
            val filename = info.extractBundleFilename() ?: return@forEach

            // Only overlay if work is still active (not finished)
            // Finished work defers to disk scan for source of truth
            if (!info.state.isFinished) {
                statusMap[filename] = info.toStatus()
            }
        }

        return statusMap
    }

    private fun scanDiskFiles(): Set<String> {
        return File(modelDestinationPath).listFiles()?.map { it.name }?.toSet() ?: emptySet()
    }

    private fun refreshDiskState() {
        downloadedFilenames.value = scanDiskFiles()
    }

    override fun getModelPath(bundleFilename: String) = "$modelDestinationPath$bundleFilename"

    override suspend fun deleteModel(bundleFilename: String): Boolean =
        withContext(dispatcher) {
            val modelFile = File(getModelPath(bundleFilename))
            val deleted =
                if (modelFile.exists()) {
                    modelFile.delete()
                } else {
                    true // Already deleted
                }
            if (deleted) {
                refreshDiskState()
            }
            deleted
        }

    override suspend fun downloadModel(
        modelId: String,
        downloadUrl: String,
        bundleFilename: String,
    ) {
        withContext(dispatcher) {
            val destinationFile = File(getModelPath(bundleFilename))

            if (destinationFile.exists()) {
                return@withContext
            }

            val workName = "model-download-$modelId"
            val existingWork = findRunningWork(workName)

            if (existingWork == null) {
                val token = authRepository.authToken.value
                val workRequest = createDownloadWorkRequest(modelId, downloadUrl, destinationFile, token)
                workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, workRequest)
            }
            // Status updates come through modelsStatus flow
        }
    }

    private fun createDownloadWorkRequest(
        modelId: String,
        downloadUrl: String,
        destinationFile: File,
        token: String?,
    ): OneTimeWorkRequest {
        val requiresUnzip = downloadUrl.endsWith(".zip", ignoreCase = true)

        return OneTimeWorkRequestBuilder<DownloadUnzipWorker>()
            .setInputData(
                workDataOf(
                    DownloadUnzipWorker.KEY_URL to downloadUrl,
                    DownloadUnzipWorker.KEY_DESTINATION_PATH to destinationFile.absolutePath,
                    DownloadUnzipWorker.KEY_REQUIRES_UNZIP to requiresUnzip,
                    DownloadUnzipWorker.KEY_AUTH_TOKEN to token,
                )
            )
            .addTag(WORK_TAG)
            .addTag("$MODEL_ID_PREFIX$modelId")
            .addTag("$BUNDLE_FILENAME_PREFIX${destinationFile.name}")
            .build()
    }

    private suspend fun findRunningWork(workName: String): WorkInfo? =
        withContext(dispatcher) {
            workManager.getWorkInfosForUniqueWork(workName).get().firstOrNull {
                !it.state.isFinished
            }
        }

    override fun cancelDownload() {
        workManager.cancelAllWorkByTag(WORK_TAG)
    }

    override fun dispose() {
        scope.cancel()
    }

    companion object {
        private const val WORK_TAG = "model-download"
        private const val MODEL_ID_PREFIX = "model-id:"
        private const val BUNDLE_FILENAME_PREFIX = "bundle-filename:"
    }
}

private fun WorkInfo.extractBundleFilename(): String? {
    return tags.firstOrNull { it.startsWith("bundle-filename:") }?.removePrefix("bundle-filename:")
}

private fun WorkInfo.toStatus(): ModelDownloadManager.Status =
    when (state) {
        WorkInfo.State.ENQUEUED,
        WorkInfo.State.BLOCKED -> {
            ModelDownloadManager.Status.Pending
        }

        WorkInfo.State.RUNNING -> {
            val progress = progress.getFloat(DownloadUnzipWorker.KEY_PROGRESS, 0f)
            ModelDownloadManager.Status.InProgress(progress.coerceIn(0f, 100f))
        }

        WorkInfo.State.SUCCEEDED -> {
            // This case is typically not reached since we defer to disk scan
            ModelDownloadManager.Status.Completed(File(""))
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
