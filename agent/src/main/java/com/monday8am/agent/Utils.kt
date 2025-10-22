package com.monday8am.agent

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.features.eventHandler.feature.handleEvents
import co.touchlab.kermit.Logger
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

// Gemma 3n-1b-it context lengths: 1280, 2048, or 4096 tokens
// We use 4096 as the total context window (input + output combined)
const val DEFAULT_CONTEXT_LENGTH = 4096
const val DEFAULT_MAX_OUTPUT_TOKENS = 512  // Leave ~3584 tokens for input
const val DEFAULT_TOPK = 40
const val DEFAULT_TOPP = 0.9f
const val DEFAULT_TEMPERATURE = 0.5f

data class LocalLLModel(
    val path: String,
    val contextLength: Int = DEFAULT_CONTEXT_LENGTH,  // Total tokens (input + output)
    val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,  // Max tokens to generate
    val topK: Int = DEFAULT_TOPK,
    val topP: Float = DEFAULT_TOPP,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val shouldEnableImage: Boolean = false,
    val shouldEnableAudio: Boolean = false,
    val isGPUAccelerated: Boolean = true,
)

private val traceLogger: KLogger = KotlinLogging.logger("ai.koog.agents.tracing")
private val kermitLogger = Logger.withTag("ModelEventHandling")

val installCommonEventHandling: FeatureContext.() -> Unit = {
    /*
    install(Tracing) {
        addMessageProcessor(TraceFeatureMessageLogWriter(logLevel = FeatureMessageLogWriter.LogLevel.INFO, targetLogger = traceLogger))
    }
     */
    handleEvents {
        onToolCallStarting { eventContext ->
            kermitLogger.d{ "Tool called: ${eventContext.tool} with args ${eventContext.toolArgs}" }
        }
        onAgentCompleted { eventContext ->
            kermitLogger.d{ "Agent finished with result: ${eventContext.result}" }
        }
        onAgentExecutionFailed { errorContext ->
            kermitLogger.d { "Agent error with result: ${errorContext.throwable}" }
        }
    }
}
