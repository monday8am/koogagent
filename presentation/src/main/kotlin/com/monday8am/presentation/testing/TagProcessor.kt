package com.monday8am.presentation.testing

internal enum class ParserState {
    Content,
    Thinking,
    ToolCall,
}

/**
 * Internal processor to handle tag-based streaming state.
 *
 * Supports multiple thinking tag formats:
 * - Qwen models: <think> ... </think>
 * - DeepSeek models: <thinking> ... </thinking>
 */
internal class TagProcessor(private val testName: String, private val parseTags: Boolean) {
    private val currentBlock = StringBuilder()
    private var state = ParserState.Content
    private var thinkingTagType: String? = null // Track which thinking tag type is being used

    /** The content accumulated for validation (after stripping tags if enabled) */
    val resultContent: String
        get() = currentBlock.toString()

    fun process(chunk: String): TestResultFrame {
        currentBlock.append(chunk)

        if (!parseTags) {
            return TestResultFrame.Content(testName, chunk, currentBlock.toString())
        }

        // Simple state machine to detect tags. Using a window to handle split tokens.
        // We check the tail of currentBlock for state transitions.
        val lookBack = currentBlock.takeLast(25).toString()

        when (state) {
            ParserState.Content -> {
                // Check for thinking tags (DeepSeek uses <thinking>, Qwen uses <think>)
                if (lookBack.contains("<thinking>")) {
                    state = ParserState.Thinking
                    thinkingTagType = "<thinking>"
                    stripTag("<thinking>")
                } else if (lookBack.contains("<think>")) {
                    state = ParserState.Thinking
                    thinkingTagType = "<think>"
                    stripTag("<think>")
                } else if (lookBack.contains("<tool_call")) {
                    state = ParserState.ToolCall
                    stripTag("<tool_call")
                }
            }

            ParserState.Thinking -> {
                // Check for corresponding closing tag
                val closingTag =
                    when (thinkingTagType) {
                        "<thinking>" -> "</thinking>"
                        "<think>" -> "</think>"
                        else -> "</think>" // Fallback
                    }

                if (lookBack.contains(closingTag)) {
                    state = ParserState.Content
                    stripTag(closingTag, clearBefore = true)
                    thinkingTagType = null
                }
            }

            ParserState.ToolCall -> {
                if (lookBack.contains("</tool_call>")) {
                    state = ParserState.Content
                    stripTag("</tool_call>", clearBefore = true)
                }
            }
        }

        return when (state) {
            ParserState.Thinking ->
                TestResultFrame.Thinking(testName, chunk, currentBlock.toString())
            ParserState.ToolCall -> TestResultFrame.Tool(testName, chunk, currentBlock.toString())
            ParserState.Content -> TestResultFrame.Content(testName, chunk, currentBlock.toString())
        }
    }

    private fun stripTag(tag: String, clearBefore: Boolean = false) {
        val content = currentBlock.toString()
        val index = content.lastIndexOf(tag)
        if (index != -1) {
            if (clearBefore) {
                val remaining = content.substring(index + tag.length)
                currentBlock.clear()
                currentBlock.append(remaining)
            } else {
                // Just remove the tag itself if we're starting a new block
                // Actually, if we're starting <think>, we might want to clear previous content
                // if it's just whitespace or if the contract is "one block at a time".
                // The original code cleared on END tags.
                // For start tags, let's just keep everything for now but remove the tag.
                currentBlock.delete(index, index + tag.length)
            }
        }
    }
}
