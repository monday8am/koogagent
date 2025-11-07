package com.monday8am.agent.local

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Vibe code for good vibes: Claude Desktop!
class GemmaLLMClientTest {
    private lateinit var client: SimpleLLMClient

    // Shared tool definitions to avoid repetition
    private val getWeatherTool =
        ToolDescriptor(
            name = "GetWeather",
            description = "Gets current weather",
            requiredParameters = emptyList(),
            optionalParameters = emptyList(),
        )

    private val getLocationTool =
        ToolDescriptor(
            name = "GetLocation",
            description = "Gets current location",
            requiredParameters = emptyList(),
            optionalParameters = emptyList(),
        )

    @Before
    fun setup() {
        // Create a dummy instance - we won't call execute() in these tests
        client = SimpleLLMClient(promptExecutor = { "result!" })
    }

    @Test
    fun `buildToolInstructions returns empty string when no tools provided`() {
        val result = client.buildToolInstructions(emptyList())
        assertEquals("", result)
    }

    @Test
    fun `buildToolInstructions formats single tool correctly`() {
        val tools = listOf(getWeatherTool)

        val result = client.buildToolInstructions(tools)

        assertTrue(result.contains("Available tools:"))
        assertTrue(result.contains("- GetWeather: Gets current weather"))
        assertTrue(result.contains("""{"tool":"ToolName"}"""))
        assertTrue(result.contains("""{"tool":"none"}"""))
    }

    @Test
    fun `buildToolInstructions formats multiple tools correctly`() {
        val tools = listOf(getWeatherTool, getLocationTool)

        val result = client.buildToolInstructions(tools)

        assertTrue(result.contains("- GetWeather: Gets current weather"))
        assertTrue(result.contains("- GetLocation: Gets current location"))
    }

    @Test
    fun `buildSubsequentTurnPrompt extracts user message correctly`() {
        val messages =
            listOf(
                Message.User(content = "Hello", metaInfo = RequestMetaInfo.Empty),
            )

        val result = client.buildSubsequentTurnPrompt(messages)

        assertEquals("User:Hello", result)
    }

    @Test
    fun `buildSubsequentTurnPrompt extracts assistant message correctly`() {
        val messages =
            listOf(
                Message.Assistant(content = "Hi there!", metaInfo = ResponseMetaInfo.Empty),
            )

        val result = client.buildSubsequentTurnPrompt(messages)

        assertEquals("Assistant:Hi there!", result)
    }

    @Test
    fun `buildSubsequentTurnPrompt extracts tool call correctly`() {
        val messages =
            listOf(
                Message.Tool.Call(
                    id = "123",
                    tool = "GetWeather",
                    content = "{}",
                    metaInfo = ResponseMetaInfo.Empty,
                ),
            )

        val result = client.buildSubsequentTurnPrompt(messages)

        assertEquals("""Assistant:{"tool":"GetWeather"}""", result)
    }

    @Test
    fun `buildSubsequentTurnPrompt extracts tool result correctly`() {
        val messages =
            listOf(
                Message.Tool.Result(
                    id = "123",
                    tool = "GetWeather",
                    content = "Sunny, 72°F",
                    metaInfo = RequestMetaInfo.Empty,
                ),
            )

        val result = client.buildSubsequentTurnPrompt(messages)

        assertEquals("Tool 'GetWeather' returned: Sunny, 72°F", result)
    }

    @Test
    fun `buildSubsequentTurnPrompt filters out system messages`() {
        val messages =
            listOf(
                Message.System(content = "You are a helpful assistant", RequestMetaInfo.Empty),
                Message.User(content = "Hello", metaInfo = RequestMetaInfo.Empty),
            )

        val result = client.buildSubsequentTurnPrompt(messages)

        assertEquals("User:Hello", result)
        assertTrue(!result.contains("You are a helpful assistant"))
    }

    @Test
    fun `buildSubsequentTurnPrompt returns latest message only`() {
        val messages =
            listOf(
                Message.User(content = "Hello", metaInfo = RequestMetaInfo.Empty),
                Message.Assistant(content = "Hi!", metaInfo = ResponseMetaInfo.Empty),
            )

        val result = client.buildSubsequentTurnPrompt(messages)

        assertEquals("Assistant:Hi!", result)
    }

