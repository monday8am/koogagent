package com.monday8am.presentation.notifications

import kotlinx.coroutines.flow.Flow
import java.io.File

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

    fun getModelPath(modelName: String): String

    suspend fun modelExists(modelName: String): Boolean

    fun downloadModel(
        url: String,
        modelName: String,
    ): Flow<Status>

    fun cancelDownload()
}
