package com.monday8am.edgelab.agent.core

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.features.eventHandler.feature.handleEvents
import co.touchlab.kermit.Logger
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

private val traceLogger: KLogger = KotlinLogging.logger("ai.koog.agents.tracing")
private val kermitLogger = Logger.withTag("ModelEventHandling")

val installCommonEventHandling: FeatureContext.() -> Unit = {
    /*
    install(Tracing) {
        addMessageProcessor(TraceFeatureMessageLogWriter(logLevel = FeatureMessageLogWriter.LogLevel.INFO, targetLogger = traceLogger))
    }
     */
    handleEvents {
        onLLMCallStarting { kermitLogger.d { "LLM call started with prompt: ${it.prompt}" } }
        onAgentStarting { kermitLogger.d { "Agent started: ${it.agent.id}" } }
        onAgentClosing { kermitLogger.d { "Agent closed: ${it.agentId}" } }
        onLLMCallCompleted {
            kermitLogger.d { "LLM call ended with \ntools: ${it.tools}\nresponses:${it.responses}" }
        }
        onToolCallStarting { eventContext ->
            kermitLogger.d {
                "Tool called: ${eventContext.toolName} with args ${eventContext.toolArgs}"
            }
        }
        onToolCallCompleted { kermitLogger.d { "Tool call ended with result: ${it.toolName}" } }
        onAgentCompleted { eventContext ->
            kermitLogger.d { "Agent finished with result: ${eventContext.result}" }
        }
        onAgentExecutionFailed { errorContext ->
            kermitLogger.d { "Agent error with result: ${errorContext.throwable}" }
        }
    }
}
