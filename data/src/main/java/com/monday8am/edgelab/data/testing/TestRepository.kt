package com.monday8am.edgelab.data.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

interface TestRepository {
    suspend fun getTests(): List<TestCaseDefinition>

    fun getTestsAsFlow(): Flow<List<TestCaseDefinition>>
}

class AssetsTestRepository : TestRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun getTests(): List<TestCaseDefinition> {
        val jsonString =
            this::class
                .java
                .classLoader
                ?.getResource("com/monday8am/edgelab/data/testing/tool_tests.json")
                ?.readText()
                ?: throw IllegalStateException("tool_tests.json not found in resources")

        return json.decodeFromString<TestSuiteDefinition>(jsonString).tests
    }

    override fun getTestsAsFlow(): Flow<List<TestCaseDefinition>> = flow {
        try {
            emit(getTests())
        } catch (e: Exception) {
            emit(emptyList())
        }
    }
}
