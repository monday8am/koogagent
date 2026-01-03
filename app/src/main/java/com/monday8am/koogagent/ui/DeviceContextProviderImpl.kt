package com.monday8am.koogagent.ui

import android.content.Context
import com.monday8am.koogagent.data.DeviceContext
import com.monday8am.presentation.notifications.DeviceContextProvider
import java.util.Locale

class DeviceContextProviderImpl(private val context: Context) : DeviceContextProvider {
    override fun getDeviceContext(): DeviceContext {
        val locale: Locale = context.resources.configuration.locales[0]
        val country = locale.country.ifEmpty { Locale.getDefault().country }
        val language = locale.language.ifEmpty { Locale.getDefault().language }
        return DeviceContext(country, language)
    }
}
