package com.monday8am.koogagent.data.huggingface

import com.monday8am.koogagent.data.HardwareBackend
import com.monday8am.koogagent.data.InferenceLibrary
import com.monday8am.koogagent.data.ModelCatalogProvider
import com.monday8am.koogagent.data.ModelConfiguration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ModelCatalogProvider {

    companion object {
        private const val BASE_URL = "https://huggingface.co/api/models"
        private const val AUTHOR = "litert-community"
        private const val LIST_URL = "$BASE_URL?author=$AUTHOR"
        private const val DOWNLOAD_URL_TEMPLATE = "https://huggingface.co/%s/resolve/main/%s"

        // Default values when metadata cannot be parsed
        private const val DEFAULT_CONTEXT_LENGTH = 4096
        private const val DEFAULT_PARAM_COUNT = 1.0f
    }

    override suspend fun fetchModels(): Result<List<ModelConfiguration>> = withContext(dispatcher) {
        runCatching {
            val summaries = fetchModelList()
                .filter { it.pipelineTag == "text-generation" }

            summaries.flatMap { summary ->
                try {
                    val details = fetchModelDetails(summary.id)
                    if (details != null) {
                        convertToConfigurations(details)
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    println("HuggingFaceModelCatalogProvider: Failed to fetch details for ${summary.id}: ${e.message}")
                    emptyList()
                }
            }.sortedByDescending { it.parameterCount }
        }
    }

    /**
     * Fetches the list of models from the organization.
     */
    private fun fetchModelList(): List<HuggingFaceModelSummary> {
        val request = Request.Builder()
            .url(LIST_URL)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to fetch model list: ${response.code}")
            }

            val jsonArray = JSONArray(response.body.string())
            return (0 until jsonArray.length()).mapNotNull { i ->
                parseModelSummary(jsonArray.getJSONObject(i))
            }
        }
    }

    /**
     * Fetches detailed information for a specific model.
     */
    private fun fetchModelDetails(modelId: String): HuggingFaceModelDetails? {
        val url = "$BASE_URL/$modelId"
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return null
            }

            return parseModelDetails(JSONObject(response.body.string()))
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
        } catch (e: Exception) {
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
                if (rfilename != null) HuggingFaceFile(rfilename) else null
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
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Converts model details to a list of ModelConfiguration (one per valid file variant).
     */
    private fun convertToConfigurations(details: HuggingFaceModelDetails): List<ModelConfiguration> {
        return details.siblings.mapNotNull { file ->
            val parsed = ModelFilenameParser.parse(file.rfilename, details.id)
                ?: return@mapNotNull null

            val downloadUrl = String.format(DOWNLOAD_URL_TEMPLATE, details.id, file.rfilename)

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
