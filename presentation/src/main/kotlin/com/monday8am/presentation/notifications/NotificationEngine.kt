package com.monday8am.presentation.notifications

import com.monday8am.koogagent.data.NotificationResult

interface NotificationEngine {
    fun showNotification(result: NotificationResult)
}
