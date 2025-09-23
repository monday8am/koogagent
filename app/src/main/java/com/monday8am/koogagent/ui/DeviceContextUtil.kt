package com.monday8am.koogagent.ui

import android.content.Context
import java.util.Locale

data class DeviceContext(
    val country: String,
    val language: String
)

object DeviceContextUtil {
    fun getDeviceContext(context: Context): DeviceContext {
        val locale: Locale = context.resources.configuration.locales[0]
        val country = locale.country.ifEmpty { Locale.getDefault().country }
        val language = locale.language.ifEmpty { Locale.getDefault().language }
        return DeviceContext(country, language)
    }
}
