package com.monday8am.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import com.monday8am.agent.core.logger
import com.monday8am.koogagent.data.LocationProvider
import kotlinx.serialization.builtins.serializer

class GetLocation(
    private val locationProvider: LocationProvider,
) : SimpleTool<Unit>(
    name = "GetLocation",
    description = "No arguments required. Get the user's current location in latitude and longitude format",
    argsSerializer = Unit.serializer(),
) {
    override suspend fun execute(args: Unit): String = try {
        val result = locationProvider.getLocation()
        "latitude ${result.latitude}, longitude: ${result.longitude}"
    } catch (e: Exception) {
        logger.e { "GetLocation: Error fetching location: ${e.message}" }
        "location: error - ${e.message}"
    }
}
