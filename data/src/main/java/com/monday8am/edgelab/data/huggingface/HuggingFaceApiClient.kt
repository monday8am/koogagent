package com.monday8am.edgelab.data.huggingface

import co.touchlab.kermit.Logger
import com.monday8am.edgelab.data.auth.AuthRepository
import com.monday8am.edgelab.data.auth.AuthorInfo
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class HfModelDto(
    val id: String,
    val pipeline_tag: String? = null,
    val gated: JsonElement? = null,
    val downloads: Int = 0,
    val likes: Int = 0,
)

@Serializable
private data class HfFileDto(
    val path: String? = null,
    val size: Long = -1L,
    val lfs: HfLfsDto? = null,
)

@Serializable private data class HfLfsDto(val size: Long = -1L)

@Serializable private data class HfUserDto(val name: String? = null)

@Serializable
private data class HfAuthorDto(
    val avatarUrl: String? = null,
    val avatar: String? = null,
    val numModels: Int? = null,
)

class HuggingFaceApiClient(
    private val authRepository: AuthRepository? = null,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build(),
) {
    private val logger = Logger.withTag("HuggingFaceApiClient")

    companion object {
        private const val BASE_URL = "https://huggingface.co/api/models"
        private const val WHOAMI_URL = "https://huggingface.co/api/whoami-v2"
        private const val HF_BASE_URL = "https://huggingface.co/api"
    }

    suspend fun fetchModelList(author: String): List<HuggingFaceModelSummary> {
        val listUrl = "$BASE_URL?author=$author&full=true"
        val request = Request.Builder().url(listUrl).build()

        return executeRequest(request) { response ->
            json.decodeFromString<List<HfModelDto>>(response.body.string()).mapNotNull {
                parseModelSummary(it)
            }
        }
    }

    suspend fun fetchFileSizes(modelId: String): Map<String, Long> {
        val url = "$BASE_URL/$modelId/tree/main"
        val request = Request.Builder().url(url).build()

        return try {
            executeRequest(request) { response ->
                val files = json.decodeFromString<List<HfFileDto>>(response.body.string())
                val sizeMap = mutableMapOf<String, Long>()
                for (file in files) {
                    val path = file.path ?: continue
                    val size =
                        file.lfs?.size?.takeIf { it > 0 } ?: file.size.takeIf { it > 0 } ?: continue
                    sizeMap[path] = size
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
                json.decodeFromString<HfUserDto>(response.body.string()).name?.also {
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
            val dto = json.decodeFromString<HfAuthorDto>(response.body.string())
            AuthorInfo(
                name = name,
                avatarUrl = dto.avatarUrl ?: dto.avatar,
                modelCount = dto.numModels,
            )
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

    private fun parseModelSummary(dto: HfModelDto): HuggingFaceModelSummary? {
        return try {
            val gatedValue = dto.gated?.jsonPrimitive?.content
            HuggingFaceModelSummary(
                id = dto.id,
                pipelineTag = dto.pipeline_tag,
                gated = GatedStatus.fromApiValue(gatedValue),
                downloads = dto.downloads,
                likes = dto.likes,
            )
        } catch (_: Exception) {
            null
        }
    }
}
