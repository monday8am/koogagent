package com.monday8am.agent.jvm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.monday8am.agent.core.DEFAULT_CONTEXT_LENGTH
import com.monday8am.agent.core.DEFAULT_TEMPERATURE
import com.monday8am.agent.core.DEFAULT_TOPK
import com.monday8am.agent.core.DEFAULT_TOPP
import com.monday8am.agent.tools.NativeLocationTools
import com.monday8am.agent.tools.NativeWeatherTools

private val gemma3n = "/Users/anton/Downloads/gemma3-1b-it-int4.litertlm"
private val qwen3 = "/Users/anton/Downloads/qwen3_0.6b_q8_ekv4096.litertlm"
private val qwen2_5 = "/Users/anton/Downloads/qwen2.5-1.5B-Instruct_q8_ekv4096.litertlm"

suspend fun main() {
    Engine.setNativeMinLogServerity(LogSeverity.INFO) // hide log for TUI app

    val engineConfig =
        EngineConfig(
            backend = Backend.CPU,
            maxNumTokens = DEFAULT_CONTEXT_LENGTH,
            modelPath = qwen3, // gemma3n
        )
    Engine(engineConfig).use { engine ->
        engine.initialize()

        // Native LiteRT-LM tools with @Tool annotations
        // These are passed to ConversationConfig for native tool calling
        val nativeTools =
            listOf(
                NativeLocationTools(),
                NativeWeatherTools(),
            )

        val conversationConfig =
            ConversationConfig(
                systemMessage = Message.of("/no_think You are Qwen, created by Alibaba Cloud. You are a helpful assistant."),
                tools = nativeTools, // Native LiteRT-LM tools with @Tool annotations
                samplerConfig =
                    SamplerConfig(
                        topK = DEFAULT_TOPK,
                        topP = DEFAULT_TOPP.toDouble(),
                        temperature = DEFAULT_TEMPERATURE.toDouble(),
                    ),
            )

        engine.createConversation(conversationConfig = conversationConfig).use { conversation ->
            while (true) {
                print("\n>>> ")
                conversation.sendMessageAsync(Message.of(readln())).collect { print(it) }
            }
        }
    }
}