    @Test
    fun `buildSubsequentTurnPrompt extracts latest from tool calling flow`() {
        val messages =
            listOf(
                Message.User(content = "What's the weather?", metaInfo = RequestMetaInfo.Empty),
                Message.Tool.Call(
                    id = "1",
                    tool = "GetWeather",
                    content = "{}",
                    metaInfo = ResponseMetaInfo.Empty,
                ),
                Message.Tool.Result(
                    id = "1",
                    tool = "GetWeather",
                    content = "Sunny, 72°F",
                    metaInfo = RequestMetaInfo.Empty,
                ),
            )

        val result = client.buildSubsequentTurnPrompt(messages)

        assertEquals("Tool 'GetWeather' returned: Sunny, 72°F", result)
    }

    // ==================== buildFirstTurnPrompt Tests ====================

    @Test
    fun `buildFirstTurnPrompt includes system message`() {
        val prompt =
            Prompt(
                id = "promptId",
                messages =
                    listOf(
                        Message.System(content = "You are helpful", metaInfo = RequestMetaInfo.Empty),
                        Message.User(content = "Hello", metaInfo = RequestMetaInfo.Empty),
                    ),
            )

        val result = client.buildFirstTurnPrompt(prompt, emptyList())

        assertTrue(result.startsWith("You are helpful"))
        assertTrue(result.contains("Hello"))
    }

    @Test
    fun `buildFirstTurnPrompt includes tool instructions when tools provided`() {
        val prompt =
            Prompt(
                id = "promptId",
                messages =
                    listOf(
                        Message.System(content = "You are helpful", metaInfo = RequestMetaInfo.Empty),
                        Message.User(content = "What's the weather?", metaInfo = RequestMetaInfo.Empty),
                    ),
            )

        val result = client.buildFirstTurnPrompt(prompt, listOf(getWeatherTool))

        assertTrue(result.contains("You are helpful"))
        assertTrue(result.contains("Available tools:"))
        assertTrue(result.contains("- GetWeather: Gets current weather"))
        assertTrue(result.contains("What's the weather?"))
    }

    @Test
    fun `buildFirstTurnPrompt works without system message`() {
        val prompt =
            Prompt(
                id = "promptId",
                messages =
                    listOf(
                        Message.User(content = "Hello", metaInfo = RequestMetaInfo.Empty),
                    ),
            )

        val result = client.buildFirstTurnPrompt(prompt, emptyList())

        assertEquals("Hello", result)
    }

