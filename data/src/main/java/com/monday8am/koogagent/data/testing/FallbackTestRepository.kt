package com.monday8am.koogagent.data.testing

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Wraps a primary test repository with a fallback to bundled tests.
 *
 * If the primary repository fails (e.g., network error), returns the fallback tests. This ensures
 * the app always has some tests available.
 */
class FallbackTestRepository(
    private val primary: TestRepository,
    private val fallback: TestRepository = AssetsTestRepository(),
) : TestRepository {

    private val logger = Logger.withTag("FallbackTestRepository")

    override suspend fun getTests(): List<TestCaseDefinition> {
        return try {
            primary.getTests()
        } catch (e: Exception) {
            logger.w { "Primary repository failed, using fallback: ${e.message}" }
            fallback.getTests()
        }
    }

    override fun getTestsAsFlow(): Flow<List<TestCaseDefinition>> =
        primary
            .getTestsAsFlow()
            .catch { error ->
                logger.w { "Primary repository failed: ${error.message}" }
                logger.w { "Using fallback bundled tests" }
                emit(fallback.getTests())
            }
            .map { tests ->
                if (tests.isNotEmpty()) {
                    tests
                } else {
                    logger.w { "Primary repository returned empty list. Using fallback." }
                    fallback.getTests()
                }
            }
}
