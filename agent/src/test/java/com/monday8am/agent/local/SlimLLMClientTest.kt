package com.monday8am.agent.local

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlin.test.Ignore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SlimLLMClientTest {
    private lateinit var client: SlimLLMClient

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

    private val getWeatherWithParamsTool =
        ToolDescriptor(
            name = "GetWeatherFromLocation",
            description = "Gets weather for a specific location",
            requiredParameters =
                listOf(
                    ToolParameterDescriptor(
                        name = "latitude",
                        description = "Latitude coordinate",
                        type = ToolParameterType.String,
                    ),
                    ToolParameterDescriptor(
                        name = "longitude",
                        description = "Longitude coordinate",
                        type = ToolParameterType.String,
                    ),
                ),
            optionalParameters = emptyList(),
        )

    @Before
    fun setup() {
        // Create a dummy instance - we won't call execute() in these tests
        client = SlimLLMClient(promptExecutor = { "result!" })
    }

    // ==================== buildSlimToolInstructions Tests ====================

    @Test
    fun `buildSlimToolInstructions returns empty string when no tools provided`() {
        val result = client.buildSlimToolInstructions(emptyList())
        assertEquals("", result)
    }

    @Test
    fun `buildSlimToolInstructions formats single tool correctly`() {
        val tools = listOf(getWeatherTool)

        val result = client.buildSlimToolInstructions(tools)

        assertTrue(result.contains("Available functions:"))
        assertTrue(result.contains("- GetWeather: Gets current weather"))
        assertTrue(result.contains("<function>FunctionName</function>"))
        assertTrue(result.contains("<parameters>"))
    }

    @Test
    fun `buildSlimToolInstructions formats multiple tools correctly`() {
        val tools = listOf(getWeatherTool, getLocationTool)

        val result = client.buildSlimToolInstructions(tools)

        assertTrue(result.contains("- GetWeather: Gets current weather"))
        assertTrue(result.contains("- GetLocation: Gets current location"))
    }

    @Test
    fun `buildSlimToolInstructions shows parameters for tools with params`() {
        val tools = listOf(getWeatherWithParamsTool)

        val result = client.buildSlimToolInstructions(tools)

        assertTrue(result.contains("- GetWeatherFromLocation: Gets weather for a specific location (parameters: latitude, longitude)"))
    }

    @Ignore("Outdated!")
    fun `buildSlimToolInstructions includes SLIM format examples`() {
        val tools = listOf(getLocationTool)

        val result = client.buildSlimToolInstructions(tools)

        assertTrue(result.contains("EXAMPLES:"))
        assertTrue(result.contains("<function>GetLocation</function>"))
        assertTrue(result.contains("latitude: 48.8534, longitude: 2.3488"))
    }

    // ==================== buildSubsequentTurnPrompt Tests ====================

    @Test
    fun `buildSubsequentTurnPrompt extracts user message correctly`() {
        val messages =
            listOf(
                Message.User(content = "Hello", metaInfo = RequestMetaInfo.Empty),
            )

        val result = client.buildSubsequentTurnPrompt(messages)

        assertEquals("Hello", result)
    }

    @Test
    fun `buildSubsequentTurnPrompt extracts assistant message correctly`() {
        val messages =
            listOf(
                Message.Assistant(content = "Hi there!", metaInfo = ResponseMetaInfo.Empty),
            )

        val result = client.buildSubsequentTurnPrompt(messages)

        assertEquals("Hi there!", result)
    }

    @Test
    fun `buildSubsequentTurnPrompt extracts tool call without parameters`() {
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

        assertEquals("<function>GetWeather</function>", result)
    }

    @Test
    fun `buildSubsequentTurnPrompt extracts tool call with parameters`() {
        val messages =
            listOf(
                Message.Tool.Call(
                    id = "123",
                    tool = "GetWeatherFromLocation",
                    content = """{"latitude": "48.8534", "longitude": "2.3488"}""",
                    metaInfo = ResponseMetaInfo.Empty,
                ),
            )

        val result = client.buildSubsequentTurnPrompt(messages)

        assertEquals(
            """<function>GetWeatherFromLocation</function>
<parameters>{"latitude": "48.8534", "longitude": "2.3488"}</parameters>""",
            result,
        )
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

        assertEquals("Tool result: Sunny, 72°F", result)
    }

    @Test
    fun `buildSubsequentTurnPrompt filters out system messages`() {
        val messages =
            listOf(
                Message.System(content = "You are a helpful assistant", RequestMetaInfo.Empty),
                Message.User(content = "Hello", metaInfo = RequestMetaInfo.Empty),
            )

        val result = client.buildSubsequentTurnPrompt(messages)

        assertEquals("Hello", result)
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

        assertEquals("Hi!", result)
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
        assertTrue(result.contains("Available functions:"))
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

    // ==================== parseResponse Tests ====================

    @Test
    fun `parseResponse detects tool call without parameters`() {
        val result = client.parseResponse("<function>GetWeather</function>", listOf(getWeatherTool))

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Tool.Call)
        assertEquals("GetWeather", (message as Message.Tool.Call).tool)
        assertEquals("{}", message.content)
    }

    @Test
    fun `parseResponse detects tool call with parameters`() {
        val response =
            """
            <function>GetWeatherFromLocation</function>
            <parameters>{"latitude": "48.8534", "longitude": "2.3488"}</parameters>
            """.trimIndent()

        val result = client.parseResponse(response, listOf(getWeatherWithParamsTool))

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Tool.Call)
        assertEquals("GetWeatherFromLocation", (message as Message.Tool.Call).tool)
        assertEquals("""{"latitude": "48.8534", "longitude": "2.3488"}""", message.content)
    }

    @Test
    fun `parseResponse handles function none with text response`() {
        val result =
            client.parseResponse(
                """
                <function>none</function>
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
    fun `parseResponse handles function none without additional text`() {
        val result = client.parseResponse("<function>none</function>", emptyList())

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Assistant)
        assertEquals("I can help with that without using any tools.", (message as Message.Assistant).content)
    }

    @Test
    fun `parseResponse handles non-existent tool gracefully`() {
        val result = client.parseResponse("<function>GetLocation</function>", listOf(getWeatherTool))

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Assistant)
        assertTrue((message as Message.Assistant).content.contains("doesn't exist"))
    }

    @Test
    fun `parseResponse handles response without function tags`() {
        val result = client.parseResponse("Just a regular response", emptyList())

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Assistant)
        assertEquals("Just a regular response", (message as Message.Assistant).content)
    }

    @Test
    fun `parseResponse handles function tag with extra whitespace`() {
        val result = client.parseResponse("<function>  GetWeather  </function>", listOf(getWeatherTool))

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Tool.Call)
        assertEquals("GetWeather", (message as Message.Tool.Call).tool)
    }

    @Test
    fun `parseResponse handles parameters with extra whitespace`() {
        val response =
            """
            <function>GetWeatherFromLocation</function>
            <parameters>  {"latitude": "48.8534"}  </parameters>
            """.trimIndent()

        val result = client.parseResponse(response, listOf(getWeatherWithParamsTool))

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Tool.Call)
        assertEquals("""{"latitude": "48.8534"}""", (message as Message.Tool.Call).content)
    }

    @Test
    fun `parseResponse extracts function from mixed content`() {
        // Model might add extra text before function tag
        val result =
            client.parseResponse(
                "Let me check that for you.\n<function>GetWeather</function>",
                listOf(getWeatherTool),
            )

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Tool.Call)
        assertEquals("GetWeather", (message as Message.Tool.Call).tool)
    }

    @Test
    fun `parseResponse handles case-insensitive none`() {
        val result = client.parseResponse("<function>NoNe</function>", emptyList())

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Assistant)
    }

    // ==================== Infinite Loop Detection Tests ====================

    @Test
    fun `parseResponse detects infinite loop and breaks it`() {
        val conversationHistory =
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

        // Model tries to call GetWeather again
        val result =
            client.parseResponse(
                "<function>GetWeather</function>",
                listOf(getWeatherTool),
                conversationHistory,
            )

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Assistant)
        assertTrue((message as Message.Assistant).content.contains("I have the information"))
        assertTrue(message.content.contains("Sunny, 72°F"))
    }

    @Test
    fun `parseResponse allows calling different tool after result`() {
        val conversationHistory =
            listOf(
                Message.User(content = "Where am I?", metaInfo = RequestMetaInfo.Empty),
                Message.Tool.Call(
                    id = "1",
                    tool = "GetLocation",
                    content = "{}",
                    metaInfo = ResponseMetaInfo.Empty,
                ),
                Message.Tool.Result(
                    id = "1",
                    tool = "GetLocation",
                    content = "latitude: 48.8534, longitude: 2.3488",
                    metaInfo = RequestMetaInfo.Empty,
                ),
            )

        // Model now calls GetWeather (different tool) - should be allowed
        val result =
            client.parseResponse(
                "<function>GetWeather</function>",
                listOf(getWeatherTool, getLocationTool),
                conversationHistory,
            )

        assertEquals(1, result.size)
        val message = result[0]
        assertTrue(message is Message.Tool.Call)
        assertEquals("GetWeather", (message as Message.Tool.Call).tool)
    }

    // ==================== End-to-End Integration Tests ====================

    @Test
    fun `execute returns tool call when model responds with SLIM function tag`() =
        runBlockingTest {
            // Mock the LLM to return a tool call
            val client =
                SlimLLMClient(promptExecutor = { prompt ->
                    // Verify prompt contains tool instructions
                    assertTrue(prompt.contains("Available functions:"))
                    // Return a tool call
                    "<function>GetWeather</function>"
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
    fun `execute returns tool call with parameters`() =
        runBlockingTest {
            val client =
                SlimLLMClient(promptExecutor = {
                    """
                    <function>GetWeatherFromLocation</function>
                    <parameters>{"latitude": "40.4168", "longitude": "-3.7038"}</parameters>
                    """.trimIndent()
                })

            val prompt =
                Prompt(
                    id = "test",
                    messages =
                        listOf(
                            Message.User(content = "Weather in Madrid?", metaInfo = RequestMetaInfo.Empty),
                        ),
                )

            val result = client.execute(prompt, mockModel(), listOf(getWeatherWithParamsTool))

            assertEquals(1, result.size)
            assertTrue(result[0] is Message.Tool.Call)
            val call = result[0] as Message.Tool.Call
            assertEquals("GetWeatherFromLocation", call.tool)
            assertEquals("""{"latitude": "40.4168", "longitude": "-3.7038"}""", call.content)
        }

    @Test
    fun `execute returns assistant message when model responds with none`() =
        runBlockingTest {
            val client =
                SlimLLMClient(promptExecutor = {
                    """
                    <function>none</function>
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
            val client = SlimLLMClient(promptExecutor = { null })

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
                SlimLLMClient(promptExecutor = { prompt ->
                    capturedPrompt = prompt
                    "<function>none</function> Hello!"
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
            assertTrue(capturedPrompt.contains("Available functions:"))
            assertTrue(capturedPrompt.contains("- GetWeather: Gets current weather"))
            assertTrue(capturedPrompt.contains("Hi"))
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
