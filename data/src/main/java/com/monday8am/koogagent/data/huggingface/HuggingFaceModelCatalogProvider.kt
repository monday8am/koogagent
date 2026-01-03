package com.monday8am.koogagent.data.huggingface

import co.touchlab.kermit.Logger
import com.monday8am.koogagent.data.AuthRepository
import com.monday8am.koogagent.data.HardwareBackend
import com.monday8am.koogagent.data.InferenceLibrary
import com.monday8am.koogagent.data.ModelCatalogProvider
import com.monday8am.koogagent.data.ModelConfiguration
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
    private val authRepository: AuthRepository? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ModelCatalogProvider {

    private val logger = Logger.withTag("HuggingFaceModelCatalogProvider")

    companion object {
        private const val BASE_URL = "https://huggingface.co/api/models"
        private const val AUTHOR = "litert-community"
        private const val LIST_URL = "$BASE_URL?author=$AUTHOR"
        private const val DOWNLOAD_URL_TEMPLATE = "https://huggingface.co/%s/resolve/main/%s"

        // Default values when metadata cannot be parsed
        private const val DEFAULT_CONTEXT_LENGTH = 4096
        private const val DEFAULT_PARAM_COUNT = 1.0f
    }

    private val requestSemaphore = kotlinx.coroutines.sync.Semaphore(5)

    override suspend fun fetchModels(): Result<List<ModelConfiguration>> =
        withContext(dispatcher) {
            runCatching {
                val summaries = fetchModelList().filter { it.pipelineTag == "text-generation" }

                // Fetch details and file sizes in parallel with concurrency limit
                val modelDataResults =
                    summaries
                        .map { summary ->
                            async {
                                requestSemaphore.acquire()
                                try {
                                    val details = fetchModelDetails(summary.id)
                                    val fileSizes = fetchFileSizes(summary.id)
                                    details?.let { Pair(it, fileSizes) }
                                } catch (e: Exception) {
                                    logger.e(e) { "Failed to fetch data for ${summary.id}" }
                                    null
                                } finally {
                                    requestSemaphore.release()
                                }
                            }
                        }
                        .awaitAll()

                modelDataResults
                    .filterNotNull()
                    .flatMap { (details, fileSizes) -> convertToConfigurations(details, fileSizes) }
                    .sortedByDescending { it.parameterCount }
                    .also { logger.d { "Successfully loaded ${it.size} model configurations" } }
            }
        }

    /**
     * Fetches the list of models from the organization.
     */
    private suspend fun fetchModelList(): List<HuggingFaceModelSummary> {
        val request = Request.Builder()
            .url(LIST_URL)
            .build()

        return executeRequest(request) { response ->
            val body = response.body.string()
            val jsonArray = JSONArray(body)
            (0 until jsonArray.length()).mapNotNull { i ->
                parseModelSummary(jsonArray.getJSONObject(i))
            }
        }
    }

    /**
     * Fetches detailed information for a specific model.
     */
    private suspend fun fetchModelDetails(modelId: String): HuggingFaceModelDetails? {
        val url = "$BASE_URL/$modelId"
        val request = Request.Builder()
            .url(url)
            .build()

        return try {
            executeRequest(request) { response ->
                parseModelDetails(JSONObject(response.body.string()))
            }
        } catch (e: Exception) {
            logger.w { "Failed to fetch details for $modelId: ${e.message}" }
            null
        }
    }

    /**
     * Fetches file tree to get file sizes (not available in model details endpoint).
     * Returns a map of filename -> size in bytes.
     */
    private suspend fun fetchFileSizes(modelId: String): Map<String, Long> {
        val url = "$BASE_URL/$modelId/tree/main"
        val request = Request.Builder()
            .url(url)
            .build()

        return try {
            executeRequest(request) { response ->
                val jsonArray = JSONArray(response.body.string())
                val sizeMap = mutableMapOf<String, Long>()

                for (i in 0 until jsonArray.length()) {
                    val fileObj = jsonArray.getJSONObject(i)
                    val path = fileObj.optString("path", null) ?: continue

                    // Try to get size from LFS metadata first (for large files)
                    val size = if (fileObj.has("lfs")) {
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

    /**
     * Helper to execute OkHttp requests as suspending functions with cancellation support.
     */
    private suspend fun <T> executeRequest(request: Request, parser: (Response) -> T): T {
        val finalRequest = authRepository?.authToken?.value?.let { token ->
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } ?: request

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(finalRequest)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            continuation.resumeWithException(IOException("Unexpected code $response"))
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
                    continuation.resumeWithException(e)
                }
            })
        }
    }

    /**
     * Parses a model summary from the list endpoint.
     */
    private fun parseModelSummary(json: JSONObject): HuggingFaceModelSummary? {
        return try {
            HuggingFaceModelSummary(
                id = json.getString("id"),
                pipelineTag = json.optString("pipeline_tag", null),
                downloads = json.optInt("downloads", 0),
                likes = json.optInt("likes", 0),
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses model details from the details endpoint.
     */
    private fun parseModelDetails(json: JSONObject): HuggingFaceModelDetails? {
        return try {
            val siblingsArray = json.optJSONArray("siblings") ?: JSONArray()
            val siblings = (0 until siblingsArray.length()).mapNotNull { i ->
                val sibling = siblingsArray.getJSONObject(i)
                val rfilename = sibling.optString("rfilename", null)
                // Note: size is not in siblings array, fetched separately from /tree/main
                if (rfilename != null) {
                    HuggingFaceFile(
                        rfilename = rfilename,
                        size = null,
                    )
                } else {
                    null
                }
            }

            val gatedValue = json.opt("gated")

            HuggingFaceModelDetails(
                id = json.getString("id"),
                pipelineTag = json.optString("pipeline_tag", null),
                gated = GatedStatus.fromApiValue(gatedValue),
                downloads = json.optInt("downloads", 0),
                likes = json.optInt("likes", 0),
                siblings = siblings,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Converts model details to a list of ModelConfiguration (one per valid file variant).
     * @param details Model metadata from API
     * @param fileSizes Map of filename -> size in bytes from /tree/main endpoint
     */
    private fun convertToConfigurations(
        details: HuggingFaceModelDetails,
        fileSizes: Map<String, Long>
    ): List<ModelConfiguration> {
        return details.siblings.mapNotNull { file ->
            val parsed = ModelFilenameParser.parse(file.rfilename, details.id)
                ?: return@mapNotNull null

            val downloadUrl = String.format(DOWNLOAD_URL_TEMPLATE, details.id, file.rfilename)
            val huggingFaceUrl = "https://huggingface.co/${details.id}"

            // Get file size from the tree endpoint
            val fileSize = fileSizes[file.rfilename]

            ModelConfiguration(
                displayName = parsed.displayName,
                modelFamily = parsed.modelFamily,
                parameterCount = parsed.parameterCount ?: DEFAULT_PARAM_COUNT,
                quantization = parsed.quantization,
                contextLength = parsed.contextLength ?: DEFAULT_CONTEXT_LENGTH,
                downloadUrl = downloadUrl,
                bundleFilename = file.rfilename,
                inferenceLibrary = parsed.inferenceLibrary,
                hardwareAcceleration = determineHardwareBackend(parsed.inferenceLibrary),
                isGated = details.gated.isGated,
                description = null, // TODO: Fetch from README.md or implement in-app markdown viewer
                fileSizeBytes = fileSize,
                huggingFaceUrl = huggingFaceUrl,
            )
        }
    }

    /**
     * Determines hardware acceleration support based on inference library.
     */
    private fun determineHardwareBackend(library: InferenceLibrary): HardwareBackend {
        return when (library) {
            InferenceLibrary.LITERT -> HardwareBackend.GPU_SUPPORTED
            InferenceLibrary.MEDIAPIPE -> HardwareBackend.GPU_SUPPORTED
        }
    }
}
