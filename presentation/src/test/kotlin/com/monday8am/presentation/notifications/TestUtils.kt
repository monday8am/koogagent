package com.monday8am.presentation.notifications

import app.cash.turbine.ReceiveTurbine

suspend fun <T> ReceiveTurbine<T>.skipInitialState(): T = awaitItem()
