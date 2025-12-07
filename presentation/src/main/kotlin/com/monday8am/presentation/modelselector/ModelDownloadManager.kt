package com.monday8am.presentation.modelselector

import java.io.File
import kotlinx.coroutines.flow.Flow

interface ModelDownloadManager {
    sealed interface Status {
        data object Pending : Status

        data class InProgress(
            val progress: Float?,
        ) : Status // progress typically 0-100

        data class Completed(
            val file: File,
        ) : Status

        data class Failed(
            val message: String,
        ) : Status

        data object Cancelled : Status
    }

    fun getModelPath(bundleFilename: String): String

    suspend fun modelExists(bundleFilename: String): Boolean

    fun downloadModel(
        modelId: String,
        downloadUrl: String,
        bundleFilename: String,
    ): Flow<Status>

    fun cancelDownload()
}