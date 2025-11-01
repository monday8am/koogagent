package com.monday8am.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import com.monday8am.agent.core.logger
import com.monday8am.koogagent.data.LocationProvider
import kotlinx.serialization.builtins.serializer

class GetLocation(
    private val locationProvider: LocationProvider,
) : SimpleTool<Unit>() {
    override val argsSerializer = Unit.serializer()

    override val description: String
        get() = "Call this function to obtain user's location in latitude and longitude format. Parameters not required!."

    override val descriptor =
        ToolDescriptor(
            name = "GetLocation",
            description = description,
            requiredParameters = listOf(),
        )

    override suspend fun doExecute(args: Unit): String =
        try {
            val result = locationProvider.getLocation()
            "latitude ${result.latitude}, longitude: ${result.longitude}"
        } catch (e: Exception) {
            logger.e { "GetLocation: Error fetching location: ${e.message}" }
            "location: error - ${e.message}"
        }
}
