package com.monday8am.koogagent.data.huggingface

import co.touchlab.kermit.Logger
import com.monday8am.koogagent.data.auth.AuthorRepository
import com.monday8am.koogagent.data.model.LocalModelDataSource
import com.monday8am.koogagent.data.model.ModelCatalogProvider
import com.monday8am.koogagent.data.model.ModelConfiguration
import kotlin.collections.flatten
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore

class HuggingFaceModelRepository(
    private val apiClient: HuggingFaceApiClient,
    private val authorRepository: AuthorRepository,
    private val localModelDataSource: LocalModelDataSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ModelCatalogProvider {

    private val logger = Logger.withTag("HuggingFaceModelRepository")

    companion object {
        private const val DOWNLOAD_URL_TEMPLATE = "https://huggingface.co/%s/resolve/main/%s"
        private const val DEFAULT_CONTEXT_LENGTH = 4096
        private const val DEFAULT_PARAM_COUNT = 1.0f
    }

    private val requestSemaphore = Semaphore(5)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getModels(): Flow<List<ModelConfiguration>> =
        authorRepository.authors
            .flatMapLatest { authors ->
                flow {
                    val cachedModels = localModelDataSource.getModels()
                    cachedModels?.takeIf { it.isNotEmpty() }?.let { emit(it) }

                    fetchNetworkModels(authors.map { it.name })
                        .onSuccess { networkModels ->
                            saveAndEmitIfChanged(networkModels, cachedModels)
                        }
                        .onFailure { error ->
                            logger.e(error) { "Failed to refresh models from API" }
                            if (cachedModels.isNullOrEmpty()) {
                                emit(emptyList())
                            }
                        }
                }
            }
            .flowOn(dispatcher)

    private suspend fun FlowCollector<List<ModelConfiguration>>.saveAndEmitIfChanged(
        networkModels: List<ModelConfiguration>,
        cachedModels: List<ModelConfiguration>?,
    ) {
        when {
            networkModels.isEmpty() -> {
                logger.w { "Network fetch returned empty configuration" }
                if (cachedModels.isNullOrEmpty()) emit(emptyList())
            }

            networkModels != cachedModels -> {
                logger.d { "Successfully loaded ${networkModels.size} model configurations" }
                localModelDataSource.saveModels(networkModels)
                emit(networkModels)
            }

            else -> {
                logger.d { "Network data identical to cached data, skipping duplicate emission" }
            }
        }
    }

    private suspend fun fetchNetworkModels(
        authorNames: List<String>
    ): Result<List<ModelConfiguration>> =
        try {
            Result.success(
                fetchModelListFromAllSources(authorNames)
                    .filter { it.pipelineTag == "text-generation" }
                    .let { summaries -> fetchModelDataInParallel(summaries) }
                    .flatMap { (summary, fileSizes) -> convertToConfigurations(summary, fileSizes) }
                    .sortedByDescending { it.parameterCount }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private suspend fun fetchModelDataInParallel(
        summaries: List<HuggingFaceModelSummary>
    ): List<Pair<HuggingFaceModelSummary, Map<String, Long>>> = coroutineScope {
        summaries.map { summary -> async { fetchModelData(summary) } }.awaitAll().filterNotNull()
    }

    private suspend fun fetchModelData(
        summary: HuggingFaceModelSummary
    ): Pair<HuggingFaceModelSummary, Map<String, Long>>? {
        requestSemaphore.acquire()
        return try {
            val fileSizes = apiClient.fetchFileSizes(summary.id)
            summary to fileSizes
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Failed to fetch data for ${summary.id}" }
            null
        } finally {
            requestSemaphore.release()
        }
    }

    private suspend fun fetchModelListFromAllSources(
        authorNames: List<String>
    ): List<HuggingFaceModelSummary> = coroutineScope {
        val allNames = authorNames.toMutableList()
        val currentUser = apiClient.fetchCurrentUsername()
        if (currentUser != null && allNames.none { it.equals(currentUser, ignoreCase = true) }) {
            logger.d { "Adding authenticated user '$currentUser' to model sources" }
            allNames.add(currentUser)
        }

        allNames
            .map { name ->
                async {
                    try {
                        apiClient.fetchModelList(name)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logger.e(e) { "Failed to fetch models from $name" }
                        emptyList()
                    }
                }
            }
            .awaitAll()
            .flatten()
            .distinctBy { it.id }
    }

    private fun convertToConfigurations(
        summary: HuggingFaceModelSummary,
        fileSizes: Map<String, Long>,
    ): List<ModelConfiguration> {
        return fileSizes.keys.mapNotNull { filename ->
            val parsed = ModelFilenameParser.parse(filename, summary.id) ?: return@mapNotNull null

            val downloadUrl = String.format(DOWNLOAD_URL_TEMPLATE, summary.id, filename)
            val huggingFaceUrl = "https://huggingface.co/${summary.id}"
            val fileSize = fileSizes[filename]

            ModelConfiguration(
                displayName = parsed.displayName,
                modelFamily = parsed.modelFamily,
                parameterCount = parsed.parameterCount ?: DEFAULT_PARAM_COUNT,
                quantization = parsed.quantization,
                contextLength = parsed.contextLength ?: DEFAULT_CONTEXT_LENGTH,
                downloadUrl = downloadUrl,
                bundleFilename = filename,
                author = summary.id.substringBefore("/"),
                isGated = summary.gated.isGated,
                description = null,
                fileSizeBytes = fileSize,
                huggingFaceUrl = huggingFaceUrl,
            )
        }
    }
}
