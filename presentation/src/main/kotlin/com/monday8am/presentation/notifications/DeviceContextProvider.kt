package com.monday8am.presentation.notifications

import com.monday8am.koogagent.data.DeviceContext

interface DeviceContextProvider {
    fun getDeviceContext(): DeviceContext
}
