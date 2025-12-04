package com.monday8am.agent.jvm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.monday8am.agent.tools.NativeLocationTools
import com.monday8am.agent.tools.NativeWeatherTools

private val gemma3n = "/Users/anton/Downloads/gemma3-1b-it-int4.litertlm"
private val qwen3 = "/Users/anton/Downloads/qwen3_0.6b_q8_ekv4096.litertlm"
private val hammer2p1 = "/Users/anton/Downloads/hammer2.1_0.5b_q8_ekv4096.litertlm"

suspend fun main() {
    Engine.setNativeMinLogServerity(LogSeverity.INFO) // hide log for TUI app

    val engineConfig =
        EngineConfig(
            backend = Backend.CPU,
            maxNumTokens = 4096,
            modelPath = hammer2p1, // gemma3n
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
                systemMessage = Message.of("You are Qwen, created by Alibaba Cloud. You are a helpful assistant."),
                tools = nativeTools, // Native LiteRT-LM tools with @Tool annotations
                samplerConfig =
                    SamplerConfig(
                        topK = 40,
                        topP = 0.85,
                        temperature = 0.2,
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
