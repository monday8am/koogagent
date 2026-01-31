package com.monday8am.koogagent.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DataStoreModelDataSource(
    private val dataStore: DataStore<Preferences>
) : LocalModelDataSource {

    private val modelsKey = stringPreferencesKey("model_catalog")

    override suspend fun getModels(): List<ModelConfiguration>? {
        val jsonString = dataStore.data.map { preferences ->
            preferences[modelsKey]
        }.first()

        return if (jsonString != null) {
            try {
                Json.decodeFromString<List<ModelConfiguration>>(jsonString)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    override suspend fun saveModels(models: List<ModelConfiguration>) {
        val jsonString = Json.encodeToString(models)
        dataStore.edit { preferences ->
            preferences[modelsKey] = jsonString
        }
    }
}
