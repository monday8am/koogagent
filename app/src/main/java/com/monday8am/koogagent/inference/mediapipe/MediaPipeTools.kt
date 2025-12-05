package com.monday8am.koogagent.inference.mediapipe

import com.google.ai.edge.localagents.core.proto.FunctionDeclaration
import com.google.ai.edge.localagents.core.proto.Schema
import com.google.ai.edge.localagents.core.proto.Tool
import com.google.ai.edge.localagents.core.proto.Type

/**
 * MediaPipe tool declarations for location and weather functionality.
 * These match the tools defined in the Koog agent module but use MediaPipe's API format.
 */
object MediaPipeTools {
    /**
     * Creates a GetLocation tool declaration.
     * This tool returns the user's current location (latitude and longitude).
     */
    fun createGetLocationTool(): Tool {
        val getLocationFunction =
            FunctionDeclaration
                .newBuilder()
                .setName("GetLocationTool")
                .setDescription("Get the user's current location as latitude and longitude coordinates.")
                .setParameters(
                    Schema
                        .newBuilder()
                        .setType(Type.OBJECT)
                        .putProperties(
                            "dummy",
                            Schema
                                .newBuilder()
                                .setType(Type.STRING)
                                .setDescription("This tool takes no parameters")
                                .build(),
                        ).build(),
                ).build()

        return Tool
            .newBuilder()
            .addFunctionDeclarations(getLocationFunction)
            .build()
    }

    /**
     * Creates a GetWeatherFromLocation tool declaration.
     * This tool returns weather conditions for a given location.
     */
    fun createGetWeatherTool(): Tool {
        val getWeatherFunction =
            FunctionDeclaration
                .newBuilder()
                .setName("GetWeatherFromLocation")
                .setDescription(
                    "Get current weather conditions at a specific location. " +
                        "Returns the weather condition such as SUNNY, CLOUDY, RAINY, HOT, or COLD.",
                ).setParameters(
                    Schema
                        .newBuilder()
                        .setType(Type.OBJECT)
                        .putProperties(
                            "latitude",
                            Schema
                                .newBuilder()
                                .setType(Type.NUMBER)
                                .setDescription("The latitude coordinate of the location")
                                .build(),
                        ).putProperties(
                            "longitude",
                            Schema
                                .newBuilder()
                                .setType(Type.NUMBER)
                                .setDescription("The longitude coordinate of the location")
                                .build(),
                        ).addRequired("latitude")
                        .addRequired("longitude")
                        .build(),
                ).build()

        return Tool
            .newBuilder()
            .addFunctionDeclarations(getWeatherFunction)
            .build()
    }

    /**
     * Creates all available tools in a single Tool object.
     */
    fun createAllTools(): Tool =
        Tool
            .newBuilder()
            .addAllFunctionDeclarations(
                listOf(
                    createGetLocationTool().getFunctionDeclarations(0),
                    createGetWeatherTool().getFunctionDeclarations(0),
                ),
            ).build()
}
