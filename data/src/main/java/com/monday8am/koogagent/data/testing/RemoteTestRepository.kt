package com.monday8am.koogagent.data.testing

import co.touchlab.kermit.Logger
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class RemoteTestRepository(
    private val remoteUrl: String,
    private val localTestDataSource: LocalTestDataSource,
    private val json: Json,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TestRepository {

    private val logger = Logger.withTag("RemoteTestRepository")

    override suspend fun getTests(): List<TestCaseDefinition> {
        return localTestDataSource.getTests() ?: fetchRemoteTests()
    }

    override fun getTestsAsFlow(): Flow<List<TestCaseDefinition>> =
        flow {
                // 1. Emit local data immediately (cache-first pattern)
                val localTests = localTestDataSource.getTests()
                var lastEmittedTests: List<TestCaseDefinition>? = null

                if (!localTests.isNullOrEmpty()) {
                    emit(localTests)
                    lastEmittedTests = localTests
                }

                // 2. Fetch from network to refresh
                try {
                    val newTests = fetchRemoteTests()

                    if (newTests.isNotEmpty()) {
                        logger.d { "Successfully loaded ${newTests.size} tests from remote" }

                        // 3. Save to local storage
                        localTestDataSource.saveTests(newTests)

                        // 4. Emit new data only if different from cached data (deduplication)
                        if (newTests != lastEmittedTests) {
                            emit(newTests)
                        } else {
                            logger.d {
                                "Remote tests identical to cached tests, skipping duplicate emission"
                            }
                        }
                    } else {
                        logger.w { "Remote fetch returned empty test list" }
                        // Don't emit empty if we already emitted cached data
                        if (lastEmittedTests == null) {
                            emit(emptyList())
                        }
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Failed to refresh tests from remote URL" }
                    // Emit empty list only if we haven't emitted anything yet
                    if (localTests.isNullOrEmpty()) {
                        emit(emptyList())
                    }
                }
            }
            .flowOn(dispatcher)

    private suspend fun fetchRemoteTests(): List<TestCaseDefinition> {
        val request = Request.Builder().url(remoteUrl).build()

        return executeRequest(request) { response ->
            val body = response.body.string()
            val suite = json.decodeFromString<TestSuiteDefinition>(body)
            suite.tests
        }
    }

    private suspend fun <T> executeRequest(request: Request, parser: (Response) -> T): T {
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
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
}
