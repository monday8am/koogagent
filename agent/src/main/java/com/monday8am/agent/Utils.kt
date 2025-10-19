package com.monday8am.agent

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.feature.writer.FeatureMessageLogWriter
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

const val DEFAULT_MAX_TOKEN = 1024
const val DEFAULT_TOPK = 40
const val DEFAULT_TOPP = 0.9f
const val DEFAULT_TEMPERATURE = 0.5f

data class LocalLLModel(
    val path: String,
    val maxToken: Int = DEFAULT_MAX_TOKEN,
    val topK: Int = DEFAULT_TOPK,
    val topP: Float = DEFAULT_TOPP,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val shouldEnableImage: Boolean = false,
    val shouldEnableAudio: Boolean = false,
    val isGPUAccelerated: Boolean = true,
)

private val traceLogger: KLogger = KotlinLogging.logger("ai.koog.agents.tracing")

val installCommonEventHandling: FeatureContext.() -> Unit = {
    /*
    install(Tracing) {
        addMessageProcessor(TraceFeatureMessageLogWriter(logLevel = FeatureMessageLogWriter.LogLevel.INFO, targetLogger = traceLogger))
    }
     */
    handleEvents {
        onToolCallStarting { eventContext ->
            println("Tool called: ${eventContext.tool} with args ${eventContext.toolArgs}")
        }
        onAgentCompleted { eventContext ->
            println("Agent finished with result: ${eventContext.result}")
        }
        onAgentExecutionFailed { errorContext ->
            println("Agent error with result: ${errorContext.throwable}")
        }
    }
}
