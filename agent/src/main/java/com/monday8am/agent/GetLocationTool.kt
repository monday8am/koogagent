package com.monday8am.agent

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import com.monday8am.koogagent.data.LocationProvider
import kotlinx.serialization.builtins.serializer

class GetLocationTool(
    private val locationProvider: LocationProvider,
) : SimpleTool<Unit>() {
    override val argsSerializer = Unit.serializer()

    override val description: String
        get() = "Get the user current location"

    override val descriptor =
        ToolDescriptor(
            name = "GetLocationTool",
            description = "Get the current user location and returns latitude and longitude",
            requiredParameters = listOf(),
        )

    override suspend fun doExecute(args: Unit): String =
        try {
            val result = locationProvider.getLocation()
            "Location: latitude ${result.latitude}, longitude: ${result.longitude}"
        } catch (e: Exception) {
            logger.e { "GetLocationTool: Error fetching location: ${e.message}" }
            "Location: error - ${e.message}"
        }
}
