package com.monday8am.koogagent.data

/** Interface that abstracts the local storage of model configurations. */
interface LocalModelDataSource {
    suspend fun getModels(): List<ModelConfiguration>?

    suspend fun saveModels(models: List<ModelConfiguration>)
}
