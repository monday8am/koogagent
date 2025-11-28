package com.monday8am.agent.core

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.features.eventHandler.feature.handleEvents
import co.touchlab.kermit.Logger
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.AclSetuserArgs.Builder.on

// Model context lengths (MUST match .litertlm compilation settings):
// - Gemma 3n-1b-it: 1280, 2048, or 4096 tokens
// - Qwen3-0.6B: 32768 tokens max (32K), but compiled .litertlm may be smaller
// Current Qwen3 model compiled with: 1024 tokens (ekv1024 = extended KV cache 1024)
const val DEFAULT_CONTEXT_LENGTH = 4096
const val DEFAULT_MAX_OUTPUT_TOKENS = (DEFAULT_CONTEXT_LENGTH * 0.25).toInt()
const val DEFAULT_TOPK = 40
const val DEFAULT_TOPP = 0.85f
const val DEFAULT_TEMPERATURE = 0.2f
const val MODEL_URL = "https://github.com/monday8am/koogagent/releases/download/0.0.1/gemma3-1b-it-int4.zip"
const val MODEL_NAME1 = "gemma3-1b-it-int4.litertlm"
const val MODEL_NAME = "qwen3_0.6b_q8_ekv4096.litertlm"

data class LocalLLModel(
    val path: String,
    val contextLength: Int = DEFAULT_CONTEXT_LENGTH, // Total tokens (input + output)
    val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS, // Max tokens to generate
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
        onLLMCallStarting {
            kermitLogger.d { "LLM call started with prompt: ${it.prompt}" }
        }
        onAgentStarting {
            kermitLogger.d { "Agent started: ${it.agent.id}" }
        }
        onAgentClosing {
            kermitLogger.d { "Agent closed: ${it.agentId}" }
        }
        onLLMCallCompleted {
            kermitLogger.d { "LLM call ended with \ntools: ${it.tools}\nresponses:${it.responses}" }
        }
        onToolCallStarting { eventContext ->
            kermitLogger.d { "Tool called: ${eventContext.tool} with args ${eventContext.toolArgs}" }
        }
        onToolCallCompleted {
            kermitLogger.d { "Tool call ended with result: ${it.result}" }
        }
        onAgentCompleted { eventContext ->
            kermitLogger.d { "Agent finished with result: ${eventContext.result}" }
        }
        onAgentExecutionFailed { errorContext ->
            kermitLogger.d { "Agent error with result: ${errorContext.throwable}" }
        }
    }
}
