package com.monday8am.koogagent.data.huggingface

import co.touchlab.kermit.Logger
import com.monday8am.koogagent.data.AuthRepository
import com.monday8am.koogagent.data.AuthorInfo
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

class HuggingFaceApiClient(
    private val authRepository: AuthRepository? = null,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val logger = Logger.withTag("HuggingFaceApiClient")

    companion object {
        private const val BASE_URL = "https://huggingface.co/api/models"
        private const val WHOAMI_URL = "https://huggingface.co/api/whoami-v2"
        private const val HF_BASE_URL = "https://huggingface.co/api"
    }

    suspend fun fetchModelList(author: String): List<HuggingFaceModelSummary> {
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

    suspend fun fetchFileSizes(modelId: String): Map<String, Long> {
        val url = "$BASE_URL/$modelId/tree/main"
        val request = Request.Builder().url(url).build()

        return try {
            executeRequest(request) { response ->
                val jsonArray = JSONArray(response.body.string())
                val sizeMap = mutableMapOf<String, Long>()

                for (i in 0 until jsonArray.length()) {
                    val fileObj = jsonArray.getJSONObject(i)
                    val path = fileObj.optString("path", null) ?: continue

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

    suspend fun fetchCurrentUsername(): String? {
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

    suspend fun fetchAuthorInfo(name: String): AuthorInfo? {
        // Try user endpoint first, then org endpoint
        val userUrl = "$HF_BASE_URL/users/$name/overview"
        val orgUrl = "$HF_BASE_URL/organizations/$name/overview"

        return try {
            val request = Request.Builder().url(userUrl).build()
            parseAuthorInfo(name, request)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            try {
                val request = Request.Builder().url(orgUrl).build()
                parseAuthorInfo(name, request)
            } catch (e2: Exception) {
                if (e2 is CancellationException) throw e2
                logger.d { "Author '$name' not found on HuggingFace" }
                null
            }
        }
    }

    private suspend fun parseAuthorInfo(name: String, request: Request): AuthorInfo {
        return executeRequest(request) { response ->
            val body = response.body.string()
            val json = JSONObject(body)
            val avatarUrl = json.optString("avatarUrl", null) ?: json.optString("avatar", null)
            val numModels = if (json.has("numModels")) json.optInt("numModels") else null
            AuthorInfo(name = name, avatarUrl = avatarUrl, modelCount = numModels)
        }
    }

    suspend fun <T> executeRequest(request: Request, parser: (Response) -> T): T {
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
}