    @Test
    fun `parseResponse detects tool call`() {
        val result = client.parseResponse("""{"tool":"GetWeather"}""", listOf(getWeatherTool))

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Tool.Call)
        assertEquals("GetWeather", (message as Message.Tool.Call).tool)
        assertEquals("{}", message.content)
    }

    @Test
    fun `parseResponse handles tool none with text response`() {
        val result =
            client.parseResponse(
                """
                {"tool":"none"}
                Hello! How can I help you?
                """.trimIndent(),
                emptyList(),
            )

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Assistant)
        assertEquals("Hello! How can I help you?", (message as Message.Assistant).content)
    }

    @Test
    fun `parseResponse handles non-existent tool gracefully`() {
        val result = client.parseResponse("""{"tool":"GetLocation"}""", listOf(getWeatherTool))

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Assistant)
        assertTrue((message as Message.Assistant).content.contains("doesn't exist"))
    }

    @Test
    fun `parseResponse handles response without tool pattern`() {
        val result = client.parseResponse("Just a regular response", emptyList())

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Assistant)
        assertEquals("Just a regular response", (message as Message.Assistant).content)
    }

    @Test
    fun `parseResponse handles tool pattern with extra whitespace`() {
        val result = client.parseResponse("""{ "tool" : "GetWeather" }""", listOf(getWeatherTool))

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Tool.Call)
        assertEquals("GetWeather", (message as Message.Tool.Call).tool)
    }

    @Test
    fun `parseResponse trims whitespace from tool name value`() {
        // Model might add leading/trailing space in the value
        val result = client.parseResponse("""{"tool":" GetWeather "}""", listOf(getWeatherTool))

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Tool.Call)
        assertEquals("GetWeather", (message as Message.Tool.Call).tool)
    }

    @Test
    fun `parseResponse extracts tool name correctly from mixed content`() {
        // Model might add extra text
        val result =
            client.parseResponse(
                """Let me check that for you. {"tool":"GetWeather"}""",
                listOf(getWeatherTool),
            )

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Tool.Call)
        assertEquals("GetWeather", (message as Message.Tool.Call).tool)
    }

    // ==================== End-to-End Integration Tests ====================

    @Test
    fun `execute returns tool call when model responds with tool pattern`() =
        runBlockingTest {
            // Mock the LLM to return a tool call
            val client =
                SimpleLLMClient(promptExecutor = { prompt ->
                    // Verify prompt contains tool instructions
                    assertTrue(prompt.contains("Available tools:"))
                    // Return a tool call
                    """{"tool":"GetWeather"}"""
                })

            val prompt =
                Prompt(
                    id = "test",
                    messages =
                        listOf(
                            Message.User(content = "What's the weather?", metaInfo = RequestMetaInfo.Empty),
                        ),
                )

            val result = client.execute(prompt, mockModel(), listOf(getWeatherTool))

            assertEquals(1, result.size)
            assertTrue(result[0] is Message.Tool.Call)
            assertEquals("GetWeather", (result[0] as Message.Tool.Call).tool)
        }

    @Test
    fun `execute returns assistant message when model responds with none`() =
        runBlockingTest {
            val client =
                SimpleLLMClient(promptExecutor = {
                    """
                    {"tool":"none"}
                    The weather is sunny today!
                    """.trimIndent()
                })

            val prompt =
                Prompt(
                    id = "test",
                    messages =
                        listOf(
                            Message.User(content = "What's the weather?", metaInfo = RequestMetaInfo.Empty),
                        ),
                )

            val result = client.execute(prompt, mockModel(), emptyList())

            assertEquals(1, result.size)
            assertTrue(result[0] is Message.Assistant)
            assertEquals("The weather is sunny today!", (result[0] as Message.Assistant).content)
        }

    @Test
    fun `execute handles model returning null`() =
        runBlockingTest {
            val client = SimpleLLMClient(promptExecutor = { null })

            val prompt =
                Prompt(
                    id = "test",
                    messages =
                        listOf(
                            Message.User(content = "Hello", metaInfo = RequestMetaInfo.Empty),
                        ),
                )

            val result = client.execute(prompt, mockModel(), emptyList())

            assertTrue(result.isEmpty())
        }

    @Test
    fun `execute builds correct prompt with system message and tools`() =
        runBlockingTest {
            var capturedPrompt = ""
            val client =
                SimpleLLMClient(promptExecutor = { prompt ->
                    capturedPrompt = prompt
                    """{"tool":"none"} Hello!"""
                })

            val promptObj =
                Prompt(
                    id = "test",
                    messages =
                        listOf(
                            Message.System(content = "You are helpful", metaInfo = RequestMetaInfo.Empty),
                            Message.User(content = "Hi", metaInfo = RequestMetaInfo.Empty),
                        ),
                )

            client.execute(promptObj, mockModel(), listOf(getWeatherTool))

            // Verify the built prompt
            assertTrue(capturedPrompt.contains("You are helpful"))
            assertTrue(capturedPrompt.contains("Available tools:"))
            assertTrue(capturedPrompt.contains("- GetWeather: Gets current weather"))
            assertTrue(capturedPrompt.contains("User: Hi"))
        }

    @Test
    fun `execute passes conversation history correctly`() =
        runBlockingTest {
            var capturedPrompt = ""
            val client =
                SimpleLLMClient(promptExecutor = { prompt ->
                    capturedPrompt = prompt
                    """{"tool":"GetWeather"}"""
                })

            val promptObj =
                Prompt(
                    id = "test",
                    messages =
                        listOf(
                            Message.User(content = "What's the weather?", metaInfo = RequestMetaInfo.Empty),
                            Message.Tool.Call(
                                id = "1",
                                tool = "GetWeather",
                                content = "{}",
                                metaInfo = ResponseMetaInfo.Empty,
                            ),
                            Message.Tool.Result(
                                id = "1",
                                tool = "GetWeather",
                                content = "Sunny, 72°F",
                                metaInfo = RequestMetaInfo.Empty,
                            ),
                            Message.User(content = "What about tomorrow?", metaInfo = RequestMetaInfo.Empty),
                        ),
                )

            client.execute(promptObj, mockModel(), listOf(getWeatherTool))

            // Verify conversation history is included
            assertTrue(capturedPrompt.contains("User: What's the weather?"))
            assertTrue(capturedPrompt.contains("""Assistant: {"tool":"GetWeather"}"""))
            assertTrue(capturedPrompt.contains("Tool 'GetWeather' returned: Sunny, 72°F"))
            assertTrue(capturedPrompt.contains("User: What about tomorrow?"))
        }

    // Helper to create a mock LLModel
    private fun mockModel() =
        LLModel(
            provider = LLMProvider.Google,
            id = "test-model",
            capabilities = listOf(LLMCapability.Tools),
            maxOutputTokens = 1024,
            contextLength = 8192,
        )

    // Helper for running suspending functions in tests
    private fun runBlockingTest(block: suspend () -> Unit) {
        runBlocking {
            block()
        }
    }
}
