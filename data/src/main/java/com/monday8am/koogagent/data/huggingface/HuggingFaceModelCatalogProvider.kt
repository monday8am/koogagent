package com.monday8am.koogagent.data.huggingface

import co.touchlab.kermit.Logger
import com.monday8am.koogagent.data.AuthRepository
import com.monday8am.koogagent.data.LocalModelDataSource
import com.monday8am.koogagent.data.ModelCatalogProvider
import com.monday8am.koogagent.data.ModelConfiguration
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fetches models from Hugging Face API and converts them to ModelConfiguration.
 *
 * Endpoints used:
 * - List: GET https://huggingface.co/api/models?author=litert-community
 * - Details: GET https://huggingface.co/api/models/{model_id}
 */
class HuggingFaceModelCatalogProvider(
    private val localModelDataSource: LocalModelDataSource,
    private val authRepository: AuthRepository? = null,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ModelCatalogProvider {

    private val logger = Logger.withTag("HuggingFaceModelCatalogProvider")

    companion object {
        private const val BASE_URL = "https://huggingface.co/api/models"
        private const val WHOAMI_URL = "https://huggingface.co/api/whoami-v2"
        private const val DEFAULT_AUTHOR = "litert-community"
        private const val DOWNLOAD_URL_TEMPLATE = "https://huggingface.co/%s/resolve/main/%s"

        // Default values when metadata cannot be parsed
        private const val DEFAULT_CONTEXT_LENGTH = 4096
        private const val DEFAULT_PARAM_COUNT = 1.0f
    }

    private val requestSemaphore = Semaphore(5)

    override fun getModels(): Flow<List<ModelConfiguration>> =
        flow {
                // 1. Emit cached models immediately if available
                val cachedModels = localModelDataSource.getModels()
                cachedModels?.takeIf { it.isNotEmpty() }?.let { emit(it) }

                // 2. Fetch and emit fresh models from network
                fetchNetworkModels()
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

    private suspend fun fetchNetworkModels(): Result<List<ModelConfiguration>> =
        try {
            Result.success(
                fetchModelListFromAllSources()
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
            val fileSizes = fetchFileSizes(summary.id)
            summary to fileSizes
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Failed to fetch data for ${summary.id}" }
            null
        } finally {
            requestSemaphore.release()
        }
    }

    private suspend fun fetchModelListFromAllSources(): List<HuggingFaceModelSummary> {
        val modelSummaries = mutableListOf<HuggingFaceModelSummary>()

        // Always fetch from default author (litert-community)
        try {
            modelSummaries.addAll(fetchModelList(DEFAULT_AUTHOR))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.e(e) { "Failed to fetch models from $DEFAULT_AUTHOR" }
        }

        // If authenticated, fetch from user's repository
        val username = fetchCurrentUsername()
        if (username != null && username != DEFAULT_AUTHOR) {
            try {
                logger.d { "Fetching models from authenticated user: $username" }
                modelSummaries.addAll(fetchModelList(username))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logger.e(e) { "Failed to fetch models from user: $username" }
            }
        }

        // Deduplicate by model ID
        return modelSummaries.distinctBy { it.id }
    }

    private suspend fun fetchModelList(author: String): List<HuggingFaceModelSummary> {
        val listUrl = "$BASE_URL?author=$author"
        val request = Request.Builder().url(listUrl).build()

        return executeRequest(request) { response ->
            val body = response.body.string()
            val jsonArray = JSONArray(body)
            (0 until jsonArray.length()).mapNotNull { i ->
                parseModelSummary(jsonArray.getJSONObject(i))
            }
        }
    }

    private suspend fun fetchCurrentUsername(): String? {
        // Only attempt if we have an auth token
        val token = authRepository?.authToken?.value
        if (token.isNullOrBlank()) {
            logger.d { "No auth token available, skipping user models fetch" }
            return null
        }

        return try {
            val request = Request.Builder().url(WHOAMI_URL).build()
            executeRequest(request) { response ->
                val body = response.body.string()
                val json = JSONObject(body)
                json.optString("name", null)?.also {
                    logger.d { "Fetched username from whoami: $it" }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logger.w(e) { "Failed to fetch current username from whoami endpoint" }
            null
        }
    }

    private suspend fun fetchFileSizes(modelId: String): Map<String, Long> {
        val url = "$BASE_URL/$modelId/tree/main"
        val request = Request.Builder().url(url).build()

        return try {
            executeRequest(request) { response ->
                val jsonArray = JSONArray(response.body.string())
                val sizeMap = mutableMapOf<String, Long>()

                for (i in 0 until jsonArray.length()) {
                    val fileObj = jsonArray.getJSONObject(i)
                    val path = fileObj.optString("path", null) ?: continue

                    // Try to get size from LFS metadata first (for large files)
                    val size =
                        if (fileObj.has("lfs")) {
                            fileObj.getJSONObject("lfs").optLong("size", -1L)
                        } else {
                            fileObj.optLong("size", -1L)
                        }

                    if (size > 0) {
                        sizeMap[path] = size
                    }
                }

                logger.d { "Found ${sizeMap.size} files with size info" }
                sizeMap
            }
        } catch (e: Exception) {
            logger.w { "Failed to fetch file sizes for $modelId: ${e.message}" }
            emptyMap()
        }
    }

    private suspend fun <T> executeRequest(request: Request, parser: (Response) -> T): T {
        val finalRequest =
            authRepository?.authToken?.value?.let { token ->
                request.newBuilder().header("Authorization", "Bearer $token").build()
            } ?: request

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(finalRequest)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        if (!continuation.isActive) return
                        response.use {
                            if (!response.isSuccessful) {
                                continuation.resumeWithException(
                                    IOException("Unexpected code $response")
                                )
                                return
                            }
                            try {
                                continuation.resume(parser(response))
                            } catch (e: Exception) {
                                continuation.resumeWithException(e)
                            }
                        }
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            )
        }
    }

    private fun parseModelSummary(json: JSONObject): HuggingFaceModelSummary? {
        return try {
            val gatedValue = json.opt("gated")
            HuggingFaceModelSummary(
                id = json.getString("id"),
                pipelineTag = json.optString("pipeline_tag", null),
                gated = GatedStatus.fromApiValue(gatedValue),
                downloads = json.optInt("downloads", 0),
                likes = json.optInt("likes", 0),
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Converts model details to a list of ModelConfiguration (one per valid file variant).
     *
     * @param summary Model summary from list API
     * @param fileSizes Map of filename -> size in bytes from /tree/main endpoint
     */
    private fun convertToConfigurations(
        summary: HuggingFaceModelSummary,
        fileSizes: Map<String, Long>,
    ): List<ModelConfiguration> {
        // Iterate over found files in the tree instead of siblings from details
        return fileSizes.keys.mapNotNull { filename ->
            val parsed = ModelFilenameParser.parse(filename, summary.id) ?: return@mapNotNull null

            val downloadUrl = String.format(DOWNLOAD_URL_TEMPLATE, summary.id, filename)
            val huggingFaceUrl = "https://huggingface.co/${summary.id}"

            // Get file size from the tree endpoint
            val fileSize = fileSizes[filename]

            ModelConfiguration(
                displayName = parsed.displayName,
                modelFamily = parsed.modelFamily,
                parameterCount = parsed.parameterCount ?: DEFAULT_PARAM_COUNT,
                quantization = parsed.quantization,
                contextLength = parsed.contextLength ?: DEFAULT_CONTEXT_LENGTH,
                downloadUrl = downloadUrl,
                bundleFilename = filename,
                isGated = summary.gated.isGated,
                description =
                    null, // TODO: Fetch from README.md or implement in-app markdown viewer
                fileSizeBytes = fileSize,
                huggingFaceUrl = huggingFaceUrl,
            )
        }
    }
}
