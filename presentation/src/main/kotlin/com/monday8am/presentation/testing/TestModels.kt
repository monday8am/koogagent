package com.monday8am.presentation.testing

import com.monday8am.koogagent.data.testing.TestDomain

/** A single test query with optional description. */
data class TestQuery(val text: String, val description: String? = null)

/** Validation result for a test case. */
sealed class ValidationResult {
    data class Pass(val message: String) : ValidationResult()

    data class Fail(val message: String, val details: String? = null) : ValidationResult()
}

/**
 * Streaming test result frames emitted during test execution. Used by UI to show real-time
 * progress.
 */
sealed interface TestResultFrame {
    val testName: String
    val id: String

    data class Description(
        override val testName: String,
        val description: String,
        val systemPrompt: String,
    ) : TestResultFrame {
        override val id: String = "$testName-description"
    }

    data class Query(override val testName: String, val query: String) : TestResultFrame {
        override val id: String = "$testName-query"
    }

    data class Tool(override val testName: String, val content: String, val accumulator: String) :
        TestResultFrame {
        override val id: String = "$testName-tool"
    }

    data class Content(override val testName: String, val chunk: String, val accumulator: String) :
        TestResultFrame {
        override val id: String = "$testName-content"
    }

    data class Thinking(override val testName: String, val chunk: String, val accumulator: String) :
        TestResultFrame {
        override val id: String = "$testName-thinking"
    }

    data class Validation(
        override val testName: String,
        val result: ValidationResult,
        val duration: Long,
        val fullContent: String,
    ) : TestResultFrame {
        override val id: String = "$testName-validation"
    }
}

/** Framework-agnostic test case definition. */
data class TestCase(
    val name: String,
    val description: List<String> = emptyList(),
    val queries: List<TestQuery>,
    val systemPrompt: String,
    val validator: (result: String) -> ValidationResult,
    val parseThinkingTags: Boolean = true,
    val context: Map<String, Any?> = emptyMap(),
    val mockToolResponses: Map<String, String> = emptyMap(),
    val domain: TestDomain = TestDomain.GENERIC,
)
